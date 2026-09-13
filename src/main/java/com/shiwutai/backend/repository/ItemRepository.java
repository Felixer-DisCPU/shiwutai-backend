package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByOpenidAndBatchId(String openid, Long batchId);

    List<Item> findByOpenidAndBatchIdAndConfirmedTrue(String openid, Long batchId);

    List<Item> findByOpenidAndBatchIdAndConfirmedFalse(String openid, Long batchId);

    List<Item> findByOpenidAndBatchIdAndStudentNameAndConfirmedTrue(String openid, Long batchId, String studentName);

    List<Item> findByOpenidAndSheetId(String openid, Long sheetId);

    List<Item> findByOpenidAndBatchIdAndPointIdAndConfirmedTrue(String openid, Long batchId, String pointId);

    List<Item> findByOpenidAndBatchIdAndPointIdAndConfirmedFalse(String openid, Long batchId, String pointId);

    List<Item> findByOpenidAndConfirmedTrue(String openid);

    Optional<Item> findByOpenidAndId(String openid, Long id);
}
