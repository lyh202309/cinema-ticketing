package com.cinema.utils;

import java.time.Duration;

/**
 * Redis Key 规范（对应 docs/接口文档.md 附录 A）
 */
public class RedisConstants {

    /** 登录验证码 login:code:{phone} */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /** 登录 token login:token:{token} */
    public static final String LOGIN_TOKEN_KEY = "login:token:";

    /** 实体详情缓存 cache:movie:{id} / cache:cinema:{id} / cache:session:{id} */
    public static final String CACHE_MOVIE_KEY = "cache:movie:";
    public static final String CACHE_CINEMA_KEY = "cache:cinema:";
    public static final String CACHE_SESSION_KEY = "cache:session:";

    /** 场次座位图 seats:session:{sessionId} */
    public static final String SEATS_KEY = "seats:session:";

    /** 资格池 ZSet qualify:pool:{sessionId} */
    public static final String QUALIFY_POOL_KEY = "qualify:pool:";

    /** 一人一单标记 userOrder:{sessionId}:{userId} */
    public static final String USER_ORDER_KEY = "userOrder:";

    /** AI 会话上下文缓存 chat:ctx:{conversationId} */
    public static final String CHAT_CTX_KEY = "chat:ctx:";

    /** 二级限流滑动窗口 rate:user:{userId}:qualify */
    public static final String RATE_QUALIFY_KEY = "rate:user:";

    /** 布隆过滤器位数组 bloom:{movie|cinema|session} */
    public static final String BLOOM_KEY = "bloom:";

    /** 缓存重建互斥锁 lock:cache:{entity}:{id} */
    public static final String LOCK_CACHE_KEY = "lock:cache:";

    // ===== TTL =====
    public static final Long LOGIN_CODE_TTL = 5L;       // 分钟
    public static final Long LOGIN_TOKEN_TTL = 30L;     // 分钟
    public static final Long QUALIFY_TTL_SECONDS = 180L;  // 资格 3 分钟(秒)
    public static final Long SEATS_TTL_SECONDS = 18000L;  // 座位图整体 5 小时重建一次(秒)
    public static final Duration CACHE_TTL = Duration.ofMinutes(30);   // 详情缓存
    public static final Duration EMPTY_TTL = Duration.ofMinutes(2);    // 空值哨兵(防穿透)短 TTL
    public static final Duration LOCK_CACHE_TTL = Duration.ofSeconds(10); // 重建互斥锁持有时间
}
