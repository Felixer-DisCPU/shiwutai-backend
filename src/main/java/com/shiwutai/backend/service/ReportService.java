package com.shiwutai.backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final AnalyzeService analyzeService;
    private final LlmService llm;

    public ReportService(AnalyzeService analyzeService, LlmService llm) {
        this.analyzeService = analyzeService;
        this.llm = llm;
    }

    public Map<String, Object> teaching(String openid, Long batchId) {
        Map<String, Object> overview = analyzeService.overview(openid, batchId);
        List<Map<String, Object>> students = analyzeService.students(openid, batchId);
        String summary = dataSummary(overview, students);
        String prompt = "你是中学教研助手。基于以下学情数据生成教学建议，结论必须可追溯到给定数字，禁止编造。\n"
                + summary + "\n输出两段：\n一、全班讲评建议：最该讲的3个知识点、每点典型错误、练习方向。"
                + "\n二、个体辅导：列出需重点关注的学生及各自补什么、可用哪道错题切入。";
        String text = llm.callText(prompt);
        Map<String, Object> r = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            r.put("text", "（AI 接口未配置，展示统计数据）\n" + summary);
            r.put("fallback", true);
        } else {
            r.put("text", text);
        }
        return r;
    }

    public Map<String, Object> report(String openid, Long batchId) {
        Map<String, Object> overview = analyzeService.overview(openid, batchId);
        List<Map<String, Object>> students = analyzeService.students(openid, batchId);
        String summary = dataSummary(overview, students);
        String prompt = "你是中学教研助手。基于以下学情数据生成四段式学情报告，结论必须可追溯到给定数字，禁止编造。\n"
                + summary + "\n四段：整体情况 / 薄弱知识点 / 需关注学生 / 后续建议。";
        String text = llm.callText(prompt);
        Map<String, Object> r = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            r.put("text", "（AI 接口未配置，展示统计数据）\n" + summary);
            r.put("fallback", true);
        } else {
            r.put("text", text);
        }
        return r;
    }

    private String dataSummary(Map<String, Object> overview, List<Map<String, Object>> students) {
        int classMastery = overview.get("classMastery") instanceof Number
                ? ((Number) overview.get("classMastery")).intValue() : 0;
        int concentration = overview.get("concentration") instanceof Number
                ? ((Number) overview.get("concentration")).intValue() : 0;
        int attention = overview.get("attention") instanceof Number
                ? ((Number) overview.get("attention")).intValue() : 0;
        List<Map<String, Object>> kpRank = (List<Map<String, Object>>) overview.get("kpRank");
        List<Map<String, Object>> errDist = (List<Map<String, Object>>) overview.get("errDist");
        String top3 = kpRank == null ? "" : kpRank.stream().limit(3)
                .map(k -> k.get("name") + "(" + k.get("rate") + "%)").collect(Collectors.joining("、"));
        String errStr = errDist == null ? "" : errDist.stream()
                .map(e -> e.get("name") + "" + e.get("rate") + "%").collect(Collectors.joining("、"));
        String focusNames = students == null ? "" : students.stream()
                .filter(s -> "优先".equals(s.get("focus")))
                .map(s -> (String) s.get("name")).collect(Collectors.joining("、"));
        return "班级掌握度 " + classMastery + "%；错误集中度 " + concentration + "%；需关注 " + attention + " 人。\n"
                + "薄弱知识点 Top3：" + top3 + "。\n"
                + "错误类型：" + errStr + "。\n"
                + "需重点辅导学生：" + (focusNames.isEmpty() ? "无" : focusNames) + "。";
    }
}
