package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.Config;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigRepository extends JpaRepository<Config, Long> {
    Optional<Config> findByK(String k);
    List<Config> findByKIn(List<String> ks);
}
