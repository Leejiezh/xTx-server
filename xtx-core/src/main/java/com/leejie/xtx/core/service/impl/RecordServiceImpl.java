package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.service.impl.OwnedServiceImpl;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.mapper.RecordMapper;
import com.leejie.xtx.core.service.FileService;
import com.leejie.xtx.core.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 记录表 服务实现
 *
 * <p>override create/update/delete 是为了钩住 file_metadata 的附件生命周期：
 * create → attach（TEMP 转 ATTACHED）；update → reconcile（新增的 ATTACHED、被移除的 DETACHED）；
 * delete → detachAll（进入 24h 宽限期，由 OrphanFileSweeper 物理清理）。
 *
 * <p>三个方法都在同一事务内：附件校验失败（如 objectKey 不属于当前用户）必须连带回滚
 * 记录本身，否则会留下一条引用了无效图片的记录。
 */
@Service
@RequiredArgsConstructor
public class RecordServiceImpl extends OwnedServiceImpl<RecordMapper, Record> implements RecordService {

    private final FileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Record entity) {
        normalizeContent(entity);
        Long id = super.create(entity);
        fileService.attach(id, entity.getImages());
        return id;
    }

    /**
     * images 为 null 时不动附件：MyBatis-Plus 的 NOT_NULL 策略会跳过该列，库里旧 objectKey 仍在，
     * 此时若按「新值为空」去 reconcile，会把仍被引用的图片标 DETACHED 并在宽限期后删掉，
     * 记录的图片就此裂掉。null = 未提交该字段，空数组 = 显式清空。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Record entity) {
        normalizeContent(entity);
        // 先取旧 images：super.update 之后实体里已是新值，无从对比
        List<String> oldKeys = super.get(entity.getId()).getImages();
        List<String> newKeys = entity.getImages();
        super.update(entity);
        if (newKeys != null) {
            fileService.reconcile(entity.getId(), newKeys, oldKeys);
        }
    }

    /**
     * 标题与正文至少一项非空；content 空串时兜底为 ""（content 列 NOT NULL，
     * 前端空正文会发空串而非缺省，这里再兜一道防止缺字段直接踩 DB 约束）。
     */
    private void normalizeContent(Record entity) {
        if (isBlank(entity.getTitle()) && isBlank(entity.getContent())) {
            throw new BusinessException(422, "标题与内容不能同时为空");
        }
        if (entity.getContent() == null) {
            entity.setContent("");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        List<String> oldKeys = super.get(id).getImages();
        super.delete(id);
        fileService.detachAll(oldKeys);
    }
}
