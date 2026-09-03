package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电影
 */
@Data
@TableName("tb_movie")
public class Movie {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 片名 */
    private String title;

    /** 类型 */
    private String genre;

    /** 时长(分钟) */
    private Integer duration;

    /** 导演 */
    private String director;

    /** 评分 */
    private BigDecimal rating;

    /** 海报 */
    private String poster;

    /** 上映日期 */
    private LocalDate releaseDate;

    /** 简介 */
    private String description;

    /** 0未上映/1上映中/2下映 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
