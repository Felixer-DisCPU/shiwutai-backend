package com.shiwutai.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiwutai.backend.service.InitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序登录：用 wx.login 拿到的 code 换取 openid（F8 数据隔离依据）。
 * 未配置 WX_APPID/WX_SECRET 时返回固定 demo_openid，便于本地联调。
 */
@RestController
public class AuthController {

    @Value("${app.wx.appid:}")
    private String appid;

    @Value("${app.wx.secret:}")
    private String secret;

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final InitService initService;

    public AuthController(InitService initService) {
        this.initService = initService;
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String code = body == null ? null : (String) body.get("code");
        String openid;
        if (appid != null && !appid.isBlank() && secret != null && !secret.isBlank() && code != null) {
            openid = exchange(code);
        } else {
            openid = "demo_openid";
        }
        // 登录后自动初始化该用户知识点体系（幂等，已有则不覆盖）
        initService.run(openid);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("openid", openid);
        return r;
    }

    private String exchange(String code) {
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid="
                    + URLEncoder.encode(appid, StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                    + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = om.readTree(resp.body());
            JsonNode o = node.get("openid");
            return o != null && !o.asText().isEmpty() ? o.asText() : "demo_openid";
        } catch (Exception e) {
            return "demo_openid";
        }
    }
}
