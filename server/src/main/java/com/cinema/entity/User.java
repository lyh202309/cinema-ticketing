package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@TableName("tb_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号 */
    private String phone;

    /** 密码(BCrypt)，可空——验证码登录用户无密码 */
    private String password;

    /** 昵称 */
    private String nickName;

    /** 头像 */
    private String icon;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
