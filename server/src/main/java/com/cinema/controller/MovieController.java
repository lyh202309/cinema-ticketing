package com.cinema.controller;

import com.cinema.common.Result;
import com.cinema.dto.PageResult;
import com.cinema.entity.Movie;
import com.cinema.service.IMovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 电影浏览（公开）
 */
@RestController
@RequestMapping("/movie")
@RequiredArgsConstructor
public class MovieController {

    private final IMovieService movieService;

    @GetMapping
    public Result<PageResult<Movie>> list(@RequestParam(required = false) String genre,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(movieService.pageByGenre(genre, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        Movie movie = movieService.getDetail(id);
        return movie == null ? Result.fail("电影不存在") : Result.ok(movie);
    }
}
