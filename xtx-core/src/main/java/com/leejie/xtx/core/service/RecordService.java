package com.leejie.xtx.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.service.OwnedService;
import com.leejie.xtx.core.entity.Record;

/**
 * 记录表 服务接口
 */
public interface RecordService extends OwnedService<Record> {

    /**
     * 彻底删除：仅回收站内的记录，物理删图片（进入 24h 宽限期）。
     * 普通删除（移入回收站）见 {@link OwnedService#delete}。
     */
    void purge(Long id);

    /** 回收站分页：仅回收站内记录，按进回收站时间倒序（最新删除在前） */
    IPage<Record> recyclePage(PageQuery query);

    /**
     * 从回收站恢复：置 recycled_at=null，图片随记录一并还原（回收时未 detach）。
     * 仅回收站内记录可恢复，否则 404。
     */
    void restore(Long id);
}
