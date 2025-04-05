package com.dy.umb.aop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;

@Component
public class CacheDeleteHelper {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void doubleDelete(String key, long delayMillis) {
        redisTemplate.delete(key);
        executor.submit(() -> {
            try {
                Thread.sleep(delayMillis);
                redisTemplate.delete(key);
            } catch (InterruptedException ignored) {}
        });
    }

    public void doubleDelete(String key) {
        doubleDelete(key, 300L);
    }
}