package com.cinema.utils;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 缓存客户端：统一详情查询（缓存穿透/击穿防护）
 *
 * 流程：布隆(拦不存在 id) → 读缓存(空值哨兵拦截 DB 无数据) → miss 后互斥锁重建
 * 约定：缓存值为空串 "" 表示"DB 无数据"空哨兵（短 TTL）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final BloomFilter bloomFilter;

    private static final String EMPTY = "";

    public void set(String key, Object value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl);
    }

    public void setEmpty(String key, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, EMPTY, ttl);
    }

    /**
     * 实体详情统一查询：
     * 1. 布隆过滤（一定不存在的 id 直接返回 null，不打缓存/DB）——防穿透第一层
     * 2. 读缓存：空哨兵 → null（DB 无）；对象 → 返回                    ——防穿透第二层
     * 3. 缓存 miss → SETNX 互斥锁重建（防击穿）
     */
    public <T> T queryDetail(String bloomEntity, Long id, String cacheKeyPrefix,
                             Supplier<T> dbQuery, Class<T> type) {
        if (!bloomFilter.mightContain(bloomEntity, String.valueOf(id))) {
            return null;   // 一定不存在
        }
        String key = cacheKeyPrefix + id;
        String json = readRaw(key);
        if (json != null) {
            // 缓存命中（可能是空哨兵）
            return json.isEmpty() ? null : JSONUtil.toBean(json, type);
        }
        // 缓存 miss → 互斥锁重建
        String lockKey = RedisConstants.LOCK_CACHE_KEY + bloomEntity + ":" + id;
        return queryWithMutex(key, lockKey, dbQuery, type);
    }

    private <T> T queryWithMutex(String cacheKey, String lockKey,
                                 Supplier<T> dbQuery, Class<T> type) {
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisConstants.LOCK_CACHE_TTL))) {
            try {
                // double check
                String json = readRaw(cacheKey);
                if (json != null) {
                    return json.isEmpty() ? null : JSONUtil.toBean(json, type);
                }
                T data = dbQuery.get();
                if (data == null) {
                    setEmpty(cacheKey, RedisConstants.EMPTY_TTL);
                    return null;
                }
                set(cacheKey, data, RedisConstants.CACHE_TTL);
                return data;
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        } else {
            // 拿锁失败：等待后重读（重建线程正在查 DB，稍等即可）
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String json = readRaw(cacheKey);
            if (json == null) {
                return null;
            }
            return json.isEmpty() ? null : JSONUtil.toBean(json, type);
        }
    }

    /** 读原始缓存 json；null=无缓存；""=空哨兵(DB 无) */
    public String readRaw(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }
}
