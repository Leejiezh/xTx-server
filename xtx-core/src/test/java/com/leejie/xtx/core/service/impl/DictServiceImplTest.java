package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leejie.xtx.core.entity.DictItem;
import com.leejie.xtx.core.entity.DictType;
import com.leejie.xtx.core.mapper.DictItemMapper;
import com.leejie.xtx.core.mapper.DictTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictServiceImplTest {

    @Mock
    private DictTypeMapper dictTypeMapper;
    @Mock
    private DictItemMapper dictItemMapper;

    private DictServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DictServiceImpl(dictTypeMapper, dictItemMapper);
        // 手动注册 TableInfo，让 LambdaQueryWrapper 的方法引用能解析成列名（等价于 Spring 启动时的 mapper 扫描）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DictItem.class);
    }

    private static DictType type(int enabled) {
        DictType t = new DictType();
        t.setTypeCode("note_label");
        t.setEnabled(enabled);
        return t;
    }

    @Test
    @DisplayName("类型不存在 → 空列表，且不查 item")
    void unknownType_returnsEmpty() {
        when(dictTypeMapper.selectOne(any())).thenReturn(null);

        assertThat(service.listEnabledItems("nope")).isEmpty();
        verify(dictItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("类型整本停用 → 空列表，且不查 item")
    void disabledType_returnsEmpty() {
        when(dictTypeMapper.selectOne(any())).thenReturn(type(0));

        assertThat(service.listEnabledItems("note_label")).isEmpty();
        verify(dictItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("类型启用 → 返回 item，查询带 enabled 过滤与 sort_order 排序")
    void enabledType_returnsItemsWithFilterAndOrder() {
        when(dictTypeMapper.selectOne(any())).thenReturn(type(1));
        DictItem work = new DictItem();
        work.setItemKey("work");
        when(dictItemMapper.selectList(any())).thenReturn(List.of(work));

        assertThat(service.listEnabledItems("note_label")).containsExactly(work);

        ArgumentCaptor<LambdaQueryWrapper<DictItem>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(dictItemMapper).selectList(cap.capture());
        LambdaQueryWrapper<DictItem> wrapper = cap.getValue();
        assertThat(wrapper.getSqlSegment()).contains("enabled");
        MergeSegments expr = wrapper.getExpression();
        // getOrderBy() 返回的是 ISqlSegment lambda，toString() 不含 SQL，必须逐段取 getSqlSegment()
        List<ISqlSegment> orderBy = expr.getOrderBy();
        assertThat(orderBy).hasSize(2);
        assertThat(orderBy.getFirst().getSqlSegment()).contains("sort_order");
        assertThat(orderBy.getLast().getSqlSegment()).contains("id");
    }
}
