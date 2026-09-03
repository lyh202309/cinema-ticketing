package com.cinema.service;

import com.cinema.dto.LockRequest;
import com.cinema.dto.LockResultVO;
import com.cinema.dto.SeatMapVO;

/**
 * 秒杀购票链路服务（抢资格 / 座位图 / 锁座）
 */
public interface ISeckillService {

    /**
     * 抢资格（热门场次）
     *
     * @return 0 抢到 / 1 名额满 / 2 已抢过
     */
    int qualify(Long sessionId);

    /**
     * 座位图：热门场次校验资格(无 → Forbidden)；Redis 缺失从 DB 重建
     */
    SeatMapVO seatMap(Long sessionId);

    /**
     * 锁座 + 下单（一次完成）：停售→资格→一人一单→Lua 锁座→同步落库→发延迟消息
     *
     * @return 订单信息（含 orderId / 支付剩余秒数）
     */
    LockResultVO lock(LockRequest request);

    /**
     * 校验用户能否查看某场次座位图 / 订阅实时事件（热门场次需持有效资格，否则 Forbidden）
     */
    void ensureViewable(Long sessionId);
}
