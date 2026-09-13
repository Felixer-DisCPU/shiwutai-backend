package com.shiwutai.backend.model;

import com.shiwutai.backend.config.StringListConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_points")
@Data
@NoArgsConstructor
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;
    private String subject;   // 学科
    private String chapter;   // 章节
    private String name;      // 知识点名

    @Convert(converter = StringListConverter.class)
    private List<String> aliases = new ArrayList<>(); // 别名/同义表达

    private Long createdAt;
}
