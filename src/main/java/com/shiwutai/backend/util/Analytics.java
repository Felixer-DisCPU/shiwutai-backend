package com.shiwutai.backend.util;

import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.model.Sheet;

import java.util.*;

/**
 * 学情计算核心（F5）。纯函数，统一以「正确 / 错误 / 部分正确 / 未作答」判定正误，
 * 避免云函数版本中部分统计用英文 'wrong'/'partial' 导致的掌握度恒为 100% 的 bug。
 */
public final class Analytics {

    private Analytics() {}

    public static boolean isWrong(Item i) {
        String r = i.getResult();
        return "错误".equals(r) || "部分正确".equals(r);
    }

    public static double rateOf(List<Item> items) {
        int a = items.size();
        if (a == 0) return 1.0;
        long e = items.stream().filter(Analytics::isWrong).count();
        return 1.0 - (double) e / a;
    }

    public static int classMasteryPct(List<Item> items) {
        int a = items.size();
        if (a == 0) return 0;
        long e = items.stream().filter(Analytics::isWrong).count();
        return (int) Math.round((1.0 - (double) e / a) * 100);
    }

    /** 知识点掌握度排行（错误数多的排前面）。 */
    public static List<Map<String, Object>> kpRank(List<Item> items) {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (Item it : items) {
            String p = it.getPointId() == null || it.getPointId().isBlank() ? "未分类" : it.getPointId();
            m.computeIfAbsent(p, k -> new int[2])[0]++;            // A
            if (isWrong(it)) m.get(p)[1]++;                        // E
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : m.entrySet()) {
            int a = e.getValue()[0], er = e.getValue()[1];
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("name", e.getKey());
            k.put("rate", a == 0 ? 100 : (int) Math.round((1.0 - (double) er / a) * 100));
            k.put("wrong", er);
            out.add(k);
        }
        out.sort((x, y) -> ((Integer) y.get("wrong")) - ((Integer) x.get("wrong")));
        return out;
    }

    /** 四类错误分布。 */
    public static List<Map<String, Object>> errDist(List<Item> items) {
        String[] types = {"概念性错误", "运算/推导失误", "审题与理解偏差", "表达不规范"};
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String t : types) m.put(t, 0);
        int tot = 0;
        for (Item it : items) {
            String e = it.getErrorType();
            if (e != null && m.containsKey(e)) { m.put(e, m.get(e) + 1); tot++; }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String t : types) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", t);
            x.put("count", m.get(t));
            x.put("rate", tot == 0 ? 0 : (int) Math.round((double) m.get(t) / tot * 100));
            out.add(x);
        }
        return out;
    }

    public static String rateCls(int r) {
        if (r < 60) return "danger";
        if (r < 80) return "warn";
        return "ok";
    }

    /** 错误集中度：排名前 20%（至少 1 个）知识点贡献的错误占全部错误的比例。 */
    public static int concentration(List<Map<String, Object>> rank) {
        if (rank.isEmpty()) return 0;
        int k = Math.max(1, (int) Math.ceil(rank.size() * 0.2));
        long totalWrong = rank.stream().mapToInt(x -> (Integer) x.get("wrong")).sum();
        if (totalWrong == 0) return 0;
        long topWrong = rank.stream().limit(k).mapToInt(x -> (Integer) x.get("wrong")).sum();
        return (int) Math.round((double) topWrong / totalWrong * 100);
    }

    /** 需关注学生：连续两个已确认批次掌握率 < 60%。 */
    public static List<Map<String, Object>> computeAttention(List<Item> its, List<Sheet> sh) {
        Map<String, List<Sheet>> byStudent = new LinkedHashMap<>();
        for (Sheet s : sh) {
            if (!"已确认".equals(s.getStatus())) continue;
            byStudent.computeIfAbsent(s.getStudentName(), k -> new ArrayList<>()).add(s);
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map.Entry<String, List<Sheet>> e : byStudent.entrySet()) {
            String name = e.getKey();
            List<Sheet> list = e.getValue();
            list.sort(Comparator.comparingLong(s -> s.getCreatedAt() == null ? 0 : s.getCreatedAt()));
            int streak = 0;
            for (Sheet s : list) {
                List<Item> si = new ArrayList<>();
                for (Item it : its) {
                    if (name.equals(it.getStudentName()) && s.getBatchId().equals(it.getBatchId())) si.add(it);
                }
                double p = rateOf(si);
                if (p < 0.6) {
                    streak++;
                    if (streak >= 2) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("name", name);
                        r.put("cls", "danger");
                        res.add(r);
                        break;
                    }
                } else {
                    streak = 0;
                }
            }
        }
        return res;
    }

    public static String hintFor(List<Map<String, Object>> err) {
        if (err == null || err.isEmpty()) return "错误分布较均衡，无明显主导类型。";
        Map<String, Object> dom = err.stream().max(Comparator.comparingInt(x -> (Integer) x.get("rate"))).orElse(null);
        if (dom == null || (Integer) dom.get("rate") < 10) return "错误分布较均衡，无明显主导类型。";
        switch ((String) dom.get("name")) {
            case "概念性错误": return "概念性错误占比最高，属于「不会」而非粗心，需要重讲概念。";
            case "运算/推导失误": return "运算/推导失误占比最高，建议练规范与限时计算。";
            case "审题与理解偏差": return "审题偏差占比最高，是读题习惯问题，建议训练圈画关键词。";
            default: return dom.get("name") + "占比最高，可作为辅导重点。";
        }
    }

    public static String adviceFor(String name, List<Map<String, Object>> kp, List<Map<String, Object>> err) {
        List<Map<String, Object>> weak = new ArrayList<>();
        for (Map<String, Object> k : kp) if ((Integer) k.get("rate") < 60) weak.add(k);
        weak.sort(Comparator.comparingInt(x -> (Integer) x.get("rate")));
        if (weak.isEmpty()) return name + "本次掌握良好，保持练习即可。";
        Map<String, Object> w = weak.get(0);
        String errHint = (err != null && !err.isEmpty() && (Integer) err.get(0).get("rate") >= 40)
                ? " 错误以" + err.get(0).get("name") + "为主，建议先从概念讲起。"
                : " 建议配套阶梯练习巩固。";
        return "优先补 <" + w.get("name") + ">（掌握率 " + w.get("rate") + "%）。" + errHint;
    }
}
