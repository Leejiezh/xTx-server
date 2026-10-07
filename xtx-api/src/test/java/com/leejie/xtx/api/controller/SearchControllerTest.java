package com.leejie.xtx.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.SearchQuery;
import com.leejie.xtx.core.dto.SearchVO;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.service.RecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchControllerTest {

    @Test
    @DisplayName("把记录转成 SearchVO：命中窗口摘要 + 服务端高亮，不含 content/images")
    void search_mapsRecordToVo() {
        RecordService recordService = mock(RecordService.class);
        Record r = new Record();
        r.setId(100L);
        r.setTitle("MySQL 笔记");
        r.setLabel("work");
        r.setContent("MySQL 8.0 使用 ngram 分词器处理中文搜索，这是一段足够长的正文用来测试摘要截取，"
                + "确保命中位置居中而不是固定在开头。");
        r.setRecordDate(LocalDate.of(2026, 10, 6));
        r.setCreatedAt(LocalDateTime.of(2026, 10, 6, 10, 0));
        r.setUpdatedAt(LocalDateTime.of(2026, 10, 6, 11, 0));

        Page<Record> page = new Page<>(1, 10);
        page.setRecords(List.of(r));
        page.setTotal(1);

        SearchQuery query = new SearchQuery();
        query.setQ("mysql");
        when(recordService.search(query)).thenReturn(page);

        R<PageResult<SearchVO>> rsp = new SearchController(recordService).search(query);

        assertEquals(200, rsp.getCode());
        SearchVO vo = rsp.getData().getList().getFirst();
        assertEquals(100L, vo.getId());
        assertEquals("MySQL 笔记", vo.getTitle());
        assertEquals("work", vo.getLabel());
        assertEquals(LocalDate.of(2026, 10, 6), vo.getRecordDate());
        assertTrue(vo.getExcerpt().contains("MySQL"), "摘要应包含命中词");
        assertEquals("<span class=\"hl\">MySQL</span> 笔记", vo.getHighlights().getTitle());
        assertTrue(vo.getHighlights().getExcerpt().contains("MySQL"), "摘要高亮应包含命中词");
    }
}
