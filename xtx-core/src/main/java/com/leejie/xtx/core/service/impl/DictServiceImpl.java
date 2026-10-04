package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leejie.xtx.core.entity.DictItem;
import com.leejie.xtx.core.entity.DictType;
import com.leejie.xtx.core.mapper.DictItemMapper;
import com.leejie.xtx.core.mapper.DictTypeMapper;
import com.leejie.xtx.core.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final int ENABLED = 1;

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    public List<DictItem> listEnabledItems(String typeCode) {
        DictType type = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictType>().eq(DictType::getTypeCode, typeCode));
        // 类型不存在或整本停用，都不该把项暴露出去
        if (type == null || !Integer.valueOf(ENABLED).equals(type.getEnabled())) {
            return List.of();
        }
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictType, typeCode)
                .eq(DictItem::getEnabled, ENABLED)
                .orderByAsc(DictItem::getSortOrder)
                .orderByAsc(DictItem::getId));
    }
}
