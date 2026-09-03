-- 第二层限流：Redis 滑动窗口（按 userId）
-- KEYS[1] = rate:user:{userId}:{key}
-- ARGV[1] = now(毫秒)
-- ARGV[2] = 窗口长度(毫秒)
-- ARGV[3] = 窗口内允许次数
-- ARGV[4] = member 唯一串（now_随机，避免同刻覆盖）
-- return: 1 放行 / 0 拒绝

-- 1. 清掉窗口外的历史请求
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[1]) - tonumber(ARGV[2]))

-- 2. 窗口内次数已满则拒绝
if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
    return 0
end

-- 3. 记录本次请求 + 给 key 设过期(防堆积)
redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[4])
redis.call('EXPIRE', KEYS[1], math.floor(tonumber(ARGV[2]) / 1000) + 1)
return 1
