package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.ReceiptRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRecordJpaRepository extends JpaRepository<ReceiptRecordJpaEntity, String> {
    List<ReceiptRecordJpaEntity> findByRunId(String runId);

    Optional<ReceiptRecordJpaEntity> findByRunIdAndMemberId(String runId, String memberId);
}
