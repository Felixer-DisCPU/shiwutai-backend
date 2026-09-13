package com.shiwutai.backend.model;

import com.shiwutai.backend.config.StringListConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sheets")
@Data
@NoArgsConstructor
public class Sheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;

    private Long batchId;
    private String studentName;

    @Convert(converter = StringListConverter.class)
    private List<String> images = new ArrayList<>(); // 作业影像 URL 列表

    @Column(length = 4000)
    private String rawText;     // 识别得到的原始文本

    private String status;      // 待识别 / 已识别 / 已确认

    private String aiModel;     // 识别所用模型
    private Integer aiCostMs;   // 识别耗时

    private Long createdAt;
}
