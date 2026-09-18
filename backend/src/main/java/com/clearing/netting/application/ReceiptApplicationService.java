package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.ReceiptRecord;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ReceiptRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回执对账：将会员录入的回执净额与系统轧差净头寸按会员逐一比对。
 */
@Service
public class ReceiptApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final ReceiptRepositoryPort receiptRepository;
    private final MemberRepositoryPort memberRepository;

    public ReceiptApplicationService(
            NettingRunRepositoryPort runRepository,
            NetPositionRepositoryPort positionRepository,
            ReceiptRepositoryPort receiptRepository,
            MemberRepositoryPort memberRepository) {
        this.runRepository = runRepository;
        this.positionRepository = positionRepository;
        this.receiptRepository = receiptRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Reconciliation getReconciliation(String runId) {
        NettingRun run = requireRun(runId);
        List<NetPosition> positions = positionRepository.findByRunId(runId);
        Map<String, ReceiptRecord> receipts = loadReceipts(runId);
        Map<String, String> names = loadNames(positions, receipts.values());
        return build(run, positions, receipts, names);
    }

    @Transactional
    public Reconciliation saveReceipts(String runId, List<ReceiptInput> inputs) {
        NettingRun run = requireRun(runId);
        List<NetPosition> positions = positionRepository.findByRunId(runId);
        Map<String, NetPosition> positionByMember = new HashMap<>();
        for (NetPosition p : positions) {
            positionByMember.put(p.getMemberId(), p);
        }

        for (ReceiptInput input : inputs) {
            if (input == null || input.memberId() == null || input.memberId().isBlank()) {
                throw new DomainException("VALIDATION_ERROR", "memberId is required");
            }
            if (input.netAmount() == null) {
                throw new DomainException("VALIDATION_ERROR", "netAmount is required for member " + input.memberId());
            }
            if (!positionByMember.containsKey(input.memberId())) {
                throw new DomainException("MEMBER_NOT_IN_RUN", "member has no net position in run: " + input.memberId());
            }
            ReceiptRecord existing = receiptRepository.findByRunIdAndMemberId(runId, input.memberId()).orElse(null);
            if (existing == null) {
                receiptRepository.save(ReceiptRecord.create(runId, input.memberId(), input.netAmount()));
            } else {
                existing.changeAmount(input.netAmount());
                receiptRepository.save(existing);
            }
        }

        Map<String, ReceiptRecord> receipts = loadReceipts(runId);
        Map<String, String> names = loadNames(positions, receipts.values());
        return build(run, positions, receipts, names);
    }

    private NettingRun requireRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new DomainException("RUN_NOT_FOUND", "netting run not found: " + runId));
    }

    private Map<String, ReceiptRecord> loadReceipts(String runId) {
        Map<String, ReceiptRecord> map = new HashMap<>();
        for (ReceiptRecord r : receiptRepository.findByRunId(runId)) {
            map.put(r.getMemberId(), r);
        }
        return map;
    }

    private Map<String, String> loadNames(List<NetPosition> positions, Iterable<ReceiptRecord> receipts) {
        List<String> ids = new ArrayList<>();
        for (NetPosition p : positions) {
            ids.add(p.getMemberId());
        }
        for (ReceiptRecord r : receipts) {
            ids.add(r.getMemberId());
        }
        Map<String, String> names = new HashMap<>();
        for (Member m : memberRepository.findByIds(ids)) {
            names.put(m.getMemberId(), m.getName());
        }
        return names;
    }

    private Reconciliation build(
            NettingRun run,
            List<NetPosition> positions,
            Map<String, ReceiptRecord> receipts,
            Map<String, String> names) {

        List<ReconciliationRow> rows = new ArrayList<>();
        Map<String, NetPosition> positionByMember = new HashMap<>();
        for (NetPosition p : positions) {
            positionByMember.put(p.getMemberId(), p);
        }
        positionByMember.keySet().stream().sorted().forEach(memberId -> {
            NetPosition position = positionByMember.get(memberId);
            ReceiptRecord receipt = receipts.get(memberId);
            BigDecimal systemNet = position.getNetAmount();
            BigDecimal receiptNet = receipt == null ? null : receipt.getNetAmount();
            BigDecimal difference = receiptNet == null ? null : receiptNet.subtract(systemNet);
            boolean matched = difference != null && difference.compareTo(BigDecimal.ZERO) == 0;
            String status = receiptNet == null ? "MISSING" : matched ? "MATCHED" : "MISMATCH";
            rows.add(new ReconciliationRow(
                    memberId,
                    names.get(memberId),
                    position.getCurrency(),
                    systemNet,
                    receiptNet,
                    difference,
                    matched,
                    status,
                    receipt == null ? null : receipt.getUpdatedAt()));
        });

        long matchedCount = rows.stream().filter(r -> r.matched()).count();
        long mismatchCount = rows.stream().filter(r -> "MISMATCH".equals(r.status())).count();
        long missingCount = rows.stream().filter(r -> "MISSING".equals(r.status())).count();
        boolean allMatched = !rows.isEmpty() && mismatchCount == 0 && missingCount == 0;

        return new Reconciliation(
                run.getRunId(),
                run.getSettleDate() == null ? null : run.getSettleDate().toString(),
                run.getCurrency(),
                run.getStatus().name(),
                rows,
                rows.size(),
                matchedCount,
                mismatchCount,
                missingCount,
                allMatched);
    }

    public record ReceiptInput(String memberId, BigDecimal netAmount) {
    }

    public record ReconciliationRow(
            String memberId,
            String memberName,
            String currency,
            BigDecimal systemNetAmount,
            BigDecimal receiptNetAmount,
            BigDecimal difference,
            boolean matched,
            String status,
            java.time.Instant updatedAt) {
    }

    public record Reconciliation(
            String runId,
            String settleDate,
            String currency,
            String runStatus,
            List<ReconciliationRow> rows,
            long totalMembers,
            long matchedCount,
            long mismatchCount,
            long missingCount,
            boolean allMatched) {
    }
}
