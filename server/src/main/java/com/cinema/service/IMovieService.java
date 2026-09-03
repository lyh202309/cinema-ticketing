package com.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cinema.dto.PageResult;
import com.cinema.entity.Movie;

/**
 * 电影服务
 */
public interface IMovieService extends IService<Movie> {

    /** 电影列表（可按类型/上映状态筛：0未上映/1上映中/2下映，不传=全部，分页） */
    PageResult<Movie> pageByGenre(String genre, Integer status, Integer page, Integer size);

    /** 电影详情（布隆 + 空值哨兵 + 互斥锁缓存） */
    Movie getDetail(Long id);
}
