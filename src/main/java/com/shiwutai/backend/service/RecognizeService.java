package com.shiwutai.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiwutai.backend.model.Batch;
import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.model.Sheet;
import com.shiwutai.backend.repository.BatchRepository;
import com.shiwutai.backend.repository.ItemRepository;
import com.shiwutai.backend.repository.SheetRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecognizeService {

    private static final int K = 3; // 单次识别份数，避免请求超时

    private static final List<String> ERR_TYPES = Arrays.asList(
            "概念性错误", "运算/推导失误", "审题与理解偏差", "表达不规范");

    private final SheetRepository sheetRepo;
    private final ItemRepository itemRepo;
    private final BatchRepository batchRepo;
    private final FileStore fileStore;
    private final LlmService llm;
    private final ConfigService config;

    private final ObjectMapper om = new ObjectMapper();

    public RecognizeService(SheetRepository sheetRepo, ItemRepository itemRepo,
                            BatchRepository batchRepo, FileStore fileStore, LlmService llm,
                            ConfigService config) {
        this.sheetRepo = sheetRepo;
        this.itemRepo = itemRepo;
        this.batchRepo = batchRepo;
        this.fileStore = fileStore;
        this.llm = llm;
        this.config = config;
    }

    public Map<String, Object> trigger(String openid, Long batchId, Long sheetId) {
        List<Sheet> todo;
        if (sheetId != null) {
            Optional<Sheet> os = sheetRepo.findByOpenidAndId(openid, sheetId);
            todo = os.map(Collections::singletonList).orElse(Collections.emptyList());
        } else {
            todo = sheetRepo.findByOpenidAndBatchIdAndStatus(openid, batchId, "待识别");
            if (todo.size() > K) todo = todo.subList(0, K);
        }

        Batch batch = (batchId != null) ? batchRepo.findByOpenidAndId(openid, batchId).orElse(null) : null;
        String scopeText = (batch != null && batch.getPointScope() != null)
                ? String.join("、", batch.getPointScope()) : "";

        List<Map<String, Object>> results = new ArrayList<>();
        for (Sheet s : todo) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("sheetId", s.getId());
            try {
                if (s.getImages() == null || s.getImages().isEmpty()) {
                    throw new IllegalStateException("无可识别影像");
                }
                List<byte[]> bytes = new ArrayList<>();
                for (String url : s.getImages()) {
                    byte[] b = fileStore.read(fileStore.filenameFromUrl(url));
                    if (b != null) bytes.add(b);
                }
                if (bytes.isEmpty()) throw new IllegalStateException("无可识别影像");
                String content = llm.callVision(openid, bytes, scopeText);
                List<Map<String, Object>> items = parseItems(content);
                for (Map<String, Object> it : items) {
                    Item item = new Item();
                    item.setOpenid(openid);
                    item.setBatchId(s.getBatchId());
                    item.setSheetId(s.getId());
                    item.setStudentName(s.getStudentName());
                    item.setQno((String) it.get("qno"));
                    item.setAnswerText((String) it.get("answerText"));
                    item.setResult((String) it.get("result"));
                    item.setPointId((String) it.get("pointId"));
                    item.setPointIds((List<String>) it.get("pointIds"));
                    item.setErrorType((String) it.get("errorType"));
                    Double conf = (Double) it.get("confidence");
                    item.setConfidence(conf);
                    item.setAiNote("");
                    // 高置信度结果自动采纳：开关开启且置信度 >= 阈值时，直接标记已确认（无需教师复核）
                    boolean autoAdopt = config.flag("RECOGNIZE_AUTO_ADOPT");
                    item.setConfirmed(autoAdopt && conf != null && conf >= config.confThreshold());
                    item.setCreatedAt(System.currentTimeMillis());
                    itemRepo.save(item);
                }
                s.setStatus("已识别");
                s.setRawText(items.stream().map(i -> (String) i.get("qno") + "：" + i.get("answerText")).collect(java.util.stream.Collectors.joining("\n")));
                s.setAiModel(llm.visionModelName());
                s.setAiCostMs(0);
                sheetRepo.save(s);
                one.put("ok", true);
                one.put("count", items.size());
            } catch (Exception e) {
                one.put("ok", false);
                one.put("error", e.getMessage());
            }
            results.add(one);
        }

        long remaining = 0;
        if (sheetId == null && batchId != null) {
            remaining = sheetRepo.countByOpenidAndBatchIdAndStatus(openid, batchId, "待识别");
            if (remaining == 0 && batch != null) {
                batch.setStatus("待校正");
                // 整批识别完成：不再依赖教师手动勾选的范围，而是根据清单名称 + 录入的作业内容自动归纳知识点范围
                generateScope(openid, batch);
                batchRepo.save(batch);
            } else if (batch != null && "录入中".equals(batch.getStatus())) {
                batch.setStatus("识别中");
                batchRepo.save(batch);
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("processed", results.size());
        r.put("remaining", remaining);
        r.put("results", results);
        return r;
    }

    /** 根据清单名称 + 本批录入的作业内容，用模型自动归纳知识点范围并写回 batch.pointScope。失败则保留原值。 */
    private void generateScope(String openid, Batch batch) {
        List<Item> items = itemRepo.findByOpenidAndBatchId(openid, batch.getId());
        if (items.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (it.getAnswerText() != null && !it.getAnswerText().isBlank()) {
                sb.append(it.getAnswerText()).append("\n");
            }
        }
        String content = sb.length() > 4000 ? sb.substring(0, 4000) : sb.toString();
        if (content.isBlank()) return;
        List<String> scope = llm.callScope(batch.getTitle(), content);
        if (scope != null && !scope.isEmpty()) {
            batch.setPointScope(scope);
        }
    }

    private List<Map<String, Object>> parseItems(String content) throws Exception {
        if (content == null) throw new IllegalStateException("模型未返回内容");
        int i = content.indexOf('[');
        int j = content.lastIndexOf(']');
        if (i < 0 || j < 0 || j <= i) throw new IllegalStateException("模型未返回 JSON 数组");
        JsonNode arr = om.readTree(content.substring(i, j + 1));
        if (!arr.isArray()) throw new IllegalStateException("模型未返回 JSON 数组");
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("qno", n.path("qno").asText(""));
            it.put("answerText", n.path("answerText").asText(n.path("answer").asText("")));
            String result = n.path("result").asText("未作答");
            it.put("result", result);
            List<String> points = new ArrayList<>();
            JsonNode p = n.path("points");
            if (p.isArray()) for (JsonNode x : p) points.add(x.asText());
            String mainPoint = points.isEmpty() ? n.path("pointId").asText("未分类") : points.get(0);
            it.put("pointId", mainPoint);
            it.put("pointIds", points);
            String errType = n.path("errorType").asText("");
            if (!ERR_TYPES.contains(errType)) {
                errType = "正确".equals(result) ? "" : "概念性错误";
            }
            it.put("errorType", errType);
            double conf = n.path("confidence").asDouble(0.5);
            it.put("confidence", conf);
            out.add(it);
        }
        return out;
    }
}
