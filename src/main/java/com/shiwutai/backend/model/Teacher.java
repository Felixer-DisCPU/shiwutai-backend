package com.shiwutai.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teachers")
@Data
@NoArgsConstructor
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String openid;

    private String name;
    private String school;
    private String subject;
    private String grade;

    @Column(length = 4000)
    private String settingsJson;

    private Long updatedAt; // epoch millis
}
