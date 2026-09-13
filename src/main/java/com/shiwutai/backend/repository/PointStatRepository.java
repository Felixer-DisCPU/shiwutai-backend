package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.PointStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointStatRepository extends JpaRepository<PointStat, Long> {
    Optional<PointStat> findByOpenidAndBatchId(String openid, Long batchId);

    void deleteByOpenidAndBatchId(String openid, Long batchId);
}
