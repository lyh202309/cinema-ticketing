package com.cinema.controller;

import com.cinema.common.Result;
import com.cinema.dto.OrderDetailVO;
import com.cinema.dto.OrderListItemVO;
import com.cinema.dto.PageResult;
import com.cinema.service.IOrderService;
import com.cinema.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单模块（需登录，userId 从 ThreadLocal 取，防越权）
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @GetMapping("/my")
    public Result<PageResult<OrderListItemVO>> my(@RequestParam(value = "status", required = false) Integer status,
                                                  @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                  @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.ok(orderService.myOrders(UserHolder.getUserId(), status, page, size));
    }

    @GetMapping("/{orderId}")
    public Result<OrderDetailVO> detail(@PathVariable("orderId") Long orderId) {
        return Result.ok(orderService.getDetail(orderId, UserHolder.getUserId()));
    }

    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(@PathVariable("orderId") Long orderId) {
        orderService.pay(orderId, UserHolder.getUserId());
        return Result.ok();
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable("orderId") Long orderId) {
        orderService.cancel(orderId, UserHolder.getUserId());
        return Result.ok();
    }

    @PostMapping("/{orderId}/refund")
    public Result<Void> refund(@PathVariable("orderId") Long orderId) {
        orderService.refund(orderId, UserHolder.getUserId());
        return Result.ok();
    }
}
