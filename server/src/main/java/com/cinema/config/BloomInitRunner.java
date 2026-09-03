package com.cinema.config;

import com.cinema.mapper.CinemaMapper;
import com.cinema.mapper.MovieMapper;
import com.cinema.mapper.SessionMapper;
import com.cinema.utils.BloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把合法 id 全部加入布隆过滤器（movie/cinema/session）
 * 保证详情查询前布隆已有完整集合；后续若有新增数据需同步 add
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomInitRunner implements CommandLineRunner {

    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final SessionMapper sessionMapper;
    private final BloomFilter bloomFilter;

    @Override
    public void run(String... args) {
        movieMapper.selectList(null).forEach(m -> bloomFilter.add("movie", String.valueOf(m.getId())));
        cinemaMapper.selectList(null).forEach(c -> bloomFilter.add("cinema", String.valueOf(c.getId())));
        sessionMapper.selectList(null).forEach(s -> bloomFilter.add("session", String.valueOf(s.getId())));
        log.info("[布隆过滤器] movie/cinema/session 已初始化");
    }
}
