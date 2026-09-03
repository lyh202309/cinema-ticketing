package com.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cinema.dto.LoginFormDTO;
import com.cinema.entity.User;

/**
 * 用户服务：验证码/登录/登出（me 直接读 ThreadLocal，无需服务层）
 */
public interface IUserService extends IService<User> {

    /** 发送验证码（模拟，验证码存 Redis） */
    void sendCode(String phone);

    /** 登录：验证码 或 密码；返回 token */
    String login(LoginFormDTO loginForm);

    /** 登出：删除 Redis token */
    void logout(String token);
}
