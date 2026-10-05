package com.leejie.xtx.api.controller;

import cn.hutool.core.util.StrUtil;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.RecordCreateReq;
import com.leejie.xtx.core.dto.RecordQuery;
import com.leejie.xtx.core.dto.RecordUpdateReq;
import com.leejie.xtx.core.dto.RecordVO;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.service.FileService;
import com.leejie.xtx.core.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 记录表接口。
 *
 * <p>出站前必须把 images 里的 objectKey 换成预签名 access URL：库里存的是永久
 * objectKey，桶是私有的，直接返给小程序取不到图（见 ADR-0002）。
 */
@Tag(name = "记录表管理")
@RestController
@RequestMapping("/record")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;
    private final FileService fileService;


    @GetMapping("/page")
    @Operation(summary = "分页查询记录表")
    public R<PageResult<RecordVO>> page(@Valid RecordQuery query) {
        // 无筛选必须传 null：空 lambda 会被 applyFilters 嵌套成 AND ()，SQL 直接报错
        String label = query.getLabel();
        return R.ok(PageResult.of(recordService.page(query,
                StrUtil.isBlank(label) ? null : q -> q.eq("label", label)), this::toVO));
    }

    @PostMapping("/create")
    @Operation(summary = "创建记录表")
    public R<Long> create(@Valid @RequestBody RecordCreateReq req) {
        return R.ok(recordService.create(req.toEntity()));
    }

    @GetMapping("/getDetail/{id}")
    @Operation(summary = "查询记录表详情")
    public R<RecordVO> get(@PathVariable Long id) {
        return R.ok(toVO(recordService.get(id)));
    }

    @PutMapping("/update")
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


    private RecordVO toVO(Record entity) {
        RecordVO vo = RecordVO.fromEntity(entity);
        vo.setImages(fileService.accessUrls(vo.getImages()));
        return vo;
    }
}