package com.cinema.dto;

import com.cinema.entity.Cinema;
import com.cinema.entity.Hall;
import com.cinema.entity.Movie;
import com.cinema.entity.Session;
import lombok.Data;

/**
 * 场次详情（嵌套关联实体）
 */
@Data
public class SessionDetailVO {

    private Session session;

    private Movie movie;

    private Cinema cinema;

    private Hall hall;

    /** 座位总数 = hall.rows × hall.cols */
    private Integer seatsTotal;
}
