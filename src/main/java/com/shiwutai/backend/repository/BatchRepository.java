package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByOpenid(String openid);

    Optional<Batch> findByOpenidAndId(String openid, Long id);
}
