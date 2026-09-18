package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 会员回执净额：对某轧差批次（runId）下某会员（memberId）实际确认的净额。
 * 口径与系统净头寸一致：正数=应收，负数=应付。
 */
public class ReceiptRecord {
    private final String receiptId;
    private final String runId;
    private final String memberId;
    private BigDecimal netAmount;
    private final Instant createdAt;
    private Instant updatedAt;

    public ReceiptRecord(
            String receiptId,
            String runId,
            String memberId,
            BigDecimal netAmount,
            Instant createdAt,
            Instant updatedAt) {
        this.receiptId = Objects.requireNonNull(receiptId);
        this.runId = Objects.requireNonNull(runId);
        this.memberId = Objects.requireNonNull(memberId);
        this.netAmount = normalize(netAmount);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static ReceiptRecord create(String runId, String memberId, BigDecimal netAmount) {
        Instant now = Instant.now();
        return new ReceiptRecord(UUID.randomUUID().toString(), runId, memberId, netAmount, now, now);
    }

    public void changeAmount(BigDecimal amount) {
        this.netAmount = normalize(amount);
        this.updatedAt = Instant.now();
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return Objects.requireNonNull(amount, "netAmount").setScale(8, RoundingMode.HALF_UP);
    }

    public String getReceiptId() {
        return receiptId;
    }

    public String getRunId() {
        return runId;
    }

    public String getMemberId() {
        return memberId;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
