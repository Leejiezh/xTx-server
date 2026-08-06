package com.leejie.xtx.common.base.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体。
 *
 * <p>不直接把 MyBatis-Plus 的 {@code IPage} 丢给前端 —— 那会把
 * orders / optimizeCountSql / searchCount / countId 等内部字段一起序列化出去。
 *
 * <p>带 hasNext 是因为小程序两个列表 tab 都是上拉加载(时间倒序无限流)，
 * 前端只需 {@code if (hasNext) pageNum++}，不用自己算页数。
 */
@Data
public class PageResult<V> {

    /** 当前页数据 */
    private List<V> list;

    /** 总条数，给「共 N 条」文案用 */
    private long total;

    /** 当前页码，与请求参数同名 */
    private long pageNum;

    /** 每页条数，与请求参数同名 */
    private long pageSize;

    /** 是否还有下一页 */
    private boolean hasNext;

    /**
     * 由实体分页 + 转换函数构造 VO 分页。
     *
     * @param page 实体分页结果
     * @param conv 实体 -> VO 的转换函数(通常传 Controller 的 this::toVo)
     */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> conv) {
        PageResult<V> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());

        List<E> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            result.setList(Collections.emptyList());
        } else {
            result.setList(records.stream().map(conv).toList());
        }

        // current * size < total 说明后面还有数据
        result.setHasNext(page.getCurrent() * page.getSize() < page.getTotal());
        return result;
    }
}
