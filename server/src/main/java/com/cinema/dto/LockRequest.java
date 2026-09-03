package com.cinema.dto;

import lombok.Data;

import java.util.List;

/**
 * 锁座 + 下单入参
 */
@Data
public class LockRequest {

    private Long sessionId;

    /** 选中的座位（多选，一单多座） */
    private List<SeatDTO> seats;
}
