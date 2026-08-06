package com.leejie.xtx.api.base;

import com.leejie.xtx.api.config.security.SecurityUtils;

/**
 * Controller 基类 —— 只提供「取当前登录用户 ID」。
 *
 * <p>刻意不做泛型 CRUD 基类：那样每个 Controller 要填 5 个泛型参数，
 * 而且想知道一个接口长什么样得跳三个文件。业务 Controller 里
 * 直接把 5 个方法写出来更好读，代码量也就多几十行。
 *
 * <p><b>注意</b>：涉及用户数据的查询/更新/删除，务必在 Wrapper 上
 * 带 {@code .eq("user_id", getUserId())} —— 漏写就是越权漏洞。
 */
public abstract class BaseController {

    /** 取当前登录用户 ID，由 Spring Security 的 SecurityContextHolder 提供 */
    protected Long getUserId() {
        return SecurityUtils.getCurrentUserId();
    }
}
