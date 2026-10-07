package com.leejie.xtx.core.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.service.impl.OwnedServiceImpl;
import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.dto.LabelCountVO;
import com.leejie.xtx.core.dto.SearchQuery;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.mapper.RecordMapper;
import com.leejie.xtx.core.service.FileService;
import com.leejie.xtx.core.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 记录表 服务实现
 *
 * <p>create → attach（TEMP 转 ATTACHED）；update → reconcile（新增的 ATTACHED、被移除的 DETACHED）；
 * delete → 移入回收站（置 recycled_at，不删行、不动图片，可完整恢复）；
 * purge → 彻底删除（仅回收站内记录，@TableLogic 置 deleted=1 + detachAll 让图片进 24h 宽限期）。
 *
 * <p>普通查询（page/get/update）一律排除回收站记录；回收站的列表/恢复接口（RecycleController）
 * 后续复用 {@link #recycledById} 同源的 recycled_at 条件。
 *
 * <p>会改附件的方法都在同一事务内：附件校验失败必须连带回滚记录本身，
 * 否则会留下一条引用了无效图片的记录。
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
        // 先取旧 images：super.update 之后实体里已是新值，无从对比。
        // 用本类 get() 而非 super.get()：回收站记录不可编辑（404）。
        List<String> oldKeys = get(entity.getId()).getImages();
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

    /**
     * 移入回收站：只置 recycled_at，不删行、不 detach 图片 —— 回收站内图片保留，
     * 恢复后能完整还原。彻底删除见 {@link #purge}。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 归属 + 存在 + 未回收；已回收的记录再删 → 404（应走 purge）
        get(id);
        LambdaUpdateWrapper<Record> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Record::getId, id)
                .eq(Record::getUserId, currentUser.currentUserId())
                .set(Record::getRecycledAt, LocalDateTime.now());
        super.update(null, wrapper);
    }

    @Override
    public Record get(Long id) {
        Record record = super.get(id);
        if (record.getRecycledAt() != null) {
            throw new BusinessException(404, "数据不存在");
        }
        return record;
    }

    /** 普通分页恒排除回收站记录（回收站列表走 {@link #recycledById} 同源条件） */
    @Override
    public IPage<Record> page(PageQuery query, Consumer<QueryWrapper<Record>> filters) {
        Consumer<QueryWrapper<Record>> scoped = filters == null
                ? w -> w.isNull("recycled_at")
                : filters.andThen(w -> w.isNull("recycled_at"));
        return super.page(query, scoped);
    }

    /**
     * 彻底删除：仅回收站内记录。@TableLogic 把 remove 变成 UPDATE ... SET deleted=1，
     * 行留库但从所有查询消失（普通查询与回收站查询都带 deleted=0，不可再恢复）；
     * 图片 detach 后进入 24h 宽限期由 OrphanFileSweeper 物理清理。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purge(Long id) {
        Record record = super.getOne(recycledById(id));
        if (record == null) {
            throw new BusinessException(404, "数据不存在");
        }
        super.remove(recycledById(id));
        fileService.detachAll(record.getImages());
    }

    /** 回收站分页：仅回收站内记录，按进回收站时间倒序（最新删除在前） */
    @Override
    public IPage<Record> recyclePage(PageQuery query) {
        return super.page(query.toPage(), new QueryWrapper<Record>()
                .eq("user_id", currentUser.currentUserId())
                .isNotNull("recycled_at")
                .orderByDesc("recycled_at"));
    }

    /**
     * 从回收站恢复：置 recycled_at=null，图片随记录一并还原（回收时未 detach）。
     * 仅回收站内记录可恢复（否则 404），已彻底删除的记录不可恢复。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        if (!super.exists(recycledById(id))) {
            throw new BusinessException(404, "数据不存在");
        }
        // 用 setSql 置 NULL：lambda 的 set(column, null) 对 null 值在不同版本行为不一，
        // 显式 SQL 保证生成 SET recycled_at = NULL
        LambdaUpdateWrapper<Record> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Record::getId, id)
                .eq(Record::getUserId, currentUser.currentUserId())
                .setSql("recycled_at = NULL");
        super.update(null, wrapper);
    }

    /** 回收站内的记录：归属 + 存在 + 已进回收站（recycled_at 非空）；@TableLogic 自动附加 deleted=0 */
    private QueryWrapper<Record> recycledById(Long id) {
        return new QueryWrapper<Record>()
                .eq("user_id", currentUser.currentUserId())
                .eq("id", id)
                .isNotNull("recycled_at");
    }

    /** 当前用户各标签笔记数：归属边界与 OwnedService 一致（userId 取自登录态，不信任入参） */
    @Override
    public List<LabelCountVO> countByLabel() {
        return baseMapper.countByLabel(currentUser.currentUserId(), Constants.DICT_TYPE_NOTE_LABEL);
    }

    /**
     * 全文搜索：标题/正文 INSTR 字面子串匹配（utf8mb4_unicode_ci 下不区分大小写），
     * 不走 LIKE 避免 % / _ 通配符转义，个人笔记量级不建全文索引。
     * raw SQL 在 RecordMapper.xml：user_id 归属、deleted=0、排除回收站都在 SQL 里显式写，
     * kw / label 空 = 不过滤；分页由 MP 拦截器按 page 参数自动追加。
     */
    @Override
    public IPage<Record> search(SearchQuery query) {
        String kw = StrUtil.trimToNull(query.getQ());
        String label = StrUtil.trimToNull(query.getLabel());
        return baseMapper.search(query.toPage(), currentUser.currentUserId(), kw, label);
    }
}
