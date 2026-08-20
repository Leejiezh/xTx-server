package com.leejie.xtx.common.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leejie.xtx.common.base.entity.OwnedEntity;
import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.base.service.OwnedService;
import com.leejie.xtx.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@link OwnedService} 的唯一实现 —— ownership 约束在这一个文件里收口。
 *
 * <p>用字符串列名而非 {@code LambdaQueryWrapper} 的方法引用：本类是泛型基类，
 * 无法写 {@code T::getUserId} 这样的方法引用，只能用字符串。id 与 user_id 这两个
 * 列名在 DDL 里写死且 record / report 两表一致，用字符串是安全的。
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 */
@Service
public abstract class OwnedServiceImpl<M extends BaseMapper<T>, T extends OwnedEntity>
        extends ServiceImpl<M, T> implements OwnedService<T> {

    /**
     * 字段注入而非构造注入：子类需要注入自己的业务依赖，若走构造注入，
     * 每个子类都得复述一遍 {@code super(currentUser)} —— 那样基类反而变浅了。
     */
    @Autowired
    protected CurrentUserProvider currentUser;

    @Override
    public Long create(T entity) {
        // 覆盖而非校验：前端传了别人的 userId 也直接作废，不报错、不给探测机会
        entity.setUserId(currentUser.currentUserId());
        entity.setId(null);
        super.save(entity);
        return entity.getId();
    }

    @Override
    public T get(Long id) {
        requireId(id);
        // @TableLogic 会自动追加 deleted = 0
        T found = super.getOne(ownedById(id));
        if (found == null) {
            throw notFound();
        }
        return found;
    }

    @Override
    public void update(T entity) {
        Long id = entity.getId();
        requireId(id);

        // 先确认归属再 updateById，两条 SQL。
        // 也可以用 update(entity, wrapper) 一条搞定，但那样得先把 entity.id 置空
        // 避免生成 SET id = ?、事后再还原 —— 副作用藏在入参对象里更难读。
        // 单用户量级不差这一次 exists 查询，可读性优先。
        if (!super.exists(ownedById(id))) {
            throw notFound();
        }

        // 置 null 后 MyBatis-Plus 默认的 NOT_NULL 更新策略会跳过这两列，
        // 于是调用者改不动数据归属，也没法把 deleted 从 1 改回 0
        entity.setUserId(null);
        entity.setDeleted(null);
        super.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireId(id);
        // @TableLogic 让 remove 变成 UPDATE ... SET deleted = 1
        if (!super.remove(ownedById(id))) {
            throw notFound();
        }
    }

    @Override
    public IPage<T> page(PageQuery query, Consumer<QueryWrapper<T>> filters) {
        QueryWrapper<T> wrapper = owned();
        applyFilters(wrapper, filters);
        wrapper.orderByDesc("id");
        return super.page(query.toPage(), wrapper);
    }

    @Override
    public List<T> list(Consumer<QueryWrapper<T>> filters) {
        QueryWrapper<T> wrapper = owned();
        applyFilters(wrapper, filters);
        return super.list(wrapper);
    }

    /** user_id = 当前用户 */
    private QueryWrapper<T> owned() {
        return new QueryWrapper<T>().eq("user_id", currentUser.currentUserId());
    }

    /** id = ? AND user_id = 当前用户 */
    private QueryWrapper<T> ownedById(Long id) {
        return owned().eq("id", id);
    }

    /**
     * 把业务条件包进 {@code and(...)} 的括号里。
     *
     * <p>关键防护：若直接让调用者往 wrapper 上追加条件，一个 {@code .or()} 就能把
     * SQL 变成 {@code user_id = 1 OR category = 'LIFE'} —— 全站数据泄露。
     * 嵌套成 {@code user_id = 1 AND (业务条件)} 之后，括号里怎么 or 都出不去。
     */
    private void applyFilters(QueryWrapper<T> wrapper, Consumer<QueryWrapper<T>> filters) {
        if (filters != null) {
            wrapper.and(filters::accept);
        }
    }

    private void requireId(Long id) {
        if (id == null) {
            throw new BusinessException(422, "缺少 id");
        }
    }

    /** 统一 404：不区分「不存在」与「不是你的」，否则等于提供越权探测接口 */
    private BusinessException notFound() {
        return new BusinessException(404, "数据不存在");
    }
}
