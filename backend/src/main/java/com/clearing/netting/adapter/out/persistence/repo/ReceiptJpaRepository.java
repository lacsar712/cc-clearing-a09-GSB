package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.ReceiptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptJpaRepository extends JpaRepository<ReceiptJpaEntity, String> {
    List<ReceiptJpaEntity> findByRunId(String runId);

    Optional<ReceiptJpaEntity> findByRunIdAndMemberId(String runId, String memberId);

    void deleteByRunIdAndMemberId(String runId, String memberId);
}
