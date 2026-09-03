-- 抢资格 Lua（Redis 单线程原子执行）
-- KEYS[1] = qualify:pool:{sessionId}
-- ARGV[1] = 当前时间(秒)     用于清理过期成员
-- ARGV[2] = userId
-- ARGV[3] = 容量 = 座位数 × 3
-- ARGV[4] = 资格过期时间戳 = now + 180(秒)
-- return: 0 抢到 / 1 名额满 / 2 已抢过

-- 1. 清理过期成员 → 名额自然回流（无需主动触发）
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[1]))

-- 2. 一人一资格
local sc = redis.call('ZSCORE', KEYS[1], ARGV[2])
if sc ~= false then
    return 2
end

-- 3. 有效人数是否已满（ZCARD = 当前有效资格数）
if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
    return 1
end

-- 4. 记录资格（score = 过期时间戳，懒删除由下次抢/校验触发）
redis.call('ZADD', KEYS[1], tonumber(ARGV[4]), ARGV[2])
return 0
