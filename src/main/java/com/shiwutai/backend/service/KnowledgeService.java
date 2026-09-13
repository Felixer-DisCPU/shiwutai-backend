package com.shiwutai.backend.service;

import com.shiwutai.backend.model.KnowledgePoint;
import com.shiwutai.backend.repository.KnowledgePointRepository;
import com.shiwutai.backend.util.Req;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgePointRepository kpRepo;

    public KnowledgeService(KnowledgePointRepository kpRepo) {
        this.kpRepo = kpRepo;
    }

    public List<Map<String, Object>> list(String openid, String subject) {
        String subj = (subject == null || subject.isBlank()) ? "初中数学" : subject;
        return kpRepo.findByOpenidAndSubject(openid, subj).stream()
                .sorted((a, b) -> {
                    int c = nullSafe(a.getChapter()).compareTo(nullSafe(b.getChapter()));
                    return c != 0 ? c : nullSafe(a.getName()).compareTo(nullSafe(b.getName()));
                })
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> add(String openid, Map<String, Object> kp) {
        KnowledgePoint k = new KnowledgePoint();
        k.setOpenid(openid);
        k.setSubject(Req.str(kp, "subject"));
        k.setChapter(Req.str(kp, "chapter"));
        k.setName(Req.str(kp, "name"));
        k.setAliases(Req.strList(kp, "aliases"));
        k.setCreatedAt(System.currentTimeMillis());
        kpRepo.save(k);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", k.getId());
        return r;
    }

    public Map<String, Object> update(String openid, Long id, Map<String, Object> patch) {
        Optional<KnowledgePoint> opt = kpRepo.findByOpenidAndId(openid, id);
        if (opt.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("error", "not found");
            return e;
        }
        KnowledgePoint k = opt.get();
        if (patch.containsKey("subject")) k.setSubject(Req.str(patch, "subject"));
        if (patch.containsKey("chapter")) k.setChapter(Req.str(patch, "chapter"));
        if (patch.containsKey("name")) k.setName(Req.str(patch, "name"));
        if (patch.containsKey("aliases")) k.setAliases(Req.strList(patch, "aliases"));
        kpRepo.save(k);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        return r;
    }

    public Map<String, Object> remove(String openid, Long id) {
        Optional<KnowledgePoint> opt = kpRepo.findByOpenidAndId(openid, id);
        if (opt.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("error", "not found");
            return e;
        }
        kpRepo.delete(opt.get());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        return r;
    }

    private Map<String, Object> toMap(KnowledgePoint k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", k.getId());
        m.put("subject", k.getSubject());
        m.put("chapter", k.getChapter());
        m.put("name", k.getName());
        m.put("aliases", k.getAliases());
        return m;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
