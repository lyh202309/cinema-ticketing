package com.cinema.dto;

import com.cinema.entity.Cinema;
import com.cinema.entity.Hall;
import lombok.Data;

import java.util.List;

/**
 * 影院详情：影院 + 影厅列表（含 rows×cols 布局）
 */
@Data
public class CinemaDetailVO {

    private Cinema cinema;

    private List<Hall> halls;
}
