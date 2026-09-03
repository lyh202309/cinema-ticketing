package com.cinema;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电影院订票系统启动类
 */
@EnableScheduling
@MapperScan("com.cinema.mapper")
@SpringBootApplication
public class CinemaTicketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinemaTicketingApplication.class, args);
    }
}
