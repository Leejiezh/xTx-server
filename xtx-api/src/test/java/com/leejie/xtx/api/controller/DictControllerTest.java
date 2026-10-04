package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.DictItemVO;
import com.leejie.xtx.core.entity.DictItem;
import com.leejie.xtx.core.service.DictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictControllerTest {

    @Test
    @DisplayName("把字典实体映射成 {key,label,sortOrder,extra} 并包进 R")
    void list_mapsEntityToVo() {
        DictService dictService = mock(DictService.class);
        DictItem work = new DictItem();
        work.setItemKey("work");
        work.setItemLabel("工作");
        work.setSortOrder(10);
        work.setExtra(Map.of("color", "#7C3AED"));
        when(dictService.listEnabledItems("note_label")).thenReturn(List.of(work));

        R<List<DictItemVO>> r = new DictController(dictService).list("note_label");

        assertEquals(200, r.getCode());
        DictItemVO vo = r.getData().getFirst();
        assertEquals("work", vo.getKey());
        assertEquals("工作", vo.getLabel());
        assertEquals(10, vo.getSortOrder());
        assertEquals("#7C3AED", vo.getExtra().get("color"));
    }
}
