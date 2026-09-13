package com.shiwutai.backend.controller;

import com.shiwutai.backend.service.*;
import com.shiwutai.backend.util.Req;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一资源接口：/api/<资源> 接收 { action, openid, ...params } 信封，按 action 分发到对应 Service。
 * 与小程序端 utils/cloud.js 的请求结构一一对应。
 */
@RestController
public class ApiController {

    private final BatchService batchService;
    private final SheetService sheetService;
   private final RecognizeService recognizeService;
    private final ReviewService reviewService;
    private final AnalyzeService analyzeService;
    private final ReportService reportService;
    private final ExportService exportService;
    private final KnowledgeService knowledgeService;
    private final InitService initService;
    private final ProfileService profileService;
    private final SeedService seedService;
    private final ConfigService configService;

    public ApiController(BatchService batchService, SheetService sheetService,
                         RecognizeService recognizeService, ReviewService reviewService,
                         AnalyzeService analyzeService, ReportService reportService,
                         ExportService exportService, KnowledgeService knowledgeService,
                         InitService initService, ProfileService profileService,
                         SeedService seedService, ConfigService configService) {
        this.batchService = batchService;
        this.sheetService = sheetService;
        this.recognizeService = recognizeService;
        this.reviewService = reviewService;
        this.analyzeService = analyzeService;
        this.reportService = reportService;
        this.exportService = exportService;
        this.knowledgeService = knowledgeService;
        this.initService = initService;
        this.profileService = profileService;
        this.seedService = seedService;
        this.configService = configService;
    }

