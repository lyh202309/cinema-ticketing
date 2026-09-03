package com.cinema.utils;

import com.cinema.dto.UserDTO;

/**
 * 基于 ThreadLocal 保存当前登录用户
 * 由 RefreshTokenInterceptor 写入，请求结束后清除
 */
public class UserHolder {

    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        TL.set(user);
    }

    public static UserDTO getUser() {
        return TL.get();
    }

    /** 当前登录用户 id；未登录返回 null */
    public static Long getUserId() {
        UserDTO user = TL.get();
        return user == null ? null : user.getId();
    }

    public static void remove() {
        TL.remove();
    }
}
