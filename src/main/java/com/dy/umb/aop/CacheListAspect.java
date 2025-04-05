package com.dy.umb.aop;

import com.dy.umb.common.BaseResponse;
import com.dy.umb.common.ResultUtils;
import com.dy.umb.model.domain.User;
import com.dy.umb.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Random;

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

    @Around("@annotation(cacheGetList)")
    public Object around(ProceedingJoinPoint joinPoint, CacheGetList cacheGetList) throws Throwable {
        // 1. 获取用户ID
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();

        // 2. 构造缓存Key
        String key = cacheGetList.keyPrefix() + userId;

        // 3. 查缓存
        Object cacheValue = redisTemplate.opsForValue().get(key);
        if (cacheValue != null) {
            log.info("缓存命中: {}", key);
            return ResultUtils.success(cacheValue);
        }

        // 4. 执行原方法（从数据库查询）
        Object result = joinPoint.proceed();

        // 5. 写入缓存（设置过期时间 + 随机扩展，防雪崩）
        long timeout = cacheGetList.timeout();
        redisTemplate.opsForValue().set(
                key,
                ((BaseResponse<?>) result).getData(), // 只缓存 data 部分
                Duration.ofMinutes(timeout + new Random().nextInt(5))
        );

        return result;
    }
}

