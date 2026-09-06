-- 释放座位（取消订单 / 退票 / 落库失败补偿）
-- 仅释放"value == userId"的座位（归属校验），防止误释放他人刚锁的座位
--
-- KEYS[1] = seats:session:{sessionId}   座位状态 Hash
-- ARGV[1] = userId
-- ARGV[2..] = seatNo
-- return 0

local uid = ARGV[1]

for i = 1, #ARGV - 1 do
    local seatNo = ARGV[1 + i]
    -- 仅当该座位仍归属本用户（value == uid）才释放回可售（"0"）
    if redis.call('HGET', KEYS[1], seatNo) == uid then
        redis.call('HSET', KEYS[1], seatNo, '0')
    end
end
return 0
