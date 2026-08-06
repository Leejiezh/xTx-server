package com.leejie.xtx.common.base.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leejie.xtx.common.base.entity.OwnedEntity;
import com.leejie.xtx.common.base.query.PageQuery;

import java.util.List;
import java.util.function.Consumer;

/**
 * 用户私有数据的访问入口。
 *
 * <p><b>这个接口的全部意义：所有方法都没有 userId 参数。</b>
 * 调用者拿不到「查别人数据」的表达能力 —— 越权不靠 code review 拦，
 * 而是在类型层面就写不出来。
 *
 * <p>此前的做法是在 Controller 基类的 javadoc 里写「务必带
 * {@code .eq("user_id", getUserId())}」，那条规则要在 record / report 的
 * 10 个接口里各写一遍，漏一处就是能拉到别人记录的漏洞。现在收口到一处实现。
 *
 * <p>不属于当前用户的 id 一律按「不存在」处理(404)，不返回 403 ——
 * 403 会泄露「这条记录确实存在，只是不是你的」，等于送出一个越权探测接口。
 *
 * @param <T> 实体类型，必须携带 userId / deleted
 */
public interface OwnedService<T extends OwnedEntity> {

    /**
     * 新增，自动盖上当前用户。
     *
     * <p>入参里的 userId 会被忽略并覆盖，前端传谁的都不作数。
     *
     * @return 新记录主键。只返 id 不返完整实体，因为时间戳由 MySQL 默认值生成，
     *         save() 之后对象里 createdAt 仍是 null(见 {@link com.leejie.xtx.common.base.entity.BaseEntity})
     */
    Long create(T entity);

    /**
     * 按主键查当前用户的一条数据。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 404，不存在或不属于当前用户
     */
    T get(Long id);

    /**
     * 更新当前用户的一条数据。{@code entity.id} 必填；userId 与 deleted 改不动。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 404，不存在或不属于当前用户
     */
    void update(T entity);

    /**
     * 逻辑删除当前用户的一条数据。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 404，不存在或不属于当前用户
     */
    void delete(Long id);

    /**
     * 分页查当前用户的数据，默认按 id 倒序 —— 小程序两个列表 tab 都是时间倒序上拉加载。
     *
     * @param filters 业务筛选条件，可为 null。会被嵌套进括号内与 user_id 做 AND，
     *                因此里面用 {@code .or()} 也逃不出当前用户范围
     */
    IPage<T> page(PageQuery query, Consumer<QueryWrapper<T>> filters);

    /**
     * 不分页取当前用户的数据 —— 给「生成报告」用(要把日期区间内的记录全喂给 AI)。
     *
     * @param filters 同 {@link #page}；这里语义上必传，避免无意中把用户全部数据捞出来
     */
    List<T> list(Consumer<QueryWrapper<T>> filters);
}
