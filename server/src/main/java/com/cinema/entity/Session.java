package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场次（购票核心对象）
 */
@Data
@TableName("tb_session")
public class Session {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 电影 */
    private Long movieId;

    /** 影院（冗余，便于按影院查） */
    private Long cinemaId;

    /** 影厅（决定座位布局） */
    private Long hallId;

    /** 开场时间 */
    private LocalDateTime startTime;

    /** 散场时间 */
    private LocalDateTime endTime;

    /** 统一票价 */
    private BigDecimal price;

    /** 热门场次：1 走抢资格流程 */
    private Boolean isHot;

    /** 开售时间：热门=抢资格开启时刻；普通可空=发布即可买 */
    private LocalDateTime saleStartTime;

    /** 截止售票：过它不再产生新订单 */
    private LocalDateTime saleEndTime;

    /** 0未开售/1售票中/2已结束 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
