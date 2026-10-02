package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.core.service.AuthService;
import com.leejie.xtx.wechat.service.WechatLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final WechatLoginService wechatLoginService;
    private final UserMapper userMapper;

    @Override
    public LoginVO login(String code) {
        String openid = wechatLoginService.login(code).openid();

        User user = findByOpenid(openid);
        if (user == null) {
            user = createUser(openid);
        }

        String token = JwtUtils.generate(Map.of(
                Constants.CLAIM_USER_ID, user.getId(),
                Constants.CLAIM_OPENID, openid
        ));
        return new LoginVO(token, UserVO.fromEntity(user));
    }

    private User findByOpenid(String openid) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenid, openid));
    }

    private User createUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            // 同一 openid 并发首登：另一个请求先插成功并撞上 uk_openid。回读赢家那行即可，
            // 对调用方而言仍是一次普通登录，不该暴露成 500。
            return findByOpenid(openid);
        }
    }
}
