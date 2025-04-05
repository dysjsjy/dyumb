package com.dy.umb.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheGetList {
    String keyPrefix(); // 缓存key前缀，如 "team:list:my:create:"
    long timeout() default 10; // 默认缓存10分钟
}
