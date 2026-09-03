-- 锁座（Redis 原子）：校验可售 → 全部置位 → 写座位归属锁 → 一人一单标记
-- 座位状态用 Hash：KEYS[1] = seats:session:{sid}，field=seatNo，value 0可售/1占用
--
-- KEYS[1] = seats:session:{sessionId}   座位状态 Hash
-- KEYS[2] = userOrder:{sessionId}:{userId}   一人一单标记
-- KEYS[3..] = lock:seat:{sessionId}:{seatNo}   每座的归属锁（带 TTL 兜底幽灵锁）
-- ARGV[1..n] = seatNo（与 KEYS[3..] 一一对应）
-- ARGV[n+1] = userId
-- ARGV[n+2] = 座位锁 TTL(秒)
-- ARGV[n+3] = 一人一单标记 TTL(秒)
-- return 0 成功 / 1 座位被占 / 3 该用户已有进行中的订单

local n = #ARGV - 3
local userId = ARGV[n + 1]
local lockTtl = tonumber(ARGV[n + 2])
local uoTtl = tonumber(ARGV[n + 3])

-- 一人一单：有进行中的订单则拒绝
if redis.call('EXISTS', KEYS[2]) == 1 then
    return 3
end

-- 1. 校验所有座位可售（v 为 nil 视为从未标记 = 可售）
for i = 1, n do
    local v = redis.call('HGET', KEYS[1], ARGV[i])
    if v and v ~= '0' then
        return 1
    end
end

-- 2. 全部置位(占用) + 写归属锁(EX TTL，幽灵锁自动过期兜底)
for i = 1, n do
    redis.call('HSET', KEYS[1], ARGV[i], '1')
    redis.call('SET', KEYS[2 + i], userId, 'EX', lockTtl)
end

-- 3. 一人一单标记
redis.call('SET', KEYS[2], userId, 'EX', uoTtl)
return 0
