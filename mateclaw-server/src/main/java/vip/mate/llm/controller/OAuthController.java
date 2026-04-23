package vip.mate.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.llm.oauth.OpenAIOAuthService;
import vip.mate.llm.oauth.OpenAIOAuthService.OAuthAuthorizeResult;
import vip.mate.llm.oauth.OpenAIOAuthService.OAuthStatusResult;

@Tag(name = "OpenAI OAuth")
@RestController
@RequestMapping("/api/v1/oauth/openai")
@RequiredArgsConstructor
public class OAuthController {

    private final OpenAIOAuthService oauthService;

    @Operation(summary = "获取 OAuth 授权 URL（同时启动本地回调服务器）")
    @GetMapping("/authorize")
    public R<OAuthAuthorizeResult> authorize() {
        return R.ok(oauthService.buildAuthorizeUrl());
    }

    @Operation(summary = "手动刷新 Token")
    @PostMapping("/refresh")
    public R<Void> refresh() {
        oauthService.refreshToken();
        return R.ok();
    }

    @Operation(summary = "清除 OAuth 凭证")
    @DeleteMapping("/revoke")
    public R<Void> revoke() {
        oauthService.revokeToken();
        return R.ok();
    }

    @Operation(summary = "获取 OAuth 连接状态")
    @GetMapping("/status")
    public R<OAuthStatusResult> status() {
        return R.ok(oauthService.getStatus());
    }
}
