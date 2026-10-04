package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.DictItemVO;
import com.leejie.xtx.core.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 通用字典读接口。字典是系统级共享数据，任何已登录用户可读。 */
@Tag(name = "字典")
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/{typeCode}")
    @Operation(summary = "按类型码查询字典项(仅启用项)")
    public R<List<DictItemVO>> list(@PathVariable String typeCode) {
        return R.ok(dictService.listEnabledItems(typeCode).stream()
                .map(DictItemVO::fromEntity)
                .toList());
    }
}
