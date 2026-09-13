package com.shiwutai.backend.service;

import com.shiwutai.backend.model.KnowledgePoint;
import com.shiwutai.backend.repository.KnowledgePointRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InitService {

    private final KnowledgePointRepository kpRepo;

    public InitService(KnowledgePointRepository kpRepo) {
        this.kpRepo = kpRepo;
    }

    /** 内置初中数学知识点三级模板（学科 → 章节 → 知识点）。 */
    private static final Object[][] TPL = {
            {"初中数学", "二次函数", "二次函数顶点式", "顶点式|配方顶点式"},
            {"初中数学", "二次函数", "对称轴与开口方向", "对称轴|开口"},
            {"初中数学", "二次函数", "配方法", "配方"},
            {"初中数学", "二次函数", "图像平移", "平移|平移变换"},
            {"初中数学", "二次函数", "最值问题", "最值|极值"},
            {"初中数学", "二次函数", "实际应用题建模", "应用题|建模"},
            {"初中数学", "二次函数", "交点坐标", "与坐标轴交点"},
            {"初中数学", "一元二次方程", "根的判别式", "判别式|Δ"},
            {"初中数学", "一元二次方程", "求根公式", "公式法"},
            {"初中数学", "一元二次方程", "韦达定理", "根与系数关系"},
            {"初中数学", "反比例函数", "反比例函数图像与性质", "反比函数"},
            {"初中数学", "反比例函数", "待定系数法求解析式", "待定系数法"},
            {"初中数学", "相似三角形", "相似的判定", "相似判定"},
            {"初中数学", "相似三角形", "相似的性质", "相似性质"},
    };

    public Map<String, Object> run(String openid) {
        long cnt = kpRepo.countByOpenid(openid);
        if (cnt > 0) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", true);
            r.put("skipped", true);
            r.put("total", cnt);
            return r;
        }
        long now = System.currentTimeMillis();
        int n = 0;
        for (Object[] row : TPL) {
            KnowledgePoint k = new KnowledgePoint();
            k.setOpenid(openid);
            k.setSubject((String) row[0]);
            k.setChapter((String) row[1]);
            k.setName((String) row[2]);
            k.setAliases(List.of(((String) row[3]).split("\\|")));
            k.setCreatedAt(now);
            kpRepo.save(k);
            n++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("total", n);
        return r;
    }
}
