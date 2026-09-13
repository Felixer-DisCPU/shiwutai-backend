package com.shiwutai.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批次确认时写入的知识点掌握度快照（F5 输入）。
 * kps 以 JSON 字符串保存：[{"name":..,"rate":..}, ...]
 */
@Entity
@Table(name = "point_stats")
@Data
@NoArgsConstructor
public class PointStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;
    private Long batchId;

    private Integer classMastery; // 班级掌握度 %
    private Integer kpCount;      // 知识点数

    @Column(length = 4000)
    private String kps;           // JSON: [{name, rate}]

    private Long createdAt;
}
