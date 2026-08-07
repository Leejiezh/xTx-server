package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.service.impl.OwnedServiceImpl;
import com.leejie.xtx.core.entity.Report;
import com.leejie.xtx.core.mapper.ReportMapper;
import com.leejie.xtx.core.service.ReportService;
import org.springframework.stereotype.Service;

/**
 * 报告表 服务实现
 */
@Service
public class ReportServiceImpl extends OwnedServiceImpl<ReportMapper, Report> implements ReportService {
}