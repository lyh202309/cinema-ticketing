package com.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cinema.dto.CinemaDetailVO;
import com.cinema.dto.PageResult;
import com.cinema.entity.Cinema;

/**
 * 影院服务
 */
public interface ICinemaService extends IService<Cinema> {

    /** 影院列表（可按区筛，分页） */
    PageResult<Cinema> pageByRegion(String region, Integer page, Integer size);

    /** 影院详情（含影厅列表，缓存） */
    CinemaDetailVO getDetail(Long id);
}
