package com.dy.umb.aop;

import com.dy.umb.common.BaseResponse;
import com.dy.umb.common.ResultUtils;
import com.dy.umb.model.dto.TeamQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Random;

@Slf4j
@Aspect
@Component
public class CachePageAspect {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

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

        Object cached = redisTemplate.opsForValue().get(rawKey);
        if (cached != null) {
            log.info("分页命中缓存: {}", rawKey);
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
    }
}

