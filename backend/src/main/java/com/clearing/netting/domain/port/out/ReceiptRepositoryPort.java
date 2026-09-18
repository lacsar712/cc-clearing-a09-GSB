package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.ReceiptEntry;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepositoryPort {
    ReceiptEntry save(ReceiptEntry receipt);

    Optional<ReceiptEntry> findByRunIdAndMemberId(String runId, String memberId);

    List<ReceiptEntry> findByRunId(String runId);

    void deleteByRunIdAndMemberId(String runId, String memberId);
}
