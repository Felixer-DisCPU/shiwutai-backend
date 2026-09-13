package com.shiwutai.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiwutai.backend.model.Correction;
import com.shiwutai.backend.repository.CorrectionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉 / 文本大模型客户端（OpenAI 兼容 /v1/chat/completions）。
 * 支持图片（视觉）与文本模型使用不同的 API Key 和 Base URL：
 *   - 视觉：LLM_API_KEY / LLM_BASE_URL / LLM_MODEL
 *   - 文本：LLM_TEXT_API_KEY / LLM_TEXT_BASE_URL / LLM_TEXT_MODEL
 * 文本未单独配置时自动回落到视觉配置，便于普通用户只用一组 Key。
 */
@Service
public class LlmService {

    // 视觉模型默认（亦是文本未独立配置时的回落值）
    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.model:gpt-4o}")
    private String visionModel;

    // 文本模型默认
    @Value("${llm.text-api-key:}")
    private String textApiKey;

    @Value("${llm.text-base-url:}")
    private String textBaseUrl;

    @Value("${llm.text-model:gpt-4o-mini}")
    private String textModel;

    private final ConfigService configService;
    private final CorrectionRepository correctionRepo;
    private final ObjectMapper om = new ObjectMapper();
    private HttpClient http;

    public LlmService(ConfigService configService, CorrectionRepository correctionRepo) {
        this.configService = configService;
        this.correctionRepo = correctionRepo;
    }

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** 运行时配置优先，环境变量兜底。 */
    private String envOrConfig(String key, String envVal) {
        String c = configService.get(key);
        return (c != null && !c.isBlank()) ? c : envVal;
    }

    private String visionKey() { return envOrConfig("LLM_API_KEY", apiKey); }
    private String visionBaseUrl() { return envOrConfig("LLM_BASE_URL", baseUrl); }
    private String visionModel() { return envOrConfig("LLM_MODEL", visionModel); }

    private String textKey() {
        String k = envOrConfig("LLM_TEXT_API_KEY", textApiKey);
        return (k != null && !k.isBlank()) ? k : visionKey();
    }
    private String textBaseUrl() {
        String u = envOrConfig("LLM_TEXT_BASE_URL", textBaseUrl);
        return (u != null && !u.isBlank()) ? u : visionBaseUrl();
    }
    private String textModel() { return envOrConfig("LLM_TEXT_MODEL", textModel); }

    /** 视觉模型名，供 RecognizeService 等日志使用。 */
    public String visionModelName() {
        return visionModel();
    }

    /** 文本补全，未配置 key 或失败返回 null（由调用方降级）。 */
    public String callText(String prompt) {
        String k = textKey();
        if (k == null || k.isBlank()) return null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", textModel());
            List<Map<String, Object>> msgs = new ArrayList<>();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", "user");
            m.put("content", prompt);
            msgs.add(m);
            body.put("messages", msgs);
            body.put("temperature", 0.5);
            body.put("max_tokens", 1500);
            JsonNode root = post(body, k, textBaseUrl());
            return root.path("choices").path(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 视觉识别：传入图片字节与知识点候选范围，返回模型原始文本。失败抛异常。 */
    public String callVision(String openid, List<byte[]> images, String scopeText) throws Exception {
        String k = visionKey();
        if (k == null || k.isBlank()) throw new IllegalStateException("LLM_API_KEY 未配置（请在环境变量或前端「AI 配置」中设置）");
        String prompt = "你是一名助教，负责把教师已批改的作业影像结构化。\n规则：\n"
                + "1. 仅根据图中教师的批改痕迹（对勾√、叉号×、扣分、批注）判断每题正误，不要自行评判答案对错。\n"
                + "2. 对每题输出：题号 qno、学生作答文本 answerText（保留公式与步骤）、正误 result（取值：正确/错误/部分正确/未作答）、知识点 points（从给定候选集中选 Top-3 数组）、错误类型 errorType（仅限四类：概念性错误/运算推导失误/审题与理解偏差/表达不规范，若 result 为正确则为空串）、置信度 confidence（0-1 小数）。\n"
                + "3. 只输出一个 JSON 数组，不要任何解释或 markdown 围栏。\n"
                + "知识点候选集：" + (scopeText == null || scopeText.isBlank() ? "（无，由模型自行判断）" : scopeText) + "\n"
                + "示例：[{\"qno\":\"第7题\",\"answerText\":\"y=2(x-1)^2-1\",\"result\":\"错误\",\"points\":[\"二次函数顶点式\"],\"errorType\":\"概念性错误\",\"confidence\":0.82}]";

        Map<String, Object> userContent = new LinkedHashMap<>();
        userContent.put("type", "text");
        userContent.put("text", prompt);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(userContent);
        for (byte[] img : images) {
            String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(img);
            Map<String, Object> imgContent = new LinkedHashMap<>();
            imgContent.put("type", "image_url");
            Map<String, Object> imgUrl = new LinkedHashMap<>();
            imgUrl.put("url", dataUrl);
            imgContent.put("image_url", imgUrl);
            content.add(imgContent);
        }

        List<Map<String, Object>> msgs = new ArrayList<>();
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", "你是结构化助手，只输出 JSON 数组。" + fewShotExamples(openid));
        Map<String, Object> usr = new LinkedHashMap<>();
        usr.put("role", "user");
        usr.put("content", content);
        msgs.add(sys);
        msgs.add(usr);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel());
        body.put("messages", msgs);
        body.put("temperature", 0.2);
        body.put("max_tokens", 3000);

        JsonNode root = post(body, k, visionBaseUrl());
        return root.path("choices").path(0).path("message").path("content").asText(null);
    }

    /**
     * 根据清单名称 + 录入的作业内容（学生作答文本），用文本模型归纳本次作业实际考查的知识点清单。
     * 返回去重后的字符串数组（最多 12 个）；模型不可用或解析失败时返回空列表。
     */
    public List<String> callScope(String batchName, String contentSample) {
        String k = textKey();
        if (k == null || k.isBlank()) k = visionKey();
        if (k == null || k.isBlank()) return new ArrayList<>();
        try {
            String prompt = "你是中学教研助手。下面是一份作业的标题与学生作答片段。请归纳本次作业实际考查的知识点清单。\n"
                    + "要求：简明、去重、每点 2-12 个汉字，覆盖主要考点，最多 12 个。\n"
                    + "只输出一个 JSON 字符串数组，不要任何解释或 markdown 围栏。\n"
                    + "示例：[\"二次函数顶点式\",\"配方法\",\"判别式与根\"]";
            StringBuilder userText = new StringBuilder();
            if (batchName != null && !batchName.isBlank()) userText.append("作业标题：").append(batchName).append("\n");
            userText.append("学生作答片段：\n").append(contentSample == null ? "" : contentSample);

            List<Map<String, Object>> msgs = new ArrayList<>();
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", "你是教研助手，只输出 JSON 数组。");
            Map<String, Object> usr = new LinkedHashMap<>();
            usr.put("role", "user");
            usr.put("content", userText.toString());
            msgs.add(sys);
            msgs.add(usr);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", textModel());
            body.put("messages", msgs);
            body.put("temperature", 0.3);
            body.put("max_tokens", 800);

            JsonNode root = post(body, k, textBaseUrl());
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null) return new ArrayList<>();
            int i = content.indexOf('[');
            int j = content.lastIndexOf(']');
            if (i < 0 || j < 0 || j <= i) return new ArrayList<>();
            JsonNode arr = om.readTree(content.substring(i, j + 1));
            if (!arr.isArray()) return new ArrayList<>();
            List<String> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String s = n.asText().trim();
                if (!s.isEmpty() && !out.contains(s) && out.size() < 12) out.add(s);
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 校正结果回流为样本：把该 openid 近期教师改判拼接成少样本示例，注入系统提示。
     * 仅在 RECOGNIZE_FEEDBACK 开启且有改判记录时返回非空串。
     */
    private String fewShotExamples(String openid) {
        if (openid == null || openid.isBlank()) return "";
        if (!configService.flag("RECOGNIZE_FEEDBACK")) return "";
        List<Correction> cs = correctionRepo.findByOpenidOrderByCreatedAtDesc(openid);
        if (cs == null || cs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Correction c : cs) {
            if (n >= 6) break;
            String before = c.getBeforeVal(), after = c.getAfterVal();
            if (before == null || after == null || before.equals(after)) continue;
            n++;
            sb.append("\n").append(n).append(". 字段「").append(c.getField()).append("」：原识别 ")
              .append(before)
              .append("；教师校正为 ").append(after).append("。");
        }
        if (n == 0) return "";
        return "\n\n教师改判示例（仅作风格与口径参考，不要机械套用）：" + sb;
    }

    private JsonNode post(Map<String, Object> body, String key, String baseUrl) throws Exception {
        String json = om.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ensureEndsWithSlash(baseUrl) + "chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .timeout(Duration.ofSeconds(configService.timeoutSec()))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("LLM http " + resp.statusCode());
        }
        return om.readTree(resp.body());
    }

    private static String ensureEndsWithSlash(String s) {
        if (s == null || s.isBlank()) return "https://api.openai.com/v1/";
        return s.endsWith("/") ? s : s + "/";
    }
}
