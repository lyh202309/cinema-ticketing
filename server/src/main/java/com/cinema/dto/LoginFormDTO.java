package com.cinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录入参：手机号 + (验证码 或 密码)
 */
@Data
public class LoginFormDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
    private String phone;

    /** 验证码登录时填写 */
    private String code;

    /** 密码登录时填写 */
    private String password;
}
