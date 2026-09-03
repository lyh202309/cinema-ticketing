package com.cinema.config;

import com.cinema.utils.LoginInterceptor;
import com.cinema.utils.RefreshTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册：
 * ① RefreshTokenInterceptor(order0) 拦截全部：刷新 token + 塞 UserHolder
 * ② LoginInterceptor(order1) 拦截需登录路径：校验登录态
 * 浏览接口(movie/cinema/session)公开；user 的 code/login 公开
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RefreshTokenInterceptor refreshTokenInterceptor;
    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/user/**", "/seckill/**", "/order/**", "/chat/**")
                .excludePathPatterns("/user/code", "/user/login")
                .order(1);
    }
}
