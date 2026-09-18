package com.clearing.netting.domain.service;

import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.ReceiptEntry;
import com.clearing.netting.domain.model.ReconciliationRow;
import com.clearing.netting.domain.model.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReconciliationServiceTest {

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService();
    }

    @Test
    void matchedWhenReceiptEqualsSystemNet() {
        List<NetPosition> positions = List.of(
                NetPosition.of("run-1", "A", "USD", new BigDecimal("-60.5")),
                NetPosition.of("run-1", "B", "USD", new BigDecimal("60.5")));
        List<ReceiptEntry> receipts = List.of(
                receipt("run-1", "A", "-60.50000000"),
                receipt("run-1", "B", "60.5"));

        Map<String, ReconciliationRow> rows = byMember(service.reconcile(positions, receipts));

        assertEquals(ReconciliationStatus.MATCHED, rows.get("A").getStatus());
        assertEquals(ReconciliationStatus.MATCHED, rows.get("B").getStatus());
        assertEquals(0, rows.get("A").getDifference().compareTo(BigDecimal.ZERO));
    }

    @Test
    void flagsDiffWhenReceiptBroken() {
        List<NetPosition> positions = List.of(NetPosition.of("run-1", "A", "USD", new BigDecimal("-60")));
        List<ReceiptEntry> receipts = List.of(receipt("run-1", "A", "-59"));

        Map<String, ReconciliationRow> rows = byMember(service.reconcile(positions, receipts));

        ReconciliationRow row = rows.get("A");
        assertEquals(ReconciliationStatus.DIFF, row.getStatus());
        assertEquals(0, row.getDifference().compareTo(new BigDecimal("1.00000000")));
    }

    @Test
    void flagsMissingAndExtraReceipts() {
        List<NetPosition> positions = List.of(NetPosition.of("run-1", "A", "USD", new BigDecimal("10")));
        List<ReceiptEntry> receipts = List.of(receipt("run-1", "B", "10"));

        Map<String, ReconciliationRow> rows = byMember(service.reconcile(positions, receipts));

        assertEquals(ReconciliationStatus.MISSING_RECEIPT, rows.get("A").getStatus());
        assertNull(rows.get("A").getReportedAmount());
        assertEquals(ReconciliationStatus.EXTRA_RECEIPT, rows.get("B").getStatus());
        assertNull(rows.get("B").getSystemNetAmount());
    }

    private ReceiptEntry receipt(String runId, String memberId, String amount) {
        return new ReceiptEntry(
                java.util.UUID.randomUUID().toString(),
                runId,
                memberId,
                "USD",
                new BigDecimal(amount),
                "operator",
                Instant.now());
    }

    private Map<String, ReconciliationRow> byMember(List<ReconciliationRow> rows) {
        return rows.stream().collect(Collectors.toMap(ReconciliationRow::getMemberId, Function.identity()));
    }
}
