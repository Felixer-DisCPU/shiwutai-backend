package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.Sheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SheetRepository extends JpaRepository<Sheet, Long> {
    List<Sheet> findByOpenidAndBatchId(String openid, Long batchId);

    List<Sheet> findByOpenidAndBatchIdAndStudentName(String openid, Long batchId, String studentName);

    List<Sheet> findByOpenidAndBatchIdAndStatus(String openid, Long batchId, String status);

    Optional<Sheet> findByOpenidAndId(String openid, Long id);

    long countByOpenidAndBatchIdAndStatus(String openid, Long batchId, String status);
}
