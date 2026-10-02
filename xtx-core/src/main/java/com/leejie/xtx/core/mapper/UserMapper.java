package com.leejie.xtx.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leejie.xtx.core.entity.User;

/**
 * 用户表 Mapper。
 *
 * <p>user 表不继承 OwnedEntity（无 user_id / deleted），不能走 OwnedService，
 * 登录相关的读写直接落在本 Mapper 上。
 */
public interface UserMapper extends BaseMapper<User> {
}
