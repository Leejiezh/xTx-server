package com.leejie.xtx.common.base.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leejie.xtx.common.base.service.BaseService;

public class BaseServiceImpl <M extends BaseMapper<T>, T> extends ServiceImpl<M, T> implements BaseService<T> {

    /**
     * 获取当前登录用户信息
     * @return SysUser
     */
//    public SysUser getCurrUser(){
//        return SecurityUtils.getLoginUser().getUser();
//    }


    /**
     * 获取当前登录用户ID
     * @return Long
     */
//    public Long getCurrUserId(){
//        return SecurityUtils.getUserId();
//    }
}
