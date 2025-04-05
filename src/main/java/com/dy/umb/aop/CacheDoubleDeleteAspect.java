package com.dy.umb.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;


@Slf4j
@Aspect
@Component
public class CacheDoubleDeleteAspect {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(cacheDoubleDelete)")
    public Object around(ProceedingJoinPoint joinPoint, CacheDoubleDelete cacheDoubleDelete) throws Throwable {
        // 执行目标方法前，先缓存删除
        Object result = joinPoint.proceed();

        long deleteDelay = cacheDoubleDelete.delayMillis();

        // 执行缓存删除操作
        for (String key : cacheDoubleDelete.keys()) {
            String cacheKey = buildCacheKey(key, joinPoint.getArgs());  // 根据传入的参数动态构建Key
            log.info("执行删除缓存：{}", cacheKey);

            // 延迟删除：先删除一次缓存
            redisTemplate.delete(cacheKey);

            // 使用延迟时间后再删除一次
            new Thread(() -> {
                try {
                    Thread.sleep(deleteDelay);  // 延迟一段时间再删除
                    log.info("延迟删除缓存：{}", cacheKey);
                    redisTemplate.delete(cacheKey);
                } catch (InterruptedException e) {
                    log.error("延迟删除缓存出错", e);
                }
            }).start();
        }

        return result;
    }

    /**
     * 构建缓存Key，这里可以根据方法参数自定义构建逻辑
     */
    private String buildCacheKey(String keyPattern, Object[] args) {
        StringBuilder keyBuilder = new StringBuilder(keyPattern);
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                keyBuilder.append(":").append(arg.hashCode());
            }
        }
        return keyBuilder.toString();
    }
}


