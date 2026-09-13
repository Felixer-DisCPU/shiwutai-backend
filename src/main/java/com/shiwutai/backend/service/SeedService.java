package com.shiwutai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiwutai.backend.model.Batch;
import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.model.PointStat;
import com.shiwutai.backend.model.Sheet;
import com.shiwutai.backend.repository.BatchRepository;
import com.shiwutai.backend.repository.ItemRepository;
import com.shiwutai.backend.repository.PointStatRepository;
import com.shiwutai.backend.repository.SheetRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SeedService {

    private static final String[] SURNAMES = "王李张刘陈杨赵黄周吴徐孙".split("");
    private static final String[] GIVENS = {"安琪", "思远", "子涵", "一诺", "嘉懿", "慕青", "承轩", "桐", "好", "多多",
            "晓", "雨泽", "欣怡", "宇航", "梓涵", "皓轩", "梦琪", "博文", "雨萱", "俊熙",
            "佳怡", "子轩", "可昕", "浩宇", "若曦", "天磊", "晓彤", "志强", "婉清", "睿",
            "嘉树", "念真", "乐怡", "泽楷", "雨欣", "晨曦", "弘文", "思齐", "悦", "逸尘",
            "明轩", "诗涵", "俊杰", "欣荣", "静姝", "修远", "知微", "怀瑾", "清越", "允文"};
    private static final List<String> POINTS = Arrays.asList(
            "二次函数顶点式", "对称轴与开口方向", "配方法", "图像平移", "最值问题", "实际应用题建模", "交点坐标", "根的判别式");
    private static final Map<String, Double> BASE = new LinkedHashMap<>();
    static {
        BASE.put("二次函数顶点式", 0.40);
        BASE.put("对称轴与开口方向", 0.86);
        BASE.put("配方法", 0.68);
        BASE.put("图像平移", 0.88);
        BASE.put("最值问题", 0.58);
        BASE.put("实际应用题建模", 0.52);
        BASE.put("交点坐标", 0.74);
        BASE.put("根的判别式", 0.80);
    }
    private static final String[] ERR_TYPES = {"概念性错误", "运算/推导失误", "审题与理解偏差", "表达不规范"};

    private final BatchRepository batchRepo;
    private final SheetRepository sheetRepo;
    private final ItemRepository itemRepo;
    private final PointStatRepository pointStatRepo;
    private final Random rnd = new Random();

    public SeedService(BatchRepository batchRepo, SheetRepository sheetRepo,
                       ItemRepository itemRepo, PointStatRepository pointStatRepo) {
        this.batchRepo = batchRepo;
        this.sheetRepo = sheetRepo;
        this.itemRepo = itemRepo;
        this.pointStatRepo = pointStatRepo;
    }

    public Map<String, Object> run(String openid, Long batchId) {
        long bid = (batchId != null) ? batchId : createBatch(openid).getId();
        List<String> names = buildNames(48);
        Map<String, int[]> kpAgg = new LinkedHashMap<>();
        for (String p : POINTS) kpAgg.put(p, new int[2]);

        int itemCount = 0;
        for (String name : names) {
            Sheet sheet = new Sheet();
            sheet.setOpenid(openid);
            sheet.setBatchId(bid);
            sheet.setStudentName(name);
            sheet.setImages(new ArrayList<>());
            sheet.setRawText("");
            sheet.setStatus("已确认");
            sheet.setAiModel("seed");
            sheet.setAiCostMs(0);
            sheet.setCreatedAt(System.currentTimeMillis());
            sheetRepo.save(sheet);
            long sheetId = sheet.getId();

            int qCount = 18 + rnd.nextInt(5); // 18~22
            for (int q = 1; q <= qCount; q++) {
                String pointId = POINTS.get(rnd.nextInt(POINTS.size()));
                double base = BASE.get(pointId);
                boolean correct = rnd.nextDouble() < base;
                String result = correct ? "正确" : (rnd.nextDouble() < 0.25 ? "部分正确" : "错误");
                boolean isErr = !"正确".equals(result);
                String errorType = "";
                if (isErr) {
                    double r2 = rnd.nextDouble();
                    errorType = r2 < 0.45 ? "概念性错误" : r2 < 0.7 ? "运算/推导失误" : r2 < 0.85 ? "审题与理解偏差" : "表达不规范";
                }
                kpAgg.get(pointId)[0]++;
                if (isErr) kpAgg.get(pointId)[1]++;

                Item item = new Item();
                item.setOpenid(openid);
                item.setBatchId(bid);
                item.setSheetId(sheetId);
                item.setStudentName(name);
                item.setQno("第" + q + "题");
                item.setAnswerText("（演示作答）");
                item.setResult(result);
                item.setPointId(pointId);
                item.setPointIds(Collections.singletonList(pointId));
                item.setErrorType(errorType);
                item.setConfidence(correct ? 0.9 : 0.6 + rnd.nextDouble() * 0.2);
                item.setConfirmed(true);
                item.setAiNote("");
                item.setCreatedAt(System.currentTimeMillis());
                itemRepo.save(item);
                itemCount++;
            }
        }

        List<Map<String, Object>> kps = new ArrayList<>();
        int totA = 0, totE = 0;
        for (String p : POINTS) {
            int a = kpAgg.get(p)[0], e = kpAgg.get(p)[1];
            totA += a; totE += e;
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("name", p);
            k.put("rate", a == 0 ? 100 : (int) Math.round((1.0 - (double) e / a) * 100));
            kps.add(k);
        }
        int classMastery = totA == 0 ? 0 : (int) Math.round((1.0 - (double) totE / totA) * 100);

        pointStatRepo.deleteByOpenidAndBatchId(openid, bid);
        PointStat ps = new PointStat();
        ps.setOpenid(openid);
        ps.setBatchId(bid);
        ps.setClassMastery(classMastery);
        ps.setKpCount(POINTS.size());
        try {
            ps.setKps(new ObjectMapper().writeValueAsString(kps));
        } catch (Exception ignored) {
        }
        ps.setCreatedAt(System.currentTimeMillis());
        pointStatRepo.save(ps);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("batchId", bid);
        r.put("students", names.size());
        r.put("items", itemCount);
        r.put("classMastery", classMastery);
        return r;
    }

    private Batch createBatch(String openid) {
        Batch b = new Batch();
        b.setOpenid(openid);
        b.setTitle("第三章 二次函数 单元测验（演示数据）");
        b.setClassName("初二(3)班");
        b.setSubject("数学");
        b.setType("单元测验");
        b.setExamDate("09-08");
        b.setPointScope(new ArrayList<>(POINTS));
        b.setStatus("已确认");
        b.setEntered(48);
        b.setConfirmed(48);
        b.setTeachingCovered(new ArrayList<>());
        b.setCreatedAt(System.currentTimeMillis());
        return batchRepo.save(b);
    }

    private List<String> buildNames(int n) {
        List<String> names = new ArrayList<>();
        for (String s : SURNAMES) {
            for (String g : GIVENS) {
                if (names.size() >= n) break;
                names.add(s + g);
            }
            if (names.size() >= n) break;
        }
        return names;
    }
}
