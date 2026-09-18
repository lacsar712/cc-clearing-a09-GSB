package com.clearing.netting.domain.model;

public enum ReconciliationStatus {
    MATCHED,
    DIFF,
    MISSING_RECEIPT,
    EXTRA_RECEIPT
}
