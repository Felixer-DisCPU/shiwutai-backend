package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.model.Sheet;
import com.shiwutai.backend.repository.ItemRepository;
import com.shiwutai.backend.repository.SheetRepository;
import com.shiwutai.backend.util.Req;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SheetService {

    private final SheetRepository sheetRepo;
    private final ItemRepository itemRepo;

    public SheetService(SheetRepository sheetRepo, ItemRepository itemRepo) {
        this.sheetRepo = sheetRepo;
        this.itemRepo = itemRepo;
    }

    public Map<String, Object> add(String openid, Long batchId, String studentName, List<String> images) {
        if (batchId == null || studentName == null || studentName.isBlank()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("error", "missing batchId or studentName");
            return e;
        }
        Sheet s = new Sheet();
        s.setOpenid(openid);
        s.setBatchId(batchId);
        s.setStudentName(studentName);
        s.setImages(images == null ? new ArrayList<>() : images);
        s.setRawText("");
        s.setStatus("待识别");
        s.setAiModel("");
        s.setAiCostMs(0);
        s.setCreatedAt(System.currentTimeMillis());
        sheetRepo.save(s);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", s.getId());
        return r;
    }

    public List<Map<String, Object>> list(String openid, Long batchId) {
        List<Sheet> ss = sheetRepo.findByOpenidAndBatchId(openid, batchId);
        Map<String, Sheet> latest = new LinkedHashMap<>();
        for (Sheet s : ss) {
            Sheet cur = latest.get(s.getStudentName());
            if (cur == null || Long.compare(yn(s), yn(cur)) > 0) latest.put(s.getStudentName(), s);
        }
        return latest.values().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("studentName", s.getStudentName());
            m.put("status", s.getStatus());
            m.put("images", s.getImages());
            m.put("entered", true);
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> retake(String openid, Long sheetId, List<String> images) {
        Optional<Sheet> os = sheetRepo.findByOpenidAndId(openid, sheetId);
        if (os.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("error", "not found");
            return e;
        }
        Sheet s = os.get();
        s.setImages(images == null ? new ArrayList<>() : images);
        s.setStatus("待识别");
        s.setRawText("");
        sheetRepo.save(s);
        itemRepo.deleteAll(itemRepo.findByOpenidAndSheetId(openid, sheetId));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        return r;
    }

    private static long yn(Sheet s) {
        return s.getCreatedAt() == null ? 0 : s.getCreatedAt();
    }
}
