package com.cinema.controller;

import com.cinema.common.Result;
import com.cinema.dto.PageResult;
import com.cinema.dto.SessionDetailVO;
import com.cinema.dto.SessionVO;
import com.cinema.service.ISessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 场次浏览（公开）
 */
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final ISessionService sessionService;

    @GetMapping
    public Result<PageResult<SessionVO>> list(@RequestParam(value = "movieId", required = false) Long movieId,
                                              @RequestParam(value = "cinemaId", required = false) Long cinemaId,
                                              @RequestParam(value = "date", required = false) String date,
                                              @RequestParam(value = "page", defaultValue = "1") Integer page,
                                              @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.ok(sessionService.pageSessions(movieId, cinemaId, date, page, size));
    }

    @GetMapping("/{id}")
    public Result<SessionDetailVO> detail(@PathVariable("id") Long id) {
        SessionDetailVO vo = sessionService.getDetail(id);
        return vo == null ? Result.fail("场次不存在") : Result.ok(vo);
    }
}
