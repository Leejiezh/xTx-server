package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public R<String> health() {
        return R.ok("小程序 API 服务运行正常");
    }
}