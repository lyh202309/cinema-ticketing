package com.cinema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 二级限流注解
 * 第一层：Guava 令牌桶（接口总 QPS，qps）
 * 第二层：Redis 滑动窗口（按 userId 限频）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 第二层滑动窗口 key 后缀，如 qualify */
    String key() default "";

    /** 第一层：接口总 QPS（令牌桶速率） */
    int qps() default 1000;

    /** 第二层：单个 userId 在窗口内允许的次数 */
    int limit() default 3;

    /** 第二层：滑动窗口长度（毫秒） */
    int windowMs() default 5000;
}
