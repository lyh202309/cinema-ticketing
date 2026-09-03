package com.cinema.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 自实现布隆过滤器（基于 Redis 位数组 SETBIT/GETBIT）
 *
 * 防缓存穿透第一层：拦截"一定不存在"的 id 查询，避免打到 DB。
 * - 数据写入 Redis String 位数组，每个 value 映射到 k 个 bit（k = seeds.length）
 * - 查询时 k 个 bit 全为 1 → 可能存在（有误判）；任一为 0 → 一定不存在
 * - 位数组大小固定（bitSize），本场景合法 id 极少（种子数据），误判率趋近 0
 */
@Component
@RequiredArgsConstructor
public class BloomFilter {

    private final StringRedisTemplate stringRedisTemplate;

    /** 位数组大小（bit 数）。1 << 20 = 128KB/key，本场景绰绰有余 */
    private static final long BIT_SIZE = 1L << 20;

    /** k 个 hash 的种子（质数，彼此互异保证不同 hash） */
    private static final int[] SEEDS = {5, 7, 11, 13, 31, 37, 61};

    /** 加入集合 */
    public void add(String key, String value) {
        String redisKey = RedisConstants.BLOOM_KEY + key;
        for (int seed : SEEDS) {
            long offset = hash(value, seed);
            stringRedisTemplate.opsForValue().setBit(redisKey, offset, true);
        }
    }

    /**
     * 是否"可能存在"
     *
     * @return true 可能存在 / false 一定不存在
     */
    public boolean mightContain(String key, String value) {
        String redisKey = RedisConstants.BLOOM_KEY + key;
        for (int seed : SEEDS) {
            long offset = hash(value, seed);
            Boolean bit = stringRedisTemplate.opsForValue().getBit(redisKey, offset);
            if (!Boolean.TRUE.equals(bit)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把 value 的每个字符按 seed 累乘散列，映射到 [0, BIT_SIZE)
     * 用 & 0x7FFFFFFF 处理符号/溢出
     */
    private long hash(String value, int seed) {
        long h = 0;
        for (int i = 0; i < value.length(); i++) {
            h = (h * seed + value.charAt(i)) & 0x7FFFFFFFL;
        }
        return h % BIT_SIZE;
    }
}
