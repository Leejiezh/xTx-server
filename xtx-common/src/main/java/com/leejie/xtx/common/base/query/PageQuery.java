package com.leejie.xtx.common.base.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询参数基类。
 *
 * <p>各业务查询类继承本类再加自己的筛选字段，例如
 * {@code RecordQuery extends PageQuery} 加 category / startDate / endDate。
 *
 * <p>字段名与响应体 {@link com.leejie.xtx.common.base.vo.PageResult} 保持一致
 * (pageNum / pageSize)，前端「传什么回什么」，不用做名字映射。
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    /** 每页条数，上限 100 —— 防止前端传 pageSize=99999 拖垮库 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /**
     * 转成 MyBatis-Plus 的分页对象。
     *
     * <p>入参可能为 null(前端没传)，这里兜一层默认值，不依赖字段初始值 ——
     * 因为 {@code ?pageNum=} 这种空串会被绑定成 null 而不是保留默认值。
     */
    public <T> Page<T> toPage() {
        long num = pageNum == null ? 1L : pageNum;
        long size = pageSize == null ? 10L : pageSize;
        return new Page<>(num, size);
    }
}
