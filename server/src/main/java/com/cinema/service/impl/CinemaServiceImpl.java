package com.cinema.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinema.dto.CinemaDetailVO;
import com.cinema.dto.PageResult;
import com.cinema.entity.Cinema;
import com.cinema.entity.Hall;
import com.cinema.mapper.CinemaMapper;
import com.cinema.mapper.HallMapper;
import com.cinema.service.ICinemaService;
import com.cinema.utils.CacheClient;
import com.cinema.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CinemaServiceImpl extends ServiceImpl<CinemaMapper, Cinema> implements ICinemaService {

    private final CacheClient cacheClient;
    private final HallMapper hallMapper;

    @Override
    public PageResult<Cinema> pageByRegion(String region, Integer page, Integer size) {
        Page<Cinema> p = page(new Page<>(page, size),
                new LambdaQueryWrapper<Cinema>()
                        .eq(StrUtil.isNotBlank(region), Cinema::getRegion, region)
                        .orderByDesc(Cinema::getRating));
        return PageResult.of(p);
    }

    @Override
    public CinemaDetailVO getDetail(Long id) {
        // 影院 + 影厅整体缓存（布隆 + 空值 + 互斥锁）
        return cacheClient.queryDetail("cinema", id, RedisConstants.CACHE_CINEMA_KEY, () -> {
            Cinema cinema = getById(id);
            if (cinema == null) {
                return null;
            }
            CinemaDetailVO vo = new CinemaDetailVO();
            vo.setCinema(cinema);
            vo.setHalls(hallMapper.selectList(
                    new LambdaQueryWrapper<Hall>().eq(Hall::getCinemaId, id).orderByAsc(Hall::getId)));
            return vo;
        }, CinemaDetailVO.class);
    }
}
