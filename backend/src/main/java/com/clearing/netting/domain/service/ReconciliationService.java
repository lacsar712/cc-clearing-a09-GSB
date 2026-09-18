package com.clearing.netting.domain.service;

import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.ReceiptEntry;
import com.clearing.netting.domain.model.ReconciliationRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ReconciliationService {

    public List<ReconciliationRow> reconcile(List<NetPosition> positions, List<ReceiptEntry> receipts) {
        Map<String, BigDecimal> systemByMember = new HashMap<>();
        for (NetPosition p : positions) {
            systemByMember.merge(p.getMemberId(), p.getNetAmount(), BigDecimal::add);
        }
        Map<String, BigDecimal> reportedByMember = new HashMap<>();
        for (ReceiptEntry r : receipts) {
            reportedByMember.put(r.getMemberId(), r.getReportedAmount());
        }

        Set<String> memberIds = new TreeSet<>(systemByMember.keySet());
        memberIds.addAll(reportedByMember.keySet());

        List<ReconciliationRow> rows = new ArrayList<>();
        for (String memberId : memberIds) {
            rows.add(ReconciliationRow.of(memberId, systemByMember.get(memberId), reportedByMember.get(memberId)));
        }
        return rows;
    }
}
