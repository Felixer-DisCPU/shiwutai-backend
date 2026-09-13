package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Batch;
import com.shiwutai.backend.model.PointStat;
import com.shiwutai.backend.model.Sheet;
import com.shiwutai.backend.repository.BatchRepository;
import com.shiwutai.backend.repository.CorrectionRepository;
import com.shiwutai.backend.repository.ItemRepository;
import com.shiwutai.backend.repository.PointStatRepository;
import com.shiwutai.backend.repository.SheetRepository;
import com.shiwutai.backend.util.Analytics;
import com.shiwutai.backend.util.Req;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BatchService {

    private final BatchRepository batchRepo;
    private final SheetRepository sheetRepo;
    private final ItemRepository itemRepo;
    private final PointStatRepository pointStatRepo;
    private final CorrectionRepository correctionRepo;

    public BatchService(BatchRepository batchRepo, SheetRepository sheetRepo,
                        ItemRepository itemRepo, PointStatRepository pointStatRepo,
                        CorrectionRepository correctionRepo) {
        this.batchRepo = batchRepo;
        this.sheetRepo = sheetRepo;
        this.itemRepo = itemRepo;
        this.pointStatRepo = pointStatRepo;
        this.correctionRepo = correctionRepo;
    }

    public Map<String, Object> create(String openid, Map<String, Object> b) {
        Batch batch = new Batch();
        batch.setOpenid(openid);
        batch.setTitle(Req.str(b, "title"));
        batch.setClassName(Req.str(b, "className"));
        batch.setSubject(Req.str(b, "subject"));
        batch.setType(Req.str(b, "type"));
        batch.setExamDate(Req.str(b, "examDate"));
        batch.setPointScope(Req.strList(b, "pointScope"));
        batch.setStatus("录入中");
        batch.setEntered(0);
        batch.setConfirmed(0);
        batch.setTeachingCovered(new ArrayList<>());
        batch.setCreatedAt(System.currentTimeMillis());
        batchRepo.save(batch);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", batch.getId());
        return r;
    }

    public List<Map<String, Object>> list(String openid) {
        List<Batch> all = batchRepo.findByOpenid(openid);
        all.sort((x, y) -> Long.compare(yn(y), yn(x)));
        return all.stream().map(b -> {
            long entered = sheetRepo.findByOpenidAndBatchId(openid, b.getId()).size();
            long confirmed = sheetRepo.countByOpenidAndBatchIdAndStatus(openid, b.getId(), "已确认");
            PointStat ps = pointStatRepo.findByOpenidAndBatchId(openid, b.getId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getTitle());
            m.put("klass", b.getClassName());
            m.put("date", b.getExamDate());
            m.put("count", b.getPointScope() == null ? 0 : b.getPointScope().size());
            m.put("status", b.getStatus());
            m.put("statusType", statusType(b.getStatus()));
            m.put("entered", entered);
            m.put("confirmed", confirmed);
            m.put("mastery", ps == null ? null : ps.getClassMastery());
            m.put("kpCount", ps == null ? 0 : ps.getKpCount());
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> detail(String openid, Long batchId) {
        Optional<Batch> ob = batchRepo.findByOpenidAndId(openid, batchId);
        if (ob.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("error", "not found");
            return e;
        }
        Batch b = ob.get();
        List<Sheet> ss = sheetRepo.findByOpenidAndBatchId(openid, batchId);
        List<com.shiwutai.backend.model.Item> its = itemRepo.findByOpenidAndBatchIdAndConfirmedTrue(openid, batchId);
        List<Map<String, Object>> kpRank = Analytics.kpRank(its);
        List<Map<String, Object>> errDist = Analytics.errDist(its);
        List<Map<String, Object>> attn = Analytics.computeAttention(its, ss);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getTitle());
        m.put("klass", b.getClassName());
        m.put("subject", b.getSubject());
        m.put("type", b.getType());
        m.put("date", b.getExamDate());
        m.put("kps", b.getPointScope() == null ? "" : String.join("、", b.getPointScope()));
        m.put("stages", buildStages(b, ss));
        m.put("summary", buildSummary(ss, its));
        m.put("topKps", kpRank.stream().limit(5).collect(Collectors.toList()));
        m.put("errDist", errDist);
        m.put("focusList", attn.stream().map(a -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", a.get("name"));
            x.put("cls", a.get("cls") == null ? "danger" : a.get("cls"));
            return x;
        }).collect(Collectors.toList()));
        m.put("sheets", ss.stream().map(s -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", s.getId());
            x.put("studentName", s.getStudentName());
            x.put("status", s.getStatus());
            x.put("images", s.getImages());
            return x;
        }).collect(Collectors.toList()));
        return m;
    }

    public Map<String, Object> remove(String openid, Long batchId) {
        Optional<Batch> ob = batchRepo.findByOpenidAndId(openid, batchId);
        if (ob.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("error", "not found");
            return e;
        }
        // 级联清理（影像文件保留在磁盘，按需可额外清理）
        sheetRepo.deleteAll(sheetRepo.findByOpenidAndBatchId(openid, batchId));
        itemRepo.deleteAll(itemRepo.findByOpenidAndBatchId(openid, batchId));
        correctionRepo.deleteAll(correctionRepo.findByOpenidAndBatchId(openid, batchId));
        pointStatRepo.deleteByOpenidAndBatchId(openid, batchId);
        batchRepo.delete(ob.get());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        return r;
    }

    public Map<String, Object> setStatus(String openid, Long batchId, String status) {
        if (!own(openid, batchId)) return err();
        Batch b = batchRepo.findByOpenidAndId(openid, batchId).get();
        b.setStatus(status);
        batchRepo.save(b);
        return ok();
    }

    public Map<String, Object> setTeaching(String openid, Long batchId, List<String> points) {
        if (!own(openid, batchId)) return err();
        Batch b = batchRepo.findByOpenidAndId(openid, batchId).get();
        b.setTeachingCovered(points == null ? new ArrayList<>() : points);
        batchRepo.save(b);
        return ok();
    }

    public Map<String, Object> overview(String openid) {
        List<Batch> all = batchRepo.findByOpenid(openid);
        List<Sheet> ss = new ArrayList<>();
        for (Batch b : all) ss.addAll(sheetRepo.findByOpenidAndBatchId(openid, b.getId()));
        long pending = ss.stream().filter(s -> "已识别".equals(s.getStatus())).count();
        List<com.shiwutai.backend.model.Item> its = itemRepo.findByOpenidAndConfirmedTrue(openid);
        Set<String> portraits = its.stream().map(com.shiwutai.backend.model.Item::getStudentName).collect(Collectors.toSet());
        List<Map<String, Object>> attn = Analytics.computeAttention(its, ss);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("batches", all.size());
        r.put("pending", pending);
        r.put("portraits", portraits.size());
        r.put("attention", attn.size());
        return r;
    }

    private boolean own(String openid, Long batchId) {
        return batchRepo.findByOpenidAndId(openid, batchId).isPresent();
    }

    private static long yn(Batch b) {
        return b.getCreatedAt() == null ? 0 : b.getCreatedAt();
    }

    private static String statusType(String s) {
        if ("已确认".equals(s)) return "ok";
        if ("待校正".equals(s) || "识别中".equals(s)) return "warn";
        return "";
    }

    private static List<Map<String, Object>> buildStages(Batch b, List<Sheet> ss) {
        long entered = ss.size();
        long recognized = ss.stream().filter(s -> !"待识别".equals(s.getStatus())).count();
        long corrected = ss.stream().filter(s -> "已确认".equals(s.getStatus())).count();
        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(stage("录入 " + entered, entered > 0 ? "ok" : "none"));
        stages.add(stage("识别 " + recognized, recognized > 0 ? "ok" : "none"));
        stages.add(stage("校正 " + corrected, corrected > 0 ? "warn" : "none"));
        stages.add(stage(b.getStatus() != null && b.getStatus().equals("已确认") ? "已确认" : "未确认",
                "已确认".equals(b.getStatus()) ? "ok" : "none"));
        return stages;
    }

    private static Map<String, Object> stage(String label, String state) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("state", state);
        return m;
    }

    private static List<Map<String, Object>> buildSummary(List<Sheet> ss, List<com.shiwutai.backend.model.Item> its) {
        int a = its.size();
        long e = its.stream().filter(Analytics::isWrong).count();
        long low = its.stream().filter(it -> it.getConfidence() != null && it.getConfidence() < 0.6).count();
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(sum(a, "题级记录", ""));
        out.add(sum((int) e, "错题", "danger"));
        out.add(sum((int) low, "低置信度", "warn"));
        out.add(sum(a == 0 ? "—" : (int) Math.round((1.0 - (double) e / a) * 100) + "%", "掌握度", ""));
        return out;
    }

    private static Map<String, Object> sum(Object num, String lb, String color) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("num", num);
        m.put("lb", lb);
        m.put("color", color);
        return m;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        return r;
    }

    private static Map<String, Object> err() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("error", "not found");
        return r;
    }
}
