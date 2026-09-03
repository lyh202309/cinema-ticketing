-- 释放座位（取消订单 / 退票 / 落库失败补偿）
-- 仅释放"归属锁 value == userId"的座位，防止误释放他人刚锁的座位
--
-- KEYS[1] = seats:session:{sessionId}   座位状态 Hash
-- KEYS[2..] = 每座的归属锁 lock:seat:{sessionId}:{seatNo}
-- ARGV[1] = userId
-- ARGV[2..] = seatNo（与 KEYS[2..] 一一对应）
-- return 0

local uid = ARGV[1]

for i = 1, #ARGV - 1 do
    local seatNo = ARGV[1 + i]
    local lockKey = KEYS[1 + i]
    -- 仅当锁归属本用户才释放；锁已过期(nil)则跳过，由"座位图重建"以 DB 为准收敛
    if redis.call('GET', lockKey) == uid then
        redis.call('HSET', KEYS[1], seatNo, '0')
        redis.call('DEL', lockKey)
    end
end
return 0
