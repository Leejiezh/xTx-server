package com.leejie.xtx.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