    private static Map<String, Object> unknown(String a) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("error", "unknown action: " + a);
        return r;
    }

    @PostMapping("/api/batch")
    public Object batch(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("create".equals(action)) {
            Object b = body.get("batch");
            return batchService.create(openid, b instanceof Map ? (Map<String, Object>) b : body);
        }
        if ("list".equals(action)) return batchService.list(openid);
        if ("detail".equals(action)) return batchService.detail(openid, Req.toLong(body, "batchId"));
        if ("remove".equals(action)) return batchService.remove(openid, Req.toLong(body, "batchId"));
        if ("setStatus".equals(action)) return batchService.setStatus(openid, Req.toLong(body, "batchId"), Req.str(body, "status"));
        if ("setTeaching".equals(action)) return batchService.setTeaching(openid, Req.toLong(body, "batchId"), Req.strList(body, "points"));
        if ("overview".equals(action)) return batchService.overview(openid);
        return unknown(action);
    }

    @PostMapping("/api/sheet")
    public Map<String, Object> sheet(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("add".equals(action)) return sheetService.add(openid, Req.toLong(body, "batchId"), Req.str(body, "studentName"), Req.strList(body, "images"));
        if ("list".equals(action)) return Map.of("items", sheetService.list(openid, Req.toLong(body, "batchId")));
        if ("retake".equals(action)) return sheetService.retake(openid, Req.toLong(body, "sheetId"), Req.strList(body, "images"));
        return unknown(action);
    }

    @PostMapping("/api/recognize")
    public Map<String, Object> recognize(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("trigger".equals(action)) return recognizeService.trigger(openid, Req.toLong(body, "batchId"), Req.toLong(body, "sheetId"));
        return unknown(action);
    }

    @PostMapping("/api/review")
    public Map<String, Object> review(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("list".equals(action)) return reviewService.list(openid, Req.toLong(body, "batchId"));
        if ("correct".equals(action)) {
            Object p = body.get("patch");
            return reviewService.correct(openid, Req.toLong(body, "itemId"), p instanceof Map ? (Map<String, Object>) p : Map.of());
        }
        if ("batchCorrect".equals(action)) {
            Object p = body.get("patch");
            return reviewService.batchCorrect(openid, Req.toLong(body, "batchId"), Req.str(body, "pointId"), p instanceof Map ? (Map<String, Object>) p : Map.of());
        }
        if ("reRecognize".equals(action)) return reviewService.reRecognize(openid, Req.toLong(body, "sheetId"));
        if ("confirm".equals(action)) return reviewService.confirm(openid, Req.toLong(body, "batchId"));
        return unknown(action);
    }

    @PostMapping("/api/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("overview".equals(action)) return analyzeService.overview(openid, Req.toLong(body, "batchId"));
        if ("students".equals(action)) return Map.of("students", analyzeService.students(openid, Req.toLong(body, "batchId")));
        if ("studentDetail".equals(action)) return analyzeService.studentDetail(openid, Req.toLong(body, "batchId"), Req.str(body, "name"));
        if ("trend".equals(action)) return Map.of("series", analyzeService.trend(openid, Req.str(body, "pointId")));
        if ("effectiveness".equals(action)) return analyzeService.effectiveness(openid, Req.str(body, "pointId"));
        if ("attention".equals(action)) return Map.of("attention", analyzeService.attention(openid, Req.toLong(body, "batchId")));
        return unknown(action);
    }

    @PostMapping("/api/report")
    public Map<String, Object> report(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("teaching".equals(action)) return reportService.teaching(openid, Req.toLong(body, "batchId"));
        if ("report".equals(action)) return reportService.report(openid, Req.toLong(body, "batchId"));
        return unknown(action);
    }

    @PostMapping("/api/export")
    public Map<String, Object> export(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("csv".equals(action)) return exportService.csv(openid, Req.toLong(body, "batchId"));
        if ("text".equals(action)) return exportService.text(openid, Req.toLong(body, "batchId"), Req.str(body, "type"));
        return unknown(action);
    }

    @PostMapping("/api/knowledge")
    public Map<String, Object> knowledge(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("list".equals(action)) return Map.of("items", knowledgeService.list(openid, Req.str(body, "subject")));
        if ("add".equals(action)) {
            Object kp = body.get("kp");
            return knowledgeService.add(openid, kp instanceof Map ? (Map<String, Object>) kp : Map.of());
        }
        if ("update".equals(action)) {
            Object p = body.get("patch");
            return knowledgeService.update(openid, Req.toLong(body, "id"), p instanceof Map ? (Map<String, Object>) p : Map.of());
        }
        if ("remove".equals(action)) return knowledgeService.remove(openid, Req.toLong(body, "id"));
        return unknown(action);
    }

    @PostMapping("/api/init")
    public Map<String, Object> init(@RequestBody Map<String, Object> body) {
        return initService.run(Req.str(body, "openid"));
    }

    @PostMapping("/api/profile")
    public Map<String, Object> profile(@RequestBody Map<String, Object> body) {
        String openid = Req.str(body, "openid");
        String action = Req.str(body, "action");
        if ("getProfile".equals(action)) return wrap(profileService.getProfile(openid));
        if ("saveProfile".equals(action)) {
            Object p = body.get("profile");
            profileService.saveProfile(openid, p instanceof Map ? (Map<String, Object>) p : Map.of());
            return Map.of("ok", true);
        }
        return unknown(action);
    }

    @PostMapping("/api/seed")
    public Map<String, Object> seed(@RequestBody Map<String, Object> body) {
        return seedService.run(Req.str(body, "openid"), Req.toLong(body, "batchId"));
    }

    @PostMapping("/api/config")
    public Map<String, Object> config(@RequestBody Map<String, Object> body) {
        String action = Req.str(body, "action");
        if ("get".equals(action)) {
            return Map.of("config", configService.getAll());
        }
        if ("set".equals(action)) {
            String k = Req.str(body, "k");
            String v = Req.str(body, "v");
            configService.set(k, v);
            return Map.of("ok", true);
        }
        if ("setAll".equals(action)) {
            Object c = body.get("config");
            if (c instanceof Map) {
                Map<String, String> m = new LinkedHashMap<>();
                ((Map<?, ?>) c).forEach((kk, vv) -> m.put(String.valueOf(kk), vv == null ? "" : String.valueOf(vv)));
                configService.setAll(m);
            }
            return Map.of("ok", true);
        }
        return unknown(action);
    }

    private static Map<String, Object> wrap(Object o) {
        // getProfile 可能返回 null（无档案）
        if (o == null) return new LinkedHashMap<>();
        return (Map<String, Object>) o;
    }
}
