package com.shiwutai.backend.repository;

import com.shiwutai.backend.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByOpenid(String openid);
}
