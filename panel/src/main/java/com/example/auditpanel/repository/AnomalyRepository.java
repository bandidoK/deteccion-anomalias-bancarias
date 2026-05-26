package com.example.auditpanel.repository;

import com.example.auditpanel.model.AnomalyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<AnomalyEntity, Long> {
    List<AnomalyEntity> findByIdGreaterThanOrderByIdAsc(Long id);
}
