package com.leejie.xtx.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.service.OwnedService;
import com.leejie.xtx.core.dto.LabelCountVO;
import com.leejie.xtx.core.dto.SearchQuery;
import com.leejie.xtx.core.entity.Record;

import java.util.List;

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

    /** 当前用户各标签笔记数（label 非空、未回收才计入；未分类不计入标签计数） */
    List<LabelCountVO> countByLabel();

    /**
     * 全文搜索：标题 + 正文 INSTR 字面子串匹配（utf8mb4_unicode_ci 下不区分大小写）。
     * raw SQL 在 RecordMapper.xml：user_id 归属、deleted=0、排除回收站都在 SQL 里显式写
     * （口径与 {@link #page} 一致），q / label 空 = 不过滤，按 id 倒序。
     */
    IPage<Record> search(SearchQuery query);
}
