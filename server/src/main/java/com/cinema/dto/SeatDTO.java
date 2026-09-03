package com.cinema.dto;

import lombok.Data;

/**
 * 座位坐标（1-based）
 */
@Data
public class SeatDTO {

    private Integer row;

    private Integer col;
}
