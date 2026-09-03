package com.cinema.dto;

import lombok.Data;

/**
 * 登录后放在 ThreadLocal / Redis token 中的用户信息（不含密码）
 */
@Data
public class UserDTO {

    private Long id;

    private String phone;

    private String nickName;

    private String icon;
}
