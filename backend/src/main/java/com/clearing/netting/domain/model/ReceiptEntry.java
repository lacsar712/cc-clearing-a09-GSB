package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ReceiptEntry {
    private final String receiptId;
    private final String runId;
    private final String memberId;
    private final String currency;
    private final BigDecimal reportedAmount;
    private final String createdBy;
    private final Instant createdAt;

    public ReceiptEntry(
            String receiptId,
            String runId,
            String memberId,
            String currency,
            BigDecimal reportedAmount,
            String createdBy,
            Instant createdAt) {
        this.receiptId = Objects.requireNonNull(receiptId);
        this.runId = Objects.requireNonNull(runId);
        this.memberId = Objects.requireNonNull(memberId);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.reportedAmount = Objects.requireNonNull(reportedAmount).setScale(8, RoundingMode.HALF_UP);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static ReceiptEntry of(String runId, String memberId, String currency, BigDecimal reportedAmount, String createdBy) {
        return new ReceiptEntry(
                UUID.randomUUID().toString(),
                runId,
                memberId,
                currency,
                reportedAmount,
                createdBy,
                Instant.now());
    }

    public ReceiptEntry withAmount(BigDecimal newAmount, String operator) {
        return new ReceiptEntry(receiptId, runId, memberId, currency, newAmount, operator, Instant.now());
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

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getReportedAmount() {
        return reportedAmount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
