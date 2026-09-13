package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Item;
import com.shiwutai.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    private final ItemRepository itemRepo;
    private final ReportService reportService;

    public ExportService(ItemRepository itemRepo, ReportService reportService) {
        this.itemRepo = itemRepo;
        this.reportService = reportService;
    }

    public Map<String, Object> csv(String openid, Long batchId) {
        List<Item> its = itemRepo.findByOpenidAndBatchIdAndConfirmedTrue(openid, batchId);
        StringBuilder sb = new StringBuilder();
        sb.append("学生,题号,作答,正误,知识点,错误类型,置信度\n");
        for (Item i : its) {
            sb.append(csvCell(i.getStudentName())).append(',')
                    .append(csvCell(i.getQno())).append(',')
                    .append(csvCell(i.getAnswerText())).append(',')
                    .append(csvCell(i.getResult())).append(',')
                    .append(csvCell(i.getPointId())).append(',')
                    .append(csvCell(i.getErrorType())).append(',')
                    .append(i.getConfidence() == null ? "" : i.getConfidence())
                    .append('\n');
        }
        Map<String, Object> r = new LinkedHashMap<>();
        // UTF-8 BOM，Excel 直接打开不乱码
        r.put("csv", "\uFEFF" + sb);
        r.put("filename", "batch_" + batchId + ".csv");
        return r;
    }

    public Map<String, Object> text(String openid, Long batchId, String type) {
        if ("teaching".equals(type)) return reportService.teaching(openid, batchId);
        return reportService.report(openid, batchId);
    }

    private String csvCell(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
