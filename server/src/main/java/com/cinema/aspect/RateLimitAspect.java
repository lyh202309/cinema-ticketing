package com.cinema.aspect;

import com.cinema.annotation.RateLimit;
import com.cinema.common.BusinessException;
import com.cinema.utils.RedisConstants;
import com.cinema.utils.UserHolder;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 二级限流切面（只拦截标注 @RateLimit 的方法）
 * 第一层：Guava 令牌桶 tryAcquire() —— 限接口总 QPS，本地非阻塞
 * 第二层：Redis 滑动窗口 —— 按 userId 限频
 * 两层都是快速拒绝（抛繁忙），不在入口线程阻塞等待
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    /** 快速拒绝文案：不让用户感知到"资格/限流"机制 */
    private static final String BUSY_MSG = "当前人数过多，服务器繁忙，请稍后重试";

    private final StringRedisTemplate stringRedisTemplate;

    /** per 方法（类.方法）的令牌桶 */
    private final Map<String, RateLimiter> limiterMap = new ConcurrentHashMap<>();

    private static final DefaultRedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>();

    static {
        WINDOW_SCRIPT.setLocation(new ClassPathResource("lua/slidingwindow.lua"));
        WINDOW_SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        // ===== 第一层：Guava 令牌桶（接口总 QPS）=====
        String methodKey = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
        RateLimiter limiter = limiterMap.computeIfAbsent(methodKey, k -> RateLimiter.create(rateLimit.qps()));
        if (!limiter.tryAcquire()) {
            throw new BusinessException(BUSY_MSG);
        }

        // ===== 第二层：Redis 滑动窗口（按 userId）=====
        Long userId = UserHolder.getUserId();
        if (userId != null) {
            String windowKey = RedisConstants.RATE_QUALIFY_KEY + userId + ":" + rateLimit.key();
            long now = System.currentTimeMillis();
            String member = now + "_" + ThreadLocalRandom.current().nextInt(1_000_000);
            List<String> keys = Collections.singletonList(windowKey);
            Long result = stringRedisTemplate.execute(WINDOW_SCRIPT, keys,
                    String.valueOf(now),
                    String.valueOf(rateLimit.windowMs()),
                    String.valueOf(rateLimit.limit()),
                    member);
            if (!Long.valueOf(1L).equals(result)) {
                throw new BusinessException(BUSY_MSG);
            }
        }
        return pjp.proceed();
    }
}
