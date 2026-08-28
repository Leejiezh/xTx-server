package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import com.leejie.xtx.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 调试用 token 签发端点，仅在 dev profile 下注册。
 *
 * <p>微信登录（jscode2session → 建/查 user → 签 token）尚未实施，但认证链路已生效，
 * 所有业务接口都需要 token，本地 Postman/curl 自测就没法进行。用 {@code @Profile("dev")}
 * 而非注释警告来隔离：生产不激活 dev profile，这个 bean 根本不会存在，
 * 不存在「上线忘了删」这种失效模式。
 *
 * <p>微信登录落地后本类应连同 dev profile 的用途一起重新评估。
 */
@Tag(name = "开发调试")
@Profile("dev")
@RestController
@RequestMapping("/auth")
public class DevTokenController {

    @GetMapping("/dev-token")
    @Operation(summary = "签发调试用 token（仅 dev profile）")
    public R<String> devToken(@RequestParam(defaultValue = "1") Long userId) {
        // claim key 统一收在 Constants，签发与解析两侧必须一致
        return R.ok(JwtUtils.generate(Map.of(
                Constants.CLAIM_USER_ID, userId,
                Constants.CLAIM_OPENID, "dev-openid-" + userId
        )));
    }
}
