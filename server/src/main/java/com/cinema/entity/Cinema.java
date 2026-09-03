package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 影院
 */
@Data
@TableName("tb_cinema")
public class Cinema {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 影院名称 */
    private String name;

    /** 所在区 */
    private String region;

    /** 地址 */
    private String address;

    /** 评分 */
    private BigDecimal rating;

    /** 联系电话 */
    private String phone;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
