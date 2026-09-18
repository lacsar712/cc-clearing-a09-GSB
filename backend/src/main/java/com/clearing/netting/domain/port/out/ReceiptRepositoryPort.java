package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.ReceiptRecord;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepositoryPort {
    ReceiptRecord save(ReceiptRecord receipt);

    List<ReceiptRecord> findByRunId(String runId);

    Optional<ReceiptRecord> findByRunIdAndMemberId(String runId, String memberId);
}
