package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Config;
import com.shiwutai.backend.repository.ConfigRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局键值配置服务。前端"AI 配置"页与 OOBE 通过 /api/config 写入，LlmService 读取。
 * 不区分 openid：同一后端实例共享一份配置。
 */
@Service
public class ConfigService {

    private final ConfigRepository repo;

    public ConfigService(ConfigRepository repo) {
        this.repo = repo;
    }

    /** 读取某项配置，未设置返回 null。 */
    public String get(String k) {
        return repo.findByK(k).map(Config::getV).orElse(null);
    }

    /** 读取全部配置为扁平 Map。 */
    public Map<String, String> getAll() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Config c : repo.findAll()) {
            m.put(c.getK(), c.getV());
        }
        return m;
    }

    /** 写入/更新单项。 */
    public void set(String k, String v) {
        if (k == null || k.isBlank()) return;
        Config c = repo.findByK(k).orElseGet(() -> {
            Config n = new Config();
            n.setK(k);
            return n;
        });
        c.setV(v);
        repo.save(c);
    }

    /** 批量写入。 */
    public void setAll(Map<String, String> m) {
        if (m == null) return;
        m.forEach(this::set);
    }

    /** 常见 LLM 配置键，便于前端/OOBE 枚举。 */
    public static List<String> llmKeys() {
        return List.of("LLM_API_KEY", "LLM_BASE_URL", "LLM_MODEL",
                "LLM_TEXT_API_KEY", "LLM_TEXT_BASE_URL", "LLM_TEXT_MODEL");
    }

    /* ---------- 识别与 AI 相关配置（前端「识别与 AI」组写入，后端消费） ---------- */

    /** 低置信度阈值，默认 0.6。前端写入字符串如 "0.70"。 */
    public double confThreshold() {
        String v = get("RECOGNIZE_CONF_THRESHOLD");
        if (v == null || v.isBlank()) return 0.6;
        try { return Double.parseDouble(v); } catch (Exception e) { return 0.6; }
    }

    /** 单份识别 HTTP 超时（秒），默认 60。前端写入字符串如 "60"。 */
    public int timeoutSec() {
        String v = get("RECOGNIZE_TIMEOUT_SEC");
        if (v == null || v.isBlank()) return 60;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return 60; }
    }

    /** 布尔型开关：配置值等于 true（忽略大小写）时为真。 */
    public boolean flag(String k) {
        return "true".equalsIgnoreCase(get(k));
    }
}
