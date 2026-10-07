package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.dto.LabelCountVO;
import com.leejie.xtx.core.dto.SearchQuery;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.mapper.RecordMapper;
import com.leejie.xtx.core.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记录删除与回收站行为：
 *
 * <p>普通删除 = 移入回收站（置 recycled_at，不删行、不动图片）；
 * purge = 彻底删除（回收站内记录，@TableLogic 置 deleted=1 + detach 图片）。
 * 普通查询（page/get/update/delete）一律排除回收站记录。
 */
@ExtendWith(MockitoExtension.class)
class RecordServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long RECORD_ID = 100L;

    @Mock
    private RecordMapper recordMapper;
    @Mock
    private FileService fileService;
    @Mock
    private CurrentUserProvider currentUser;

    private RecordServiceImpl recordService;

    @BeforeEach
    void setUp() {
        recordService = new RecordServiceImpl(fileService);
        ReflectionTestUtils.setField(recordService, "currentUser", currentUser);
        ReflectionTestUtils.setField(recordService, "baseMapper", recordMapper);
    }

    private Record record(LocalDateTime recycledAt) {
        Record r = new Record();
        r.setId(RECORD_ID);
        r.setUserId(USER_ID);
        r.setRecycledAt(recycledAt);
        r.setImages(List.of("img/1/a.jpg"));
        return r;
    }

    @Test
    @DisplayName("删除笔记 → 置 recycled_at，不删行、不 detach 图片")
    void delete_movesToRecycle_keepsImages() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(record(null));

        recordService.delete(RECORD_ID);

        ArgumentCaptor<LambdaUpdateWrapper> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(eq(null), cap.capture());
        assertTrue(cap.getValue().getSqlSet().contains("recycled_at"));
        // 未走逻辑删除、未动图片
        verify(recordMapper, never()).delete(any());
        verify(fileService, never()).detachAll(any());
    }

    @Test
    @DisplayName("删除已在回收站的笔记 → 404（应走彻底删除）")
    void delete_recycled_throws404() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(record(LocalDateTime.now()));

        assertEquals(404, assertThrows(BusinessException.class,
                () -> recordService.delete(RECORD_ID)).getCode());
        verify(recordMapper, never()).update(eq(null), any());
        verify(fileService, never()).detachAll(any());
    }

    @Test
    @DisplayName("get 正常记录 → 返回")
    void get_normal_ok() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(record(null));

        assertEquals(RECORD_ID, recordService.get(RECORD_ID).getId());
    }

    @Test
    @DisplayName("get 对回收站记录 → 404")
    void get_recycled_throws404() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(record(LocalDateTime.now()));

        assertEquals(404, assertThrows(BusinessException.class,
                () -> recordService.get(RECORD_ID)).getCode());
    }

    @Test
    @DisplayName("update 对回收站记录 → 404（不可编辑）")
    void update_recycled_throws404() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(record(LocalDateTime.now()));
        Record update = new Record();
        update.setId(RECORD_ID);
        update.setTitle("改");
        update.setContent("改");

        assertEquals(404, assertThrows(BusinessException.class,
                () -> recordService.update(update)).getCode());
    }

    @Test
    @DisplayName("page 过滤回收站记录（recycled_at IS NULL）")
    void page_excludesRecycled() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectPage(any(), any())).thenReturn(new Page<>());

        recordService.page(new PageQuery(), null);

        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(recordMapper).selectPage(any(), cap.capture());
        assertTrue(cap.getValue().getSqlSegment().contains("recycled_at IS NULL"));
    }

    @Test
    @DisplayName("search 委托 mapper 原生 SQL：关键词 + 标签 + 当前用户")
    void search_delegatesToMapper_withKeywordAndLabel() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        Page<Record> expected = new Page<>();
        when(recordMapper.search(any(), eq(USER_ID), eq("MySQL"), eq("work"))).thenReturn(expected);

        SearchQuery query = new SearchQuery();
        query.setQ("MySQL");
        query.setLabel("work");
        IPage<Record> result = recordService.search(query);

        assertSame(expected, result);
        verify(recordMapper).search(any(), eq(USER_ID), eq("MySQL"), eq("work"));
    }

    @Test
    @DisplayName("search 空关键词/标签 → 传 null，不加过滤条件")
    void search_blankKeyword_passesNull() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.search(any(), eq(USER_ID), isNull(), isNull())).thenReturn(new Page<>());

        recordService.search(new SearchQuery());

        verify(recordMapper).search(any(), eq(USER_ID), isNull(), isNull());
    }

    @Test
    @DisplayName("purge 回收站记录 → 逻辑删除 + detach 图片")
    void purge_recycled_ok() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(record(LocalDateTime.now()));

        recordService.purge(RECORD_ID);

        verify(recordMapper).delete(any());
        verify(fileService).detachAll(List.of("img/1/a.jpg"));
    }

    @Test
    @DisplayName("purge 非回收站记录（或不存在）→ 404，不删不 detach")
    void purge_notRecycled_throws404() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectOne(any())).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> recordService.purge(RECORD_ID)).getCode());
        verify(recordMapper, never()).delete(any());
        verify(fileService, never()).detachAll(any());
    }

    @Test
    @DisplayName("recyclePage 只查回收站记录，按进回收站时间倒序")
    void recyclePage_filtersRecycledAndOrdersDesc() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectPage(any(), any())).thenReturn(new Page<>());

        recordService.recyclePage(new PageQuery());

        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(recordMapper).selectPage(any(), cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("recycled_at IS NOT NULL"));
        assertTrue(sql.contains("recycled_at DESC"));
    }

    @Test
    @DisplayName("restore 回收站记录 → 置 recycled_at=null，不碰图片")
    void restore_recycled_ok() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectCount(any())).thenReturn(1L);

        recordService.restore(RECORD_ID);

        ArgumentCaptor<LambdaUpdateWrapper> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(eq(null), cap.capture());
        assertTrue(cap.getValue().getSqlSet().contains("recycled_at"));
        verify(fileService, never()).detachAll(any());
    }

    @Test
    @DisplayName("restore 非回收站记录（或不存在）→ 404")
    void restore_notRecycled_throws404() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(recordMapper.selectCount(any())).thenReturn(0L);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> recordService.restore(RECORD_ID)).getCode());
        verify(recordMapper, never()).update(eq(null), any());
    }

    @Test
    @DisplayName("countByLabel 以 note_label 字典为基准统计各标签笔记数")
    void countByLabel_scopedToCurrentUser() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        LabelCountVO work = new LabelCountVO();
        work.setKey("work");
        work.setLabel("工作");
        work.setCount(3L);
        when(recordMapper.countByLabel(USER_ID, Constants.DICT_TYPE_NOTE_LABEL)).thenReturn(List.of(work));

        List<LabelCountVO> result = recordService.countByLabel();

        verify(recordMapper).countByLabel(USER_ID, Constants.DICT_TYPE_NOTE_LABEL);
        assertEquals(1, result.size());
        assertEquals("work", result.get(0).getKey());
        assertEquals("工作", result.get(0).getLabel());
        assertEquals(3L, result.get(0).getCount());
    }
}
