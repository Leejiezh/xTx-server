package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.service.impl.OwnedServiceImpl;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.mapper.RecordMapper;
import com.leejie.xtx.core.service.RecordService;
import org.springframework.stereotype.Service;

/**
 * 记录表 服务实现
 */
@Service
public class RecordServiceImpl extends OwnedServiceImpl<RecordMapper, Record> implements RecordService {
}