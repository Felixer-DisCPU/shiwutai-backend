package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
    List<KnowledgePoint> findByOpenidAndSubject(String openid, String subject);

    List<KnowledgePoint> findByOpenid(String openid);

    Optional<KnowledgePoint> findByOpenidAndId(String openid, Long id);

    long countByOpenid(String openid);
}
