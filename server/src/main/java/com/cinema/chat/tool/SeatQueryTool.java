package com.cinema.chat.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinema.entity.Hall;
import com.cinema.entity.Order;
import com.cinema.entity.OrderSeat;
import com.cinema.entity.Session;
import com.cinema.mapper.HallMapper;
import com.cinema.mapper.OrderMapper;
import com.cinema.mapper.OrderSeatMapper;
import com.cinema.mapper.SessionMapper;
import com.cinema.utils.RedisConstants;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 工具：查某场次座位占用（只读）
 * 注意：这是给 AI 咨询用的，不套抢票资格校验（用户只是想问"还有哪些座"）
 */
@Component
@RequiredArgsConstructor
public class SeatQueryTool {

    private final SessionMapper sessionMapper;
    private final HallMapper hallMapper;
    private final OrderMapper orderMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Tool("查询某场次的座位占用/可售情况。参数 sessionId 是场次的数字编号：用户说'场次X'/'第X场'/'X号厅那场'时 sessionId 就是那个数字X，例如'场次1'对应 sessionId=1，直接查询即可，不要向用户追问电影名或日期。返回影厅尺寸(行x列)与已占用座位(行-列)列表，据此告知剩余可售座位数。")
    public String querySeatMap(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return "场次不存在。";
        }
        Hall hall = hallMapper.selectById(session.getHallId());
        if (hall == null) {
            return "场次影厅信息缺失。";
        }

        // 占用座位集合：优先 Redis，缺失则从 DB 有效订单推导
        Set<String> occupied = new HashSet<>();
        String seatsKey = RedisConstants.SEATS_KEY + sessionId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(seatsKey))) {
            Map<Object, Object> state = stringRedisTemplate.opsForHash().entries(seatsKey);
            state.forEach((k, v) -> {
                if ("1".equals(v)) {
                    occupied.add(String.valueOf(k));
                }
            });
        } else {
            List<Long> validOrderIds = orderMapper.selectList(
                            new LambdaQueryWrapper<Order>()
                                    .select(Order::getId)
                                    .eq(Order::getSessionId, sessionId)
                                    .in(Order::getStatus, List.of(0, 1)))
                    .stream().map(Order::getId).toList();
            if (!validOrderIds.isEmpty()) {
                orderSeatMapper.selectList(new LambdaQueryWrapper<OrderSeat>()
                                .in(OrderSeat::getOrderId, validOrderIds))
                        .forEach(os -> occupied.add(os.getSeatRow() + "-" + os.getSeatCol()));
            }
        }

        int rows = hall.getRowCount();
        int cols = hall.getColCount();
        List<String> occupiedList = new ArrayList<>(occupied);
        int total = rows * cols;
        return "影厅尺寸 " + rows + "行x" + cols + "列，共" + total + "座。"
                + "已占用座位：" + (occupiedList.isEmpty() ? "无，全场可售" : String.join(", ", occupiedList));
    }
}
