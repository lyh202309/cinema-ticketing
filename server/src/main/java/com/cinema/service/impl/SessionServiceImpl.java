package com.cinema.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinema.dto.PageResult;
import com.cinema.dto.SessionDetailVO;
import com.cinema.dto.SessionVO;
import com.cinema.entity.Cinema;
import com.cinema.entity.Hall;
import com.cinema.entity.Movie;
import com.cinema.entity.Session;
import com.cinema.mapper.CinemaMapper;
import com.cinema.mapper.HallMapper;
import com.cinema.mapper.MovieMapper;
import com.cinema.mapper.SessionMapper;
import com.cinema.service.ISessionService;
import com.cinema.utils.CacheClient;
import com.cinema.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl extends ServiceImpl<SessionMapper, Session> implements ISessionService {

    private final CacheClient cacheClient;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;

    @Override
    public PageResult<SessionVO> pageSessions(Long movieId, Long cinemaId, String date,
                                              Integer page, Integer size) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<Session>()
                .eq(movieId != null, Session::getMovieId, movieId)
                .eq(cinemaId != null, Session::getCinemaId, cinemaId);
        if (StrUtil.isNotBlank(date)) {
            // date 语义：start_time 落在 [当天 00:00, 次日 00:00)
            LocalDate day = LocalDate.parse(date.trim());
            LocalDateTime dayStart = day.atStartOfDay();
            wrapper.ge(Session::getStartTime, dayStart)
                    .lt(Session::getStartTime, dayStart.plusDays(1));
        }
        wrapper.orderByAsc(Session::getStartTime);

        Page<Session> p = page(new Page<>(page, size), wrapper);

        PageResult<SessionVO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setRecords(assembleList(p.getRecords()));
        return result;
    }

    @Override
    public SessionDetailVO getDetail(Long id) {
        return cacheClient.queryDetail("session", id, RedisConstants.CACHE_SESSION_KEY,
                () -> buildDetail(id), SessionDetailVO.class);
    }

    // ===== 组装 =====

    private List<SessionVO> assembleList(List<Session> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, Movie> movieMap = idMap(movieMapper.selectBatchIds(
                sessions.stream().map(Session::getMovieId).distinct().toList()), Movie::getId);
        Map<Long, Cinema> cinemaMap = idMap(cinemaMapper.selectBatchIds(
                sessions.stream().map(Session::getCinemaId).distinct().toList()), Cinema::getId);
        Map<Long, Hall> hallMap = idMap(hallMapper.selectBatchIds(
                sessions.stream().map(Session::getHallId).distinct().toList()), Hall::getId);

        return sessions.stream().map(s -> {
            SessionVO vo = new SessionVO();
            copySession(s, vo);
            Movie movie = movieMap.get(s.getMovieId());
            Cinema cinema = cinemaMap.get(s.getCinemaId());
            Hall hall = hallMap.get(s.getHallId());
            if (movie != null) {
                vo.setMovieTitle(movie.getTitle());
            }
            if (cinema != null) {
                vo.setCinemaName(cinema.getName());
            }
            if (hall != null) {
                vo.setHallName(hall.getName());
                vo.setSeatsTotal(hall.getRowCount() * hall.getColCount());
            }
            return vo;
        }).toList();
    }

    private SessionDetailVO buildDetail(Long id) {
        Session session = getById(id);
        if (session == null) {
            return null;
        }
        SessionDetailVO vo = new SessionDetailVO();
        vo.setSession(session);
        vo.setMovie(movieMapper.selectById(session.getMovieId()));
        vo.setCinema(cinemaMapper.selectById(session.getCinemaId()));
        Hall hall = hallMapper.selectById(session.getHallId());
        vo.setHall(hall);
        if (hall != null) {
            vo.setSeatsTotal(hall.getRowCount() * hall.getColCount());
        }
        return vo;
    }

    private void copySession(Session s, SessionVO vo) {
        vo.setId(s.getId());
        vo.setMovieId(s.getMovieId());
        vo.setCinemaId(s.getCinemaId());
        vo.setHallId(s.getHallId());
        vo.setStartTime(s.getStartTime());
        vo.setEndTime(s.getEndTime());
        vo.setPrice(s.getPrice());
        vo.setIsHot(s.getIsHot());
        vo.setSaleStartTime(s.getSaleStartTime());
        vo.setSaleEndTime(s.getSaleEndTime());
    }

    private <T> Map<Long, T> idMap(List<T> list, Function<T, Long> idGetter) {
        if (list == null) {
            return Map.of();
        }
        return list.stream().collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }
}
