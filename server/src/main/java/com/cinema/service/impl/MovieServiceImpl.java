package com.cinema.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinema.dto.PageResult;
import com.cinema.entity.Movie;
import com.cinema.mapper.MovieMapper;
import com.cinema.service.IMovieService;
import com.cinema.utils.CacheClient;
import com.cinema.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements IMovieService {

    private final CacheClient cacheClient;

    @Override
    public PageResult<Movie> pageByGenre(String genre, Integer status, Integer page, Integer size) {
        Page<Movie> p = page(new Page<>(page, size),
                new LambdaQueryWrapper<Movie>()
                        .eq(StrUtil.isNotBlank(genre), Movie::getGenre, genre)
                        .eq(status != null, Movie::getStatus, status)
                        .orderByDesc(Movie::getRating));
        return PageResult.of(p);
    }

    @Override
    public Movie getDetail(Long id) {
        // 布隆 + 空值哨兵 + 互斥锁
        return cacheClient.queryDetail("movie", id, RedisConstants.CACHE_MOVIE_KEY,
                () -> getById(id), Movie.class);
    }
}
