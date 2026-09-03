package com.cinema.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinema.common.BusinessException;
import com.cinema.common.ForbiddenException;
import com.cinema.dto.LockRequest;
import com.cinema.dto.LockResultVO;
import com.cinema.dto.SeatDTO;
import com.cinema.dto.SeatMapVO;
import com.cinema.entity.Hall;
import com.cinema.event.SeatEventHub;
import com.cinema.entity.Order;
import com.cinema.entity.OrderSeat;
import com.cinema.entity.Session;
import com.cinema.mapper.HallMapper;
import com.cinema.mapper.OrderMapper;
import com.cinema.mapper.OrderSeatMapper;
import com.cinema.mapper.SessionMapper;
import com.cinema.mq.MqDelaySender;
import com.cinema.service.ISeckillService;
import com.cinema.utils.RedisConstants;
import com.cinema.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秒杀购票链路实现
 * - 抢资格：ZSet 资格池 + Lua 原子
 * - 座位图：Redis Hash 座位状态（两态 0可售/1占用），Redis 缺失从 DB 重建
 * - 锁座：Lua 原子（一人一单并入）→ 同步落库 → 补偿释放 → 发延迟消息
 * 座位 = 影厅 (row, col)，seatNo 形如 "3-5"；Redis Hash field = seatNo
 */
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements ISeckillService {

    private final SessionMapper sessionMapper;
    private final HallMapper hallMapper;
    private final OrderMapper orderMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MqDelaySender mqDelaySender;
    private final SeatEventHub seatEventHub;

    private static final int MAX_SEATS = 5;
    /** 座位归属锁 TTL（秒）：幽灵锁自动过期兜底 */
    private static final long LOCK_TTL_SECONDS = 300;
    /** 一人一单标记 TTL（秒）：覆盖订单全生命周期后自动释放 */
    private static final long USER_ORDER_TTL_SECONDS = 600;

    private static final DefaultRedisScript<Long> QUALIFY_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> LOCK_SEAT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        QUALIFY_SCRIPT.setLocation(new ClassPathResource("lua/qualify.lua"));
        QUALIFY_SCRIPT.setResultType(Long.class);
        LOCK_SEAT_SCRIPT.setLocation(new ClassPathResource("lua/lockseat.lua"));
        LOCK_SEAT_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setLocation(new ClassPathResource("lua/releaseSeats.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    // ================= 座位图/实时事件 查看权限 =================

    @Override
    public void ensureViewable(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("场次不存在");
        }
        // 热门场次必须持有效资格（越权 → 403 非法操作）
        if (Boolean.TRUE.equals(session.getIsHot())) {
            Long userId = UserHolder.getUserId();
            if (userId == null || !hasQualify(sessionId, userId)) {
                throw new ForbiddenException("非法操作");
            }
        }
    }

    // ================= 抢资格 =================

    @Override
    public int qualify(Long sessionId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("场次不存在");
        }
        if (!Boolean.TRUE.equals(session.getIsHot())) {
            throw new BusinessException("该场次无需抢票，可直接购票");
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getSaleStartTime() != null && now.isBefore(session.getSaleStartTime())) {
            throw new BusinessException("未开售");
        }
        if (session.getSaleEndTime() != null && !now.isBefore(session.getSaleEndTime())) {
            throw new BusinessException("已截止售票");
        }
        Hall hall = hallMapper.selectById(session.getHallId());
        if (hall == null) {
            throw new BusinessException("场次影厅异常");
        }
        long capacity = (long) hall.getRowCount() * hall.getColCount() * 3;

        String key = RedisConstants.QUALIFY_POOL_KEY + sessionId;
        long nowSec = System.currentTimeMillis() / 1000;
        Long result = stringRedisTemplate.execute(QUALIFY_SCRIPT, List.of(key),
                String.valueOf(nowSec), String.valueOf(userId),
                String.valueOf(capacity),
                String.valueOf(nowSec + RedisConstants.QUALIFY_TTL_SECONDS));
        return result == null ? 1 : result.intValue();
    }

    // ================= 座位图 =================

    @Override
    public SeatMapVO seatMap(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("场次不存在");
        }
        Hall hall = hallMapper.selectById(session.getHallId());
        if (hall == null) {
            throw new BusinessException("场次影厅异常");
        }
        // 热门场次：必须持有资格（越权访问 → 403 非法操作）
        Long userId = UserHolder.getUserId();
        if (Boolean.TRUE.equals(session.getIsHot())
                && (userId == null || !hasQualify(sessionId, userId))) {
            throw new ForbiddenException("非法操作");
        }

        // Redis 座位状态；未初始化 → 从 DB 重建
        String seatsKey = RedisConstants.SEATS_KEY + sessionId;
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(seatsKey))) {
            rebuildSeatState(session, hall);
        }
        Map<Object, Object> state = stringRedisTemplate.opsForHash().entries(seatsKey);

        int rows = hall.getRowCount();
        int cols = hall.getColCount();
        int[][] seats = new int[rows][cols];
        state.forEach((field, value) -> {
            if ("1".equals(value)) {
                String[] rc = String.valueOf(field).split("-");
                int r = Integer.parseInt(rc[0]) - 1;
                int c = Integer.parseInt(rc[1]) - 1;
                if (r >= 0 && r < rows && c >= 0 && c < cols) {
                    seats[r][c] = 1;
                }
            }
        });

        SeatMapVO vo = new SeatMapVO();
        vo.setRows(rows);
        vo.setCols(cols);
        vo.setSeats(seats);
        return vo;
    }

    // ================= 锁座 + 下单 =================

    @Override
    @Transactional
    public LockResultVO lock(LockRequest request) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        List<SeatDTO> seats = request.getSeats();
        if (seats == null || seats.isEmpty()) {
            throw new BusinessException("请选择座位");
        }
        if (seats.size() > MAX_SEATS) {
            throw new BusinessException("单笔最多购买 " + MAX_SEATS + " 张");
        }

        Session session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException("场次不存在");
        }
        if (!Integer.valueOf(1).equals(session.getStatus())) {
            throw new BusinessException("该场次当前不可购买");
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getSaleEndTime() != null && !now.isBefore(session.getSaleEndTime())) {
            throw new BusinessException("已截止售票");
        }
        // 热门场次校验资格
        if (Boolean.TRUE.equals(session.getIsHot())
                && !hasQualify(request.getSessionId(), userId)) {
            throw new ForbiddenException("非法操作");
        }
        Hall hall = hallMapper.selectById(session.getHallId());
        if (hall == null) {
            throw new BusinessException("场次影厅异常");
        }

        // 座位合法性 + 去重 → seatNo 列表
        List<String> seatNos = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SeatDTO s : seats) {
            if (s.getRow() == null || s.getCol() == null
                    || s.getRow() < 1 || s.getRow() > hall.getRowCount()
                    || s.getCol() < 1 || s.getCol() > hall.getColCount()) {
                throw new BusinessException("座位超出影厅范围");
            }
            String seatNo = s.getRow() + "-" + s.getCol();
            if (!seen.add(seatNo)) {
                throw new BusinessException("不能重复选择座位");
            }
            seatNos.add(seatNo);
        }

        // ===== Lua 锁座（校验可售 + 置位 + 归属锁 + 一人一单，原子）=====
        List<String> keys = new ArrayList<>();
        keys.add(RedisConstants.SEATS_KEY + request.getSessionId());                      // KEYS[1]
        keys.add(RedisConstants.USER_ORDER_KEY + request.getSessionId() + ":" + userId);   // KEYS[2]
        for (String sn : seatNos) {
            keys.add(RedisConstants.LOCK_SEAT_KEY + request.getSessionId() + ":" + sn);    // KEYS[3..]
        }
        List<String> args = new ArrayList<>(seatNos);
        args.add(String.valueOf(userId));
        args.add(String.valueOf(LOCK_TTL_SECONDS));
        args.add(String.valueOf(USER_ORDER_TTL_SECONDS));

        Long lockResult = stringRedisTemplate.execute(LOCK_SEAT_SCRIPT, keys, args.toArray());
        int r = lockResult == null ? 1 : lockResult.intValue();
        if (r == 1) {
            throw new BusinessException("部分座位已被选择，请重新选座");
        }
        if (r == 3) {
            throw new BusinessException("您已有一笔进行中的订单，请先完成或取消");
        }

        // ===== 同步落库（DB 真源）=====
        Order order = new Order();
        order.setOrderNo(String.valueOf(IdUtil.getSnowflakeNextId()));
        order.setUserId(userId);
        order.setSessionId(request.getSessionId());
        order.setTotalPrice(session.getPrice().multiply(BigDecimal.valueOf(seatNos.size())));
        order.setStatus(0);
        try {
            orderMapper.insert(order);
            for (String sn : seatNos) {
                String[] rc = sn.split("-");
                OrderSeat os = new OrderSeat();
                os.setOrderId(order.getId());
                os.setSessionId(request.getSessionId());
                os.setSeatRow(Integer.parseInt(rc[0]));
                os.setSeatCol(Integer.parseInt(rc[1]));
                orderSeatMapper.insert(os);
            }
        } catch (RuntimeException e) {
            // 落库失败 → 补偿释放 Redis 座位（DB 事务随后回滚）
            releaseSeats(request.getSessionId(), seatNos, userId);
            throw e;
        }

        // 发延迟消息：订单 5min 超时释放
        mqDelaySender.sendOrderTimeout(order.getId());

        // 实时广播：本场锁座成功 → 其它订阅者即时看到这些座位变灰（操作者自己跳过）
        seatEventHub.publish(request.getSessionId(), userId,
                "{\"action\":\"lock\",\"seats\":" + JSONUtil.toJsonStr(seatNos) + "}");

        return new LockResultVO(order.getId(), 300);
    }

    // ================= 私有工具 =================

    /** 用户对该场次是否持有效资格（ZSet score = 过期时间戳，> now 有效） */
    private boolean hasQualify(Long sessionId, Long userId) {
        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisConstants.QUALIFY_POOL_KEY + sessionId, String.valueOf(userId));
        return score != null && score > System.currentTimeMillis() / 1000.0;
    }

    /** 从 DB 重建场次座位状态：有效订单(待支付/已支付)占用的座位标 1，写回 Redis */
    private void rebuildSeatState(Session session, Hall hall) {
        List<Long> validOrderIds = orderMapper.selectList(
                        new LambdaQueryWrapper<Order>()
                                .select(Order::getId)
                                .eq(Order::getSessionId, session.getId())
                                .in(Order::getStatus, List.of(0, 1)))
                .stream().map(Order::getId).toList();

        Set<String> occupied = new HashSet<>();
        if (!validOrderIds.isEmpty()) {
            orderSeatMapper.selectList(new LambdaQueryWrapper<OrderSeat>()
                            .in(OrderSeat::getOrderId, validOrderIds))
                    .forEach(os -> occupied.add(os.getSeatRow() + "-" + os.getSeatCol()));
        }

        String seatsKey = RedisConstants.SEATS_KEY + session.getId();
        stringRedisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) {
                byte[] hKey = seatsKey.getBytes(StandardCharsets.UTF_8);
                for (int r = 1; r <= hall.getRowCount(); r++) {
                    for (int c = 1; c <= hall.getColCount(); c++) {
                        String seatNo = r + "-" + c;
                        byte[] field = seatNo.getBytes(StandardCharsets.UTF_8);
                        byte[] value = (occupied.contains(seatNo) ? "1" : "0").getBytes(StandardCharsets.UTF_8);
                        connection.hashCommands().hSet(hKey, field, value);
                    }
                }
                return null;
            }
        });
    }

    /** 释放座位（归属校验在 Lua 内）：取消/退票/落库失败补偿共用 */
    private void releaseSeats(Long sessionId, List<String> seatNos, Long userId) {
        List<String> keys = new ArrayList<>();
        keys.add(RedisConstants.SEATS_KEY + sessionId);
        for (String sn : seatNos) {
            keys.add(RedisConstants.LOCK_SEAT_KEY + sessionId + ":" + sn);
        }
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(userId));
        args.addAll(seatNos);
        stringRedisTemplate.execute(RELEASE_SCRIPT, keys, args.toArray());
        // 补偿释放成功后清一人一单标记：此时用户并未生成有效订单(DB 已回滚)，应允许立即重试
        stringRedisTemplate.delete(
                RedisConstants.USER_ORDER_KEY + sessionId + ":" + userId);
    }
}
