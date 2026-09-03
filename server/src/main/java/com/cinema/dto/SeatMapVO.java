package com.cinema.dto;

import lombok.Data;

/**
 * 座位图（0 可售 / 1 占用）
 */
@Data
public class SeatMapVO {

    private Integer rows;

    private Integer cols;

    /** seats[r][c]，r/c 均为 0-based；值 0 可售 / 1 占用 */
    private int[][] seats;
}
