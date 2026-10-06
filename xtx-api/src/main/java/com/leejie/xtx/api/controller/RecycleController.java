package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.RecordVO;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.service.FileService;
import com.leejie.xtx.core.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理：彻底删除 / 恢复 / 列表等操作统一收敛在这里。
 *
 * <p>普通删除（移入回收站）仍是 {@code DELETE /record/{id}}；本控制器只处理
 * 回收站内部动作：列表、恢复、彻底删除。
 */
@Tag(name = "回收站管理")
@RestController
@RequestMapping("/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecordService recordService;
    private final FileService fileService;

    @GetMapping("/page")
    @Operation(summary = "分页查询回收站记录")
    public R<PageResult<RecordVO>> page(@Valid PageQuery query) {
        return R.ok(PageResult.of(recordService.recyclePage(query), this::toVO));
    }

    @PostMapping("/restore/{id}")
    @Operation(summary = "从回收站恢复记录（图片随记录还原，无需额外处理）")
    public R<Void> restore(@PathVariable Long id) {
        recordService.restore(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "彻底删除回收站记录（图片进入 24h 宽限期后清理）")
    public R<Void> purge(@PathVariable Long id) {
        recordService.purge(id);
        return R.ok();
    }

    /** 与 RecordController 同源：出站前把 images 的 objectKey 换成预签名 access URL */
    private RecordVO toVO(Record entity) {
        RecordVO vo = RecordVO.fromEntity(entity);
        vo.setImages(fileService.accessUrls(vo.getImages()));
        return vo;
    }
}
