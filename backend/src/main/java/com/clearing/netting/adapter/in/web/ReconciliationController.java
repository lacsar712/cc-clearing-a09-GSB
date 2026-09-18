package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.ReceiptApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {

    private final ReceiptApplicationService receiptService;

    public ReconciliationController(ReceiptApplicationService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/{runId}")
    public ReceiptApplicationService.Reconciliation get(@PathVariable("runId") String runId) {
        AuthContext.require();
        return receiptService.getReconciliation(runId);
    }

    @PutMapping("/{runId}/receipts")
    public ReceiptApplicationService.Reconciliation saveReceipts(
            @PathVariable("runId") String runId,
            @Valid @RequestBody SaveReceiptsRequest request) {
        AuthContext.requireOperator();
        List<ReceiptApplicationService.ReceiptInput> inputs = request.items().stream()
                .map(i -> new ReceiptApplicationService.ReceiptInput(i.memberId(), i.netAmount()))
                .toList();
        return receiptService.saveReceipts(runId, inputs);
    }

    public record SaveReceiptsRequest(@NotEmpty @Valid List<ReceiptItem> items) {
    }

    public record ReceiptItem(
            @NotBlank String memberId,
            @NotNull BigDecimal netAmount) {
    }
}
