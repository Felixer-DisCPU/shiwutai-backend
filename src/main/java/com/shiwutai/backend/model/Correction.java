package com.shiwutai.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "corrections")
@Data
@NoArgsConstructor
public class Correction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;
    private Long itemId;
    private Long sheetId;
    private Long batchId;

    private String field;     // 被校正的字段（result/pointId/errorType）

    @Column(length = 2000)
    private String beforeVal;

    @Column(length = 2000)
    private String afterVal;

    private Long createdAt;   // epoch millis
}
