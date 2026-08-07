package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.RecordCreateReq;
import com.leejie.xtx.core.dto.RecordUpdateReq;
import com.leejie.xtx.core.dto.RecordVO;
import com.leejie.xtx.core.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "记录表管理")
@RestController
@RequestMapping("/record")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @PostMapping
    @Operation(summary = "创建记录表")
    public R<Long> create(@Valid @RequestBody RecordCreateReq req) {
        return R.ok(recordService.create(req.toEntity()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询记录表详情")
    public R<RecordVO> get(@PathVariable Long id) {
        return R.ok(RecordVO.fromEntity(recordService.get(id)));
    }

    @PutMapping
    @Operation(summary = "更新记录表")
    public R<Void> update(@Valid @RequestBody RecordUpdateReq req) {
        recordService.update(req.toEntity());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除记录表")
    public R<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询记录表")
    public R<PageResult<RecordVO>> page(PageQuery query) {
        return R.ok(PageResult.of(recordService.page(query, null), RecordVO::fromEntity));
    }
}