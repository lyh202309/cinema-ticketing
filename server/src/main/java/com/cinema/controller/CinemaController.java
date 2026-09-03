package com.cinema.controller;

import com.cinema.common.Result;
import com.cinema.dto.CinemaDetailVO;
import com.cinema.dto.PageResult;
import com.cinema.entity.Cinema;
import com.cinema.service.ICinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 影院浏览（公开）
 */
@RestController
@RequestMapping("/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final ICinemaService cinemaService;

    @GetMapping
    public Result<PageResult<Cinema>> list(@RequestParam(required = false) String region,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(cinemaService.pageByRegion(region, page, size));
    }

    @GetMapping("/{id}")
    public Result<CinemaDetailVO> detail(@PathVariable Long id) {
        CinemaDetailVO vo = cinemaService.getDetail(id);
        return vo == null ? Result.fail("影院不存在") : Result.ok(vo);
    }
}
