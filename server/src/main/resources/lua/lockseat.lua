-- 锁座（Redis 原子）：座位图缺失校验 → 一人一单 → 校验可售 → 置占用
-- 座位状态用 Hash：KEYS[1] = seats:session:{sid}
--   field = seatNo（"行-列"），value = "0"(可售) 或 锁座 userId(占用)
--
-- KEYS[1] = seats:session:{sessionId}   座位状态 Hash
-- KEYS[2] = userOrder:{sessionId}:{userId}   一人一单标记
-- ARGV[1..n] = seatNo
-- ARGV[n+1] = userId
-- ARGV[n+2] = 一人一单标记 TTL(秒)
-- return 0 成功 / 1 座位被占 / 3 该用户已有进行中的订单 / 4 座位图未初始化（需重建）

-- 0. 座位图整体缺失（未初始化 / TTL 过期 / Redis 丢失）→ 由 Java 重建后重试
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 4
end

-- 1. 一人一单：有进行中的订单则拒绝
if redis.call('EXISTS', KEYS[2]) == 1 then
    return 3
end

local n = #ARGV - 2
local userId = ARGV[n + 1]
local uoTtl = tonumber(ARGV[n + 2])

-- 2. 校验所有座位可售（value 为 "0" 或 nil 均可售；非 "0" 即被占）
for i = 1, n do
    local v = redis.call('HGET', KEYS[1], ARGV[i])
    if v ~= nil and v ~= '0' then
        return 1
    end
end

-- 3. 全部置占用（value = 锁座用户，供释放时归属校验）
for i = 1, n do
    redis.call('HSET', KEYS[1], ARGV[i], userId)
end

-- 4. 一人一单标记
redis.call('SET', KEYS[2], userId, 'EX', uoTtl)
return 0
