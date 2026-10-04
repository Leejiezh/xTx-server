package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.UserProfileReq;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口。
 *
 * <p>挂在 /user/** 下走默认鉴权：更新/读取都以 JWT 里的 userId 定位本用户，
 * 不需要（也没有）路径参数指定操作谁。
 */
@Tag(name = "用户资料")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "查询当前用户资料")
    public R<UserVO> getProfile() {
        return R.ok(userService.getProfile());
    }

    @PutMapping("/profile")
    @Operation(summary = "更新当前用户资料")
    public R<Void> updateProfile(@Valid @RequestBody UserProfileReq req) {
        userService.updateProfile(req);
        return R.ok();
    }
}
