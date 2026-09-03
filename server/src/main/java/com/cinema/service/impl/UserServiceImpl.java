package com.cinema.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinema.common.BusinessException;
import com.cinema.dto.LoginFormDTO;
import com.cinema.dto.UserDTO;
import com.cinema.entity.User;
import com.cinema.mapper.UserMapper;
import com.cinema.service.IUserService;
import com.cinema.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void sendCode(String phone) {
        // 生成 6 位验证码
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES);
        // 模拟发送：打印日志代替真实短信
        log.info("[模拟短信] 手机号 {} 验证码 {}", phone, code);
    }

    @Override
    public String login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String password = loginForm.getPassword();

        // 按当前用户
        User user = lambdaQuery().eq(User::getPhone, phone).one();

        if (StrUtil.isNotBlank(code)) {
            // ===== 验证码登录 =====
            String redisCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
            if (StrUtil.isBlank(redisCode)) {
                throw new BusinessException("验证码已过期");
            }
            if (!redisCode.equals(code)) {
                throw new BusinessException("验证码错误");
            }
            // 校验通过，删验证码
            stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
            // 首次登录自动注册
            if (user == null) {
                user = createUser(phone);
            }
        } else if (StrUtil.isNotBlank(password)) {
            // ===== 密码登录 =====
            if (user == null) {
                throw new BusinessException("账号不存在");
            }
            if (StrUtil.isBlank(user.getPassword())) {
                throw new BusinessException("该账号未设置密码，请用验证码登录");
            }
            if (!BCrypt.checkpw(password, user.getPassword())) {
                throw new BusinessException("密码错误");
            }
        } else {
            throw new BusinessException("验证码或密码不能为空");
        }

        return saveToken(user);
    }

    @Override
    public void logout(String token) {
        stringRedisTemplate.delete(RedisConstants.LOGIN_TOKEN_KEY + token);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户" + RandomUtil.randomString(6));
        user.setIcon("");
        save(user);
        return user;
    }

    private String saveToken(User user) {
        // 生成 token 并缓存 UserDTO
        String token = IdUtil.fastSimpleUUID();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(userDTO),
                RedisConstants.LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        return token;
    }
}
