package com.cinema.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场次列表项（平铺关联信息，供购票入口展示）
 */
@Data
public class SessionVO {

    private Long id;
    private Long movieId;
    private String movieTitle;
    private Long cinemaId;
    private String cinemaName;
    private Long hallId;
    private String hallName;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal price;

    /** 热门场次（走抢资格） */
    private Boolean isHot;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;

    /** 座位总数 = hall.rows × hall.cols */
    private Integer seatsTotal;
}
