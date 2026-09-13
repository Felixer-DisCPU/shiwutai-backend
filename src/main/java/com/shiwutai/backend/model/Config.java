package com.shiwutai.backend.model;

import jakarta.persistence.*;

/**
 * 全局键值配置（不分 openid）。前端"AI 配置"页与 OOBE 写入，
 * LlmService 在每次调用时读取，覆盖 application.yml / 环境变量中的默认值。
 */
@Entity
@Table(name = "config_kv")
public class Config {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String k;

    @Column(length = 2000)
    private String v;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getK() { return k; }
    public void setK(String k) { this.k = k; }

    public String getV() { return v; }
    public void setV(String v) { this.v = v; }
}
