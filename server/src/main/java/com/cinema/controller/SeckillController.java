package com.cinema.controller;

import com.cinema.annotation.RateLimit;
import com.cinema.common.Result;
import com.cinema.dto.LockRequest;
import com.cinema.dto.LockResultVO;
import com.cinema.dto.SeatMapVO;
import com.cinema.event.SeatEventHub;
import com.cinema.service.ISeckillService;
import com.cinema.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 秒杀购票链路（需登录）
 * 抢资格 / 座位图 / 锁座下单
 */
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillService seckillService;
    private final SeatEventHub seatEventHub;

    /**
     * 抢资格（热门场次），二级限流：Guava 1000QPS + 每用户 5s/3 次
     */
    @PostMapping("/qualify/{sessionId}")
    @RateLimit(key = "qualify", qps = 1000, limit = 3, windowMs = 5000)
    public Result<Integer> qualify(@PathVariable Long sessionId) {
        int result = seckillService.qualify(sessionId);
        if (result == 1) {
            return Result.fail("当前人数过多，服务器繁忙，请稍后重试");
        }
        // if (result == 2) {
        //     return Result.fail("请勿重复操作");
        // }
        return Result.ok(0);   // 抢到 → 前端跳转座位图
    }

    /**
     * 座位图（热门场次校验资格；越权 → 403 非法操作）
     */
    @GetMapping("/seatmap/{sessionId}")
    public Result<SeatMapVO> seatMap(@PathVariable Long sessionId) {
        return Result.ok(seckillService.seatMap(sessionId));
    }

    /**
     * 选座实时订阅（SSE 长连接）：同场次锁座/释放座位事件广播
     * 热门场次校验资格（过期/未抢 → 403），与座位图同权
     */
    @GetMapping("/seatmap/{sessionId}/subscribe")
    public SseEmitter subscribe(@PathVariable Long sessionId) {
        seckillService.ensureViewable(sessionId);
        return seatEventHub.subscribe(sessionId, UserHolder.getUserId());
    }

    /**
     * 锁座 + 下单（一次完成）
     */
    @PostMapping("/lock")
    public Result<LockResultVO> lock(@RequestBody LockRequest request) {
        return Result.ok(seckillService.lock(request));
    }
}
