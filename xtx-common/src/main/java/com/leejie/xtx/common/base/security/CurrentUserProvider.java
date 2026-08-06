package com.leejie.xtx.common.base.security;

/**
 * 当前登录用户上下文。
 *
 * <p>为什么要这个接口：ownership 校验必须发生在 service 层(xtx-common)，
 * 而登录态在 xtx-api 的 Spring Security 里，依赖方向是 api -&gt; core -&gt; common，
 * common 看不到 api。所以这里只留接缝，实现由 api 注入。
 *
 * <p>两个适配器：api 的 {@code SecurityCurrentUserProvider}(读 SecurityContextHolder)
 * 与测试里的固定值假实现 —— 后者让「用户 1 拿不到用户 2 的数据」变成一个
 * 普通单元测试，不用起 Spring Security。
 */
public interface CurrentUserProvider {

    /**
     * 取当前登录用户 ID。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 401，未登录或登录态异常
     */
    Long currentUserId();
}
