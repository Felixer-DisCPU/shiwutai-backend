package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Batch;
import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.model.Sheet;
import com.shiwutai.backend.repository.BatchRepository;
import com.shiwutai.backend.repository.ItemRepository;
import com.shiwutai.backend.repository.SheetRepository;
import com.shiwutai.backend.util.Analytics;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyzeService {

    private final ItemRepository itemRepo;
    private final SheetRepository sheetRepo;
    private final BatchRepository batchRepo;

    public AnalyzeService(ItemRepository itemRepo, SheetRepository sheetRepo, BatchRepository batchRepo) {
        this.itemRepo = itemRepo;
        this.sheetRepo = sheetRepo;
        this.batchRepo = batchRepo;
    }

    public Map<String, Object> overview(String openid, Long batchId) {
        List<Item> its = itemRepo.findByOpenidAndBatchIdAndConfirmedTrue(openid, batchId);
        List<Map<String, Object>> kp = Analytics.kpRank(its);
        List<Map<String, Object>> err = Analytics.errDist(its);
        int a = its.size();
        long e = its.stream().filter(Analytics::isWrong).count();
        int classMastery = Analytics.classMasteryPct(its);
        List<Map<String, Object>> top20 = kp.stream().limit(Math.max(1, (int) Math.ceil(kp.size() * 0.2))).collect(Collectors.toList());
        long totalWrong = kp.stream().mapToInt(x -> (Integer) x.get("wrong")).sum();
        int concentration = totalWrong == 0 ? 0 : (int) Math.round(
                (double) top20.stream().mapToInt(x -> (Integer) x.get("wrong")).sum() / totalWrong * 100);
        List<Map<String, Object>> attn = Analytics.computeAttention(its,
                sheetRepo.findByOpenidAndBatchId(openid, batchId));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("num", classMastery + "%");
        summary.put("lb", "掌握度");
        summary.put("color", "");
        Map<String, Object> s2 = new LinkedHashMap<>();
        s2.put("num", (int) e);
        s2.put("lb", "错题数");
        s2.put("color", "danger");
        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("num", concentration + "%");
        s3.put("lb", "错误集中度");
        s3.put("color", concentration > 60 ? "warn" : "");
        Map<String, Object> s4 = new LinkedHashMap<>();
        s4.put("num", attn.size());
        s4.put("lb", "需关注");
        s4.put("color", "danger");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("batchId", batchId);
        r.put("classMastery", classMastery);
        r.put("kpRank", kp.stream().limit(8).collect(Collectors.toList()));
        r.put("errDist", err);
        r.put("concentration", concentration);
        r.put("attention", attn.size());
        r.put("summary", Arrays.asList(summary, s2, s3, s4));
        return r;
    }

    public List<Map<String, Object>> students(String openid, Long batchId) {
        List<Sheet> sh = sheetRepo.findByOpenidAndBatchId(openid, batchId);
        List<Sheet> confirmed = sh.stream().filter(s -> "已确认".equals(s.getStatus())).collect(Collectors.toList());
        Set<String> names = confirmed.stream().map(Sheet::getStudentName).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Batch> allBatches = confirmedBatches(openid);

        List<Map<String, Object>> students = new ArrayList<>();
        for (String name : names) {
            List<Item> its = itemRepo.findByOpenidAndBatchIdAndStudentNameAndConfirmedTrue(openid, batchId, name);
            if (its.isEmpty()) continue;
            List<Map<String, Object>> kp = Analytics.kpRank(its).stream().limit(6).map(k -> {
                Map<String, Object> x = new LinkedHashMap<>(k);
                x.put("cls", Analytics.rateCls((Integer) k.get("rate")));
                return x;
            }).collect(Collectors.toList());
            List<Map<String, Object>> err = Analytics.errDist(its);
            Map<String, Object> dom = err.stream().max(Comparator.comparingInt(x -> (Integer) x.get("rate"))).orElse(null);
            List<Integer> trend = new ArrayList<>();
            for (Batch b : allBatches) {
                List<Item> bi = itemRepo.findByOpenidAndBatchIdAndStudentNameAndConfirmedTrue(openid, b.getId(), name);
                if (!bi.isEmpty()) trend.add((int) Math.round(Analytics.rateOf(bi) * 100));
            }
            int rate = (int) Math.round(Analytics.rateOf(its) * 100);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("rate", rate + "%");
            m.put("rateCls", Analytics.rateCls(rate));
            m.put("times", allBatches.size());
            m.put("questions", its.size());
            m.put("kp", kp);
            m.put("err", err.stream().map(x -> {
                Map<String, Object> y = new LinkedHashMap<>(x);
                int rt = (Integer) x.get("rate");
                y.put("cls", rt >= 40 ? "danger" : (rt >= 15 ? "warn" : ""));
                return y;
            }).collect(Collectors.toList()));
            m.put("hint", Analytics.hintFor(err));
            m.put("trend", trend);
            m.put("advice", Analytics.adviceFor(name, kp, err));
            m.put("focus", rate < 60 ? "优先" : "关注");
            students.add(m);
        }
        return students;
    }

    public Map<String, Object> studentDetail(String openid, Long batchId, String name) {
        List<Item> its = itemRepo.findByOpenidAndBatchIdAndStudentNameAndConfirmedTrue(openid, batchId, name);
        List<Item> wrong = its.stream().filter(Analytics::isWrong).collect(Collectors.toList());
        List<Sheet> sh = sheetRepo.findByOpenidAndBatchIdAndStudentName(openid, batchId, name);
        Map<Long, String> imageMap = new LinkedHashMap<>();
        for (Sheet s : sh) {
            if (s.getImages() != null && !s.getImages().isEmpty()) imageMap.put(s.getId(), s.getImages().get(0));
        }
        List<Map<String, Object>> wrongList = wrong.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("qno", i.getQno());
            m.put("pointId", i.getPointId());
            m.put("errorType", i.getErrorType());
            m.put("answerText", i.getAnswerText());
            m.put("image", imageMap.getOrDefault(i.getSheetId(), ""));
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("wrongList", wrongList);
        return r;
    }

    public List<Map<String, Object>> trend(String openid, String pointId) {
        List<Batch> allBatches = confirmedBatches(openid);
        List<Map<String, Object>> series = new ArrayList<>();
        for (Batch b : allBatches) {
            List<Item> bi = itemRepo.findByOpenidAndBatchIdAndPointIdAndConfirmedTrue(openid, b.getId(), pointId);
            if (!bi.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("batch", b.getTitle());
                m.put("rate", (int) Math.round(Analytics.rateOf(bi) * 100));
                series.add(m);
            }
        }
        return series;
    }

    public Map<String, Object> effectiveness(String openid, String pointId) {
        List<Batch> allBatches = confirmedBatches(openid);
        int idx = -1;
        for (int i = 0; i < allBatches.size(); i++) {
            List<String> covered = allBatches.get(i).getTeachingCovered();
            if (covered != null && covered.contains(pointId)) { idx = i; break; }
        }
        if (idx < 0) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", false);
            r.put("note", "未找到该知识点的讲评标记");
            return r;
        }
        Rate e1 = calc(openid, allBatches.subList(0, idx));
        Rate e2 = calc(openid, allBatches.subList(idx + 1, allBatches.size()));
        if (e1 == null || e2 == null || e1.n < 10 || e2.n < 10) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", false);
            r.put("note", "样本不足（单侧<10人次），暂不判断");
            r.put("e1", e1 == null ? null : e1.toMap());
            r.put("e2", e2 == null ? null : e2.toMap());
            return r;
        }
        int delta = e1.rate - e2.rate;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("from", e1.rate + "%");
        r.put("to", e2.rate + "%");
        r.put("delta", "↓" + delta + "%");
        r.put("toRate", e2.rate);
        r.put("note", delta > 10 ? "改善" : (delta < 0 ? "反弹" : "无变化"));
        return r;
    }

    public List<Map<String, Object>> attention(String openid, Long batchId) {
        List<Item> its = itemRepo.findByOpenidAndConfirmedTrue(openid);
        List<Sheet> sh = sheetRepo.findByOpenidAndBatchId(openid, batchId);
        return Analytics.computeAttention(its, sh);
    }

    private List<Batch> confirmedBatches(String openid) {
        return batchRepo.findByOpenid(openid).stream()
                .filter(b -> "已确认".equals(b.getStatus()))
                .sorted(Comparator.comparingLong(b -> b.getCreatedAt() == null ? 0 : b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    private Rate calc(String openid, List<Batch> batches) {
        int a = 0, e = 0;
        for (Batch b : batches) {
            List<Item> bi = itemRepo.findByOpenidAndBatchIdAndConfirmedTrue(openid, b.getId());
            a += bi.size();
            e += bi.stream().filter(Analytics::isWrong).count();
        }
        if (a == 0) return null;
        return new Rate((int) Math.round((1.0 - (double) e / a) * 100), a);
    }

    private static class Rate {
        int rate;
        int n;
        Rate(int rate, int n) { this.rate = rate; this.n = n; }
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rate", rate + "%");
            m.put("n", n);
            return m;
        }
    }
}
