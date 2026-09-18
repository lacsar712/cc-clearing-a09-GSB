package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.ReconciliationApplicationService;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.ReconciliationRow;
import com.clearing.netting.domain.model.ReconciliationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/netting-runs")
public class ReconciliationController {

    private final ReconciliationApplicationService reconciliationService;

    public ReconciliationController(ReconciliationApplicationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/{runId}/reconciliation")
    public ReconciliationResponse get(@PathVariable("runId") String runId) {
        AuthContext.require();
        return ReconciliationResponse.from(reconciliationService.getReconciliation(runId));
    }

    @PostMapping("/{runId}/receipts")
    public ReconciliationResponse record(
            @PathVariable("runId") String runId,
            @Valid @RequestBody ReceiptRequest request) {
        String operator = AuthContext.requireOperator().username();
        return ReconciliationResponse.from(
                reconciliationService.recordReceipt(runId, request.memberId(), request.reportedAmount(), operator));
    }

    @DeleteMapping("/{runId}/receipts/{memberId}")
    public ReconciliationResponse delete(
            @PathVariable("runId") String runId,
            @PathVariable("memberId") String memberId) {
        AuthContext.requireOperator();
        return ReconciliationResponse.from(reconciliationService.deleteReceipt(runId, memberId));
    }

    public record ReceiptRequest(@NotBlank String memberId, @NotNull BigDecimal reportedAmount) {
    }

    public record RowResponse(
            String memberId,
            BigDecimal systemNetAmount,
            BigDecimal reportedAmount,
            BigDecimal difference,
            ReconciliationStatus status) {
        static RowResponse from(ReconciliationRow r) {
            return new RowResponse(
                    r.getMemberId(),
                    r.getSystemNetAmount(),
                    r.getReportedAmount(),
                    r.getDifference(),
                    r.getStatus());
        }
    }

    public record ReconciliationResponse(
            String runId,
            LocalDate settleDate,
            String currency,
            NettingRunStatus runStatus,
            List<RowResponse> rows,
            long matchedCount,
            long diffCount,
            long missingCount,
            long extraCount) {
        static ReconciliationResponse from(ReconciliationApplicationService.ReconciliationResult result) {
            List<ReconciliationRow> rows = result.rows();
            return new ReconciliationResponse(
                    result.run().getRunId(),
                    result.run().getSettleDate(),
                    result.run().getCurrency(),
                    result.run().getStatus(),
                    rows.stream().map(RowResponse::from).collect(Collectors.toList()),
                    count(rows, ReconciliationStatus.MATCHED),
                    count(rows, ReconciliationStatus.DIFF),
                    count(rows, ReconciliationStatus.MISSING_RECEIPT),
                    count(rows, ReconciliationStatus.EXTRA_RECEIPT));
        }

        private static long count(List<ReconciliationRow> rows, ReconciliationStatus status) {
            return rows.stream().filter(r -> r.getStatus() == status).count();
        }
    }
}
