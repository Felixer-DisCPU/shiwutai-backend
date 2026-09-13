package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.Correction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {
    List<Correction> findByOpenidAndBatchId(String openid, Long batchId);

    List<Correction> findByOpenidOrderByCreatedAtDesc(String openid);
}
