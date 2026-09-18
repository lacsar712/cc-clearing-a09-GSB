package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class ReconciliationRow {
    private final String memberId;
    private final BigDecimal systemNetAmount;
    private final BigDecimal reportedAmount;
    private final BigDecimal difference;
    private final ReconciliationStatus status;

    public ReconciliationRow(
            String memberId,
            BigDecimal systemNetAmount,
            BigDecimal reportedAmount,
            BigDecimal difference,
            ReconciliationStatus status) {
        this.memberId = Objects.requireNonNull(memberId);
        this.systemNetAmount = systemNetAmount;
        this.reportedAmount = reportedAmount;
        this.difference = difference;
        this.status = Objects.requireNonNull(status);
    }

    public static ReconciliationRow of(String memberId, BigDecimal systemNetAmount, BigDecimal reportedAmount) {
        if (systemNetAmount != null && reportedAmount != null) {
            BigDecimal difference = reportedAmount.subtract(systemNetAmount).setScale(8, RoundingMode.HALF_UP);
            ReconciliationStatus status = difference.compareTo(BigDecimal.ZERO) == 0
                    ? ReconciliationStatus.MATCHED
                    : ReconciliationStatus.DIFF;
            return new ReconciliationRow(memberId, systemNetAmount, reportedAmount, difference, status);
        }
        if (systemNetAmount != null) {
            return new ReconciliationRow(memberId, systemNetAmount, null, null, ReconciliationStatus.MISSING_RECEIPT);
        }
        return new ReconciliationRow(memberId, null, reportedAmount, null, ReconciliationStatus.EXTRA_RECEIPT);
    }

    public String getMemberId() {
        return memberId;
    }

    public BigDecimal getSystemNetAmount() {
        return systemNetAmount;
    }

    public BigDecimal getReportedAmount() {
        return reportedAmount;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }
}
