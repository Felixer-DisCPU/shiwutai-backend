package com.shiwutai.backend.model;

import com.shiwutai.backend.config.StringListConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "batches")
@Data
@NoArgsConstructor
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;

    private String title;       // 批次名称（如「第三章 二次函数 单元测验」）
    private String className;   // 班级
    private String subject;     // 学科
    private String type;        // 类型（单元测验/月考/作业…）
    private String examDate;    // 考试/布置日期

    @Convert(converter = StringListConverter.class)
    private List<String> pointScope = new ArrayList<>(); // 本次考查知识点范围

    private String status;      // 录入中 / 识别中 / 待校正 / 已确认

    private Integer entered;    // 已录入份数
    private Integer confirmed;  // 已确认份数

    @Convert(converter = StringListConverter.class)
    private List<String> teachingCovered = new ArrayList<>(); // 已讲评覆盖的知识点

    private Long createdAt;     // epoch millis
}
