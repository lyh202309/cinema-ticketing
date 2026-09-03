package com.cinema.controller;

import cn.hutool.core.util.StrUtil;
import com.cinema.common.Result;
import com.cinema.dto.LoginFormDTO;
import com.cinema.dto.UserDTO;
import com.cinema.service.IUserService;
import com.cinema.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户模块：验证码 / 登录 / 登出 / 我的信息
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /** 发送验证码（公开） */
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam("phone") String phone) {
        if (!phone.matches("^1\\d{10}$")) {
            return Result.fail("手机号格式错误");
        }
        userService.sendCode(phone);
        return Result.ok();
    }

    /** 登录（公开）：{phone, code} 或 {phone, password}，返回 token */
    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid LoginFormDTO loginForm) {
        return Result.ok(userService.login(loginForm));
    }

    /** 登出（需登录） */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            userService.logout(token);
        }
        return Result.ok();
    }

    /** 我的信息（需登录） */
    @GetMapping("/me")
    public Result<UserDTO> me() {
        return Result.ok(UserHolder.getUser());
    }
}
