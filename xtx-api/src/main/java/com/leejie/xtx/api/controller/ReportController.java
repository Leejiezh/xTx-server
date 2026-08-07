package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.ReportCreateReq;
import com.leejie.xtx.core.dto.ReportUpdateReq;
import com.leejie.xtx.core.dto.ReportVO;
import com.leejie.xtx.core.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "报告表管理")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "创建报告表")
    public R<Long> create(@Valid @RequestBody ReportCreateReq req) {
        return R.ok(reportService.create(req.toEntity()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询报告表详情")
    public R<ReportVO> get(@PathVariable Long id) {
        return R.ok(ReportVO.fromEntity(reportService.get(id)));
    }

    @PutMapping
    @Operation(summary = "更新报告表")
    public R<Void> update(@Valid @RequestBody ReportUpdateReq req) {
        reportService.update(req.toEntity());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除报告表")
    public R<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询报告表")
    public R<PageResult<ReportVO>> page(PageQuery query) {
        return R.ok(PageResult.of(reportService.page(query, null), ReportVO::fromEntity));
    }
}