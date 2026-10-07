package com.leejie.xtx.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leejie.xtx.core.dto.LabelCountVO;
import com.leejie.xtx.core.entity.Record;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 记录表 Mapper
 */
public interface RecordMapper extends BaseMapper<Record> {

    /**
     * 当前用户各标签笔记数：以字典为基准返回全部启用标签（含 0 笔记），按 sortOrder 排序。
     * SQL 在 RecordMapper.xml（多表 LEFT JOIN），deleted/recycled 过滤口径与 /record/page 一致。
     *
     * @param userId   当前登录用户
     * @param typeCode 字典类型码（笔记标签 = note_label）
     */
    List<LabelCountVO> countByLabel(@Param("userId") Long userId, @Param("typeCode") String typeCode);

    /**
     * 搜索（raw SQL 在 RecordMapper.xml）：标题/正文 INSTR 子串匹配 + label 过滤。
     * 原始 SQL 不走 @TableLogic / OwnedService，user_id 归属、deleted=0、排除回收站
     * 都在 XML 里显式写（口径与 /record/page 一致）；分页由 MP 拦截器按第一个 IPage 参数自动追加。
     *
     * @param page  MP 分页对象（拦截器据它生成 count + limit）
     * @param userId 当前登录用户（归属过滤，不信任入参）
     * @param kw    关键词，null/空 = 不过滤
     * @param label 标签（dict_item.item_key），null/空 = 不过滤
     */
    IPage<Record> search(IPage<Record> page, @Param("userId") Long userId,
                         @Param("kw") String kw, @Param("label") String label);
}
