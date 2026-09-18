package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.ReceiptEntry;
import com.clearing.netting.domain.model.ReconciliationRow;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ReceiptRepositoryPort;
import com.clearing.netting.domain.service.ReconciliationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ReconciliationApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final ReceiptRepositoryPort receiptRepository;
    private final MemberRepositoryPort memberRepository;
    private final ReconciliationService reconciliationService;

    public ReconciliationApplicationService(
            NettingRunRepositoryPort runRepository,
            NetPositionRepositoryPort positionRepository,
            ReceiptRepositoryPort receiptRepository,
            MemberRepositoryPort memberRepository) {
        this.runRepository = runRepository;
        this.positionRepository = positionRepository;
        this.receiptRepository = receiptRepository;
        this.memberRepository = memberRepository;
        this.reconciliationService = new ReconciliationService();
    }

    @Transactional(readOnly = true)
    public ReconciliationResult getReconciliation(String runId) {
        NettingRun run = getRun(runId);
        List<ReconciliationRow> rows = reconciliationService.reconcile(
                positionRepository.findByRunId(runId),
                receiptRepository.findByRunId(runId));
        return new ReconciliationResult(run, rows);
    }

    @Transactional
    public ReconciliationResult recordReceipt(String runId, String memberId, BigDecimal reportedAmount, String operator) {
        NettingRun run = getRun(runId);
        if (run.getStatus() != NettingRunStatus.COMPLETED) {
            throw new DomainException("INVALID_STATE", "only COMPLETED runs can be reconciled");
        }
        if (reportedAmount == null) {
            throw new DomainException("INVALID_AMOUNT", "reportedAmount is required");
        }
        memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));

        ReceiptEntry receipt = receiptRepository.findByRunIdAndMemberId(runId, memberId)
                .map(existing -> existing.withAmount(reportedAmount, operator))
                .orElseGet(() -> ReceiptEntry.of(runId, memberId, run.getCurrency(), reportedAmount, operator));
        receiptRepository.save(receipt);

        return getReconciliation(runId);
    }

    @Transactional
    public ReconciliationResult deleteReceipt(String runId, String memberId) {
        getRun(runId);
        receiptRepository.deleteByRunIdAndMemberId(runId, memberId);
        return getReconciliation(runId);
    }

    private NettingRun getRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new DomainException("RUN_NOT_FOUND", "netting run not found: " + runId));
    }

    public record ReconciliationResult(NettingRun run, List<ReconciliationRow> rows) {
    }
}
