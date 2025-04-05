package com.dy.umb.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;


@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheDoubleDelete {
    /**
     * 要删除的 Redis key 支持 SpEL 表达式，比如："team:info:" + #teamUpdateRequest.id
     */
    String[] keys();  // 支持传多个 Key

    /**
     * 延迟删除的时间，单位毫秒，默认300ms
     */
    long delayMillis() default 300L;
}

