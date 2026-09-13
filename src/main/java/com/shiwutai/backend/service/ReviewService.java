package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Batch;
import com.shiwutai.backend.model.Correction;
import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.model.PointStat;
import com.shiwutai.backend.model.Sheet;
import com.shiwutai.backend.repository.BatchRepository;
import com.shiwutai.backend.repository.CorrectionRepository;
import com.shiwutai.backend.repository.ItemRepository;
import com.shiwutai.backend.repository.PointStatRepository;
import com.shiwutai.backend.repository.SheetRepository;
import com.shiwutai.backend.util.Analytics;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ItemRepository itemRepo;
    private final SheetRepository sheetRepo;
    private final BatchRepository batchRepo;
    private final PointStatRepository pointStatRepo;
    private final CorrectionRepository correctionRepo;
    private final RecognizeService recognizeService;
    private final ConfigService config;

    public ReviewService(ItemRepository itemRepo, SheetRepository sheetRepo,
                         BatchRepository batchRepo, PointStatRepository pointStatRepo,
                         CorrectionRepository correctionRepo, RecognizeService recognizeService,
                         ConfigService config) {
        this.itemRepo = itemRepo;
        this.sheetRepo = sheetRepo;
        this.batchRepo = batchRepo;
        this.pointStatRepo = pointStatRepo;
        this.correctionRepo = correctionRepo;
        this.recognizeService = recognizeService;
        this.config = config;
    }

    public Map<String, Object> list(String openid, Long batchId) {
        List<Item> it = itemRepo.findByOpenidAndBatchIdAndConfirmedFalse(openid, batchId);
        it.sort(Comparator.comparingDouble(i -> i.getConfidence() == null ? 1.0 : i.getConfidence()));
        List<Sheet> sh = sheetRepo.findByOpenidAndBatchId(openid, batchId);
        Map<Long, String> firstImage = new LinkedHashMap<>();
        for (Sheet s : sh) {
            if (s.getImages() != null && !s.getImages().isEmpty()) firstImage.put(s.getId(), s.getImages().get(0));
        }
        double confThreshold = config.confThreshold();
        boolean forceReview = config.flag("RECOGNIZE_FORCE_REVIEW");
        List<Map<String, Object>> itemsOut = it.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("qno", i.getQno());
            m.put("answerText", i.getAnswerText());
            m.put("result", i.getResult());
            m.put("pointId", i.getPointId());
            m.put("pointIds", i.getPointIds());
            m.put("errorType", i.getErrorType());
            m.put("confidence", i.getConfidence());
            boolean low = i.getConfidence() != null && i.getConfidence() < confThreshold;
            m.put("lowConf", low);
            m.put("forceReview", forceReview && low);
            m.put("studentName", i.getStudentName());
            m.put("sheetId", i.getSheetId());
            m.put("image", firstImage.getOrDefault(i.getSheetId(), ""));
            return m;
        }).collect(Collectors.toList());
        List<Map<String, Object>> sheets = sh.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentName", s.getStudentName());
            m.put("images", s.getImages());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", itemsOut);
        r.put("sheets", sheets);
        return r;
    }

    public Map<String, Object> correct(String openid, Long itemId, Map<String, Object> patch) {
        Optional<Item> oi = itemRepo.findByOpenidAndId(openid, itemId);
        if (oi.isEmpty()) return err();
        Item it = oi.get();
        for (String f : new String[]{"result", "pointId", "errorType"}) {
            if (patch.containsKey(f)) {
                String before = str(getField(it, f));
                String after = str(patch.get(f));
                if (!before.equals(after)) {
                    Correction c = new Correction();
                    c.setOpenid(openid);
                    c.setItemId(it.getId());
                    c.setSheetId(it.getSheetId());
                    c.setBatchId(it.getBatchId());
                    c.setField(f);
                    c.setBeforeVal(before);
                    c.setAfterVal(after);
                    c.setCreatedAt(System.currentTimeMillis());
                    correctionRepo.save(c);
                }
                setField(it, f, after);
            }
        }
        itemRepo.save(it);
        return ok();
    }

    public Map<String, Object> batchCorrect(String openid, Long batchId, String pointId, Map<String, Object> patch) {
        List<Item> targets = itemRepo.findByOpenidAndBatchIdAndPointIdAndConfirmedFalse(openid, batchId, pointId);
        for (Item it : targets) {
            for (String f : new String[]{"result", "errorType"}) {
                if (patch.containsKey(f)) {
                    String before = str(getField(it, f));
                    String after = str(patch.get(f));
                    if (!before.equals(after)) {
                        Correction c = new Correction();
                        c.setOpenid(openid);
                        c.setItemId(it.getId());
                        c.setSheetId(it.getSheetId());
                        c.setBatchId(batchId);
                        c.setField(f);
                        c.setBeforeVal(before);
                        c.setAfterVal(after);
                        c.setCreatedAt(System.currentTimeMillis());
                        correctionRepo.save(c);
                    }
                    setField(it, f, after);
                }
            }
            itemRepo.save(it);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("count", targets.size());
        return r;
    }

    public Map<String, Object> reRecognize(String openid, Long sheetId) {
        return recognizeService.trigger(openid, null, sheetId);
    }

    public Map<String, Object> confirm(String openid, Long batchId) {
        List<Item> unconfirmed = itemRepo.findByOpenidAndBatchIdAndConfirmedFalse(openid, batchId);
        for (Item it : unconfirmed) {
            it.setConfirmed(true);
            itemRepo.save(it);
        }
        List<Sheet> sh = sheetRepo.findByOpenidAndBatchId(openid, batchId);
        for (Sheet s : sh) {
            s.setStatus("已确认");
            sheetRepo.save(s);
        }
        Optional<Batch> ob = batchRepo.findByOpenidAndId(openid, batchId);
        ob.ifPresent(b -> {
            b.setStatus("已确认");
            batchRepo.save(b);
        });

        List<Item> all = itemRepo.findByOpenidAndBatchIdAndConfirmedTrue(openid, batchId);
        Map<String, int[]> m = new LinkedHashMap<>();
        for (Item it : all) {
            String p = (it.getPointId() == null || it.getPointId().isBlank()) ? "未分类" : it.getPointId();
            m.computeIfAbsent(p, k -> new int[2])[0]++;
            if (Analytics.isWrong(it)) m.get(p)[1]++;
        }
        int totA = all.size();
        long totE = all.stream().filter(Analytics::isWrong).count();
        int classMastery = totA == 0 ? 0 : (int) Math.round((1.0 - (double) totE / totA) * 100);
        List<Map<String, Object>> kps = new ArrayList<>();
        for (Map.Entry<String, int[]> e : m.entrySet()) {
            int a = e.getValue()[0], er = e.getValue()[1];
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("name", e.getKey());
            k.put("rate", a == 0 ? 100 : (int) Math.round((1.0 - (double) er / a) * 100));
            kps.add(k);
        }
        pointStatRepo.deleteByOpenidAndBatchId(openid, batchId);
        PointStat ps = new PointStat();
        ps.setOpenid(openid);
        ps.setBatchId(batchId);
        ps.setClassMastery(classMastery);
        ps.setKpCount(m.size());
        try {
            ps.setKps(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(kps));
        } catch (Exception ignored) {
        }
        ps.setCreatedAt(System.currentTimeMillis());
        pointStatRepo.save(ps);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("confirmed", unconfirmed.size());
        return r;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String getField(Item it, String f) {
        switch (f) {
            case "result": return it.getResult();
            case "pointId": return it.getPointId();
            case "errorType": return it.getErrorType();
            default: return "";
        }
    }

    private static void setField(Item it, String f, String v) {
        switch (f) {
            case "result": it.setResult(v); break;
            case "pointId": it.setPointId(v); break;
            case "errorType": it.setErrorType(v); break;
        }
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
