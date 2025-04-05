package com.dy.umb.aop;

import com.dy.umb.common.BaseResponse;
import com.dy.umb.common.ResultUtils;
import com.dy.umb.model.domain.User;
import com.dy.umb.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class CacheListAspect {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private HttpServletRequest request;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient; // 注入 RedissonClient

    @Resource(name = "teamBloomFilter") // 注入已初始化的布隆过滤器
    private RBloomFilter<String> bloomFilter;

    @Around("@annotation(cacheGetList)")
    public Object around(ProceedingJoinPoint joinPoint, CacheGetList cacheGetList) throws Throwable {
        // 获取用户ID
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();

        // 构造缓存Key
        String key = cacheGetList.keyPrefix() + userId;

        // 1. 检查布隆过滤器，判断键是否可能存在
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("listBloomFilter");
        if (!bloomFilter.contains(key)) {
            log.warn("布隆过滤器判断键不存在: {}", key);
            return ResultUtils.success(null); // 直接返回空结果
        }

        // 2. 查缓存
        Object cacheValue = redisTemplate.opsForValue().get(key);
        if (cacheValue != null) {
            log.info("缓存命中: {}", key);
            return ResultUtils.success(cacheValue);
        }

        // 3. 缓存未命中，使用分布式锁
        String lockKey = "lock:" + key; // 锁的键名基于缓存键
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，设置等待时间和锁的过期时间
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (isLocked) {
                try {
                    // 双重检查，防止其他线程已经重建了缓存
                    cacheValue = redisTemplate.opsForValue().get(key);
                    if (cacheValue != null) {
                        log.info("双重检查命中缓存: {}", key);
                        return ResultUtils.success(cacheValue);
                    }

                    // 执行原方法（从数据库查询）
                    Object result = joinPoint.proceed();

                    // 写入缓存（设置过期时间 + 随机扩展，防雪崩）
                    long timeout = cacheGetList.timeout();
                    redisTemplate.opsForValue().set(
                            key,
                            ((BaseResponse<?>) result).getData(), // 只缓存 data 部分
                            Duration.ofMinutes(timeout + new Random().nextInt(5))
                    );

                    return result;
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                // 如果获取锁失败，稍等后重试
                log.warn("未能获取锁，稍后重试: {}", lockKey);
                Thread.sleep(100); // 简单等待
                return around(joinPoint, cacheGetList); // 递归重试
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to acquire lock", e);
        }
    }
}