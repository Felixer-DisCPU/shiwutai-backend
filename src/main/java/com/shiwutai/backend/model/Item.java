package com.shiwutai.backend.model;

import com.shiwutai.backend.config.StringListConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 题级结构化记录（F3 识别 / F4 校正产出）。
 * result 取值：正确 / 错误 / 部分正确 / 未作答
 */
@Entity
@Table(name = "items", indexes = {
        @Index(name = "idx_item_openid_batch_confirmed", columnList = "openid,batchId,confirmed"),
        @Index(name = "idx_item_openid_sheet", columnList = "openid,sheetId"),
        @Index(name = "idx_item_openid_student", columnList = "openid,batchId,studentName,confirmed")
})
@Data
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;

    private Long batchId;
    private Long sheetId;
    private String studentName;

    private String qno;         // 题号

    @Column(length = 2000)
    private String answerText;  // 学生作答文本（保留公式与步骤）

    private String result;      // 正确 / 错误 / 部分正确 / 未作答

    private String pointId;     // 主知识点

    @Convert(converter = StringListConverter.class)
    private List<String> pointIds = new ArrayList<>(); // Top-3 候选知识点

    private String errorType;   // 四类错误之一（正确时为空）

    private Double confidence;  // 识别置信度 0~1

    private Boolean confirmed;  // 是否已确认入库

    @Column(length = 2000)
    private String aiNote;

    private Long createdAt;
}
