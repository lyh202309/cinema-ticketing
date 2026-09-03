package com.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cinema.dto.PageResult;
import com.cinema.dto.SessionDetailVO;
import com.cinema.dto.SessionVO;
import com.cinema.entity.Session;

/**
 * 场次服务
 */
public interface ISessionService extends IService<Session> {

    /**
     * 场次查询：movieId / cinemaId / date 三参数可组合筛选
     * date 语义：start_time 落在 [date 00:00, date+1 00:00)
     */
    PageResult<SessionVO> pageSessions(Long movieId, Long cinemaId, String date,
                                       Integer page, Integer size);

    /** 场次详情（聚合电影/影院/影厅 + 座位总数，缓存） */
    SessionDetailVO getDetail(Long id);
}
