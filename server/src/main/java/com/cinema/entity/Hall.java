package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 影厅：rows×cols 定义座位布局（座位不建独立表）
 */
@Data
@TableName("tb_hall")
public class Hall {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属影院 */
    private Long cinemaId;

    /** 厅名，如"1号厅" */
    private String name;

    /** 行数 */
    private Integer rowCount;

    /** 列数 */
    private Integer colCount;

    private LocalDateTime createTime;
}
