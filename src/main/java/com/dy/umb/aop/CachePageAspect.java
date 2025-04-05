package com.dy.umb.aop;

import com.dy.umb.common.BaseResponse;
import com.dy.umb.common.ResultUtils;
import com.dy.umb.model.dto.TeamQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class CachePageAspect {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedissonClient redissonClient; // 注入 RedissonClient

    @Resource(name = "teamBloomFilter") // 注入已初始化的布隆过滤器
    private RBloomFilter<String> bloomFilter;

    @Around("@annotation(cachePage)")
    public Object around(ProceedingJoinPoint joinPoint, CachePage cachePage) throws Throwable {
        // 获取方法参数（假设只有一个 teamQuery）
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof TeamQuery)) {
            return joinPoint.proceed(); // 不满足条件，不缓存
        }

        TeamQuery query = (TeamQuery) args[0];

        // 构造Key：前缀 + 页码 + 页大小 + 参数hash
        String rawKey = cachePage.keyPrefix()
                + ":" + query.getPageNum()
                + ":" + query.getPageSize()
                + ":" + DigestUtils.md5DigestAsHex(new ObjectMapper().writeValueAsBytes(query));
        log.info("分页缓存Key: {}", rawKey);

        // 1. 检查布隆过滤器，判断键是否可能存在
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("teamBloomFilter"); // Bloom Filter 名称
        if (!bloomFilter.contains(rawKey)) {
            log.warn("布隆过滤器判断键不存在: {}", rawKey);
            return ResultUtils.success(null); // 直接返回空结果，避免穿透
        }

        // 2. 尝试从缓存获取数据
        Object cached = redisTemplate.opsForValue().get(rawKey);
        if (cached != null) {
            log.info("分页命中缓存: {}", rawKey);
            return ResultUtils.success(cached);
        }

        // 3. 缓存未命中，使用分布式锁
        String lockKey = "lock:" + rawKey; // 锁的键名基于缓存键
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，设置等待时间和锁的过期时间
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (isLocked) {
                try {
                    // 双重检查，防止其他线程已经重建了缓存
                    cached = redisTemplate.opsForValue().get(rawKey);
                    if (cached != null) {
                        log.info("双重检查命中缓存: {}", rawKey);
                        return ResultUtils.success(cached);
                    }

                    // 执行原方法
                    Object result = joinPoint.proceed();

                    // 写入缓存（仅缓存 Page<Team>，不包含 BaseResponse 外壳）
                    if (result instanceof BaseResponse<?>) {
                        Object data = ((BaseResponse<?>) result).getData();
                        redisTemplate.opsForValue().set(
                                rawKey,
                                data,
                                Duration.ofMinutes(cachePage.timeout() + new Random().nextInt(3))
                        );
                    }

                    return result;
                } finally {
                    //  释放锁
                    lock.unlock();
                }
            } else {
                // 如果获取锁失败，稍等后重试
                log.warn("未能获取锁，稍后重试: {}", lockKey);
                Thread.sleep(100); // 简单等待
                return around(joinPoint, cachePage); // 递归重试
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to acquire lock", e);
        }
    }
}