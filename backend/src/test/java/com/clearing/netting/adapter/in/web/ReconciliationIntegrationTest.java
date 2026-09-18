package com.clearing.netting.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全链路验收：会员/义务 -> 轧差 -> 回执录入 -> 对账 -> 改坏一条回执 -> 差异标出。
 * 使用 H2（PostgreSQL 兼容模式），无需外部数据库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconciliationIntegrationTest {

    private static final String SETTLE_DATE = LocalDate.of(2026, 9, 10).toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createMember(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("memberId").asText();
    }

    private void createObligation(String token, String payer, String payee, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payerMemberId", payer);
        body.put("payeeMemberId", payee);
        body.put("currency", "USD");
        body.put("amount", new BigDecimal(amount));
        body.put("tradeDate", SETTLE_DATE);
        body.put("settleDate", SETTLE_DATE);
        mockMvc.perform(post("/api/obligations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private MvcResult executeNetting(String token) throws Exception {
        return mockMvc.perform(post("/api/netting-runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settleDate\":\"" + SETTLE_DATE + "\",\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult saveReceipts(String token, String runId, List<Map<String, Object>> items) throws Exception {
        return mockMvc.perform(put("/api/reconciliations/{runId}/receipts", runId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andReturn();
    }

    @Test
    void corruptedReceiptIsFlaggedAndViewerCannotEdit() throws Exception {
        String operator = login("operator", "op123456");
        String viewer = login("viewer", "view123456");

        String a = createMember(operator, "Alpha Bank");
        String b = createMember(operator, "Beta Securities");
        String c = createMember(operator, "Gamma Clearing");

        createObligation(operator, a, b, "100000.00000000");
        createObligation(operator, b, c, "60000.00000000");
        createObligation(operator, c, a, "40000.00000000");
        createObligation(operator, a, c, "25000.00000000");

        JsonNode netting = objectMapper.readTree(executeNetting(operator).getResponse().getContentAsString());
        assertEquals("COMPLETED", netting.get("run").get("status").asText());
        String runId = netting.get("run").get("runId").asText();

        JsonNode positions = netting.get("positions");
        assertEquals(3, positions.size());

        // 1) 按系统净头寸录入回执，初始应当全部一致
        List<Map<String, Object>> matchedItems = new ArrayList<>();
        String victim = null;
        BigDecimal victimSystemNet = null;
        for (JsonNode p : positions) {
            String memberId = p.get("memberId").asText();
            BigDecimal net = p.get("netAmount").decimalValue();
            matchedItems.add(Map.of("memberId", memberId, "netAmount", net));
            if (victim == null) {
                victim = memberId;
                victimSystemNet = net;
            }
        }

        JsonNode savedAll = objectMapper.readTree(saveReceipts(operator, runId, matchedItems)
                .getResponse().getContentAsString());
        assertEquals(3, savedAll.get("matchedCount").asInt());
        assertEquals(0, savedAll.get("mismatchCount").asInt());
        assertEquals(0, savedAll.get("missingCount").asInt());
        assertTrue(savedAll.get("allMatched").asBoolean());

        // 2) 只读账号不能录入/修改回执 -> 403
        MvcResult forbidden = saveReceipts(viewer, runId, matchedItems);
        assertEquals(403, forbidden.getResponse().getStatus());

        // 只读账号可以查看对账结果 -> 200
        mockMvc.perform(get("/api/reconciliations/{runId}", runId)
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk());

        // 3) 改坏一条回执（某会员净额被改成与系统净头寸不同的值）
        BigDecimal corrupted = victimSystemNet.add(new BigDecimal("1234.56"));
        List<Map<String, Object>> corruptedItems = new ArrayList<>();
        for (JsonNode p : positions) {
            String memberId = p.get("memberId").asText();
            BigDecimal net = p.get("netAmount").decimalValue();
            if (memberId.equals(victim)) {
                corruptedItems.add(Map.of("memberId", memberId, "netAmount", corrupted));
            } else {
                corruptedItems.add(Map.of("memberId", memberId, "netAmount", net));
            }
        }

        JsonNode afterCorruption = objectMapper.readTree(saveReceipts(operator, runId, corruptedItems)
                .getResponse().getContentAsString());

        assertEquals(1, afterCorruption.get("mismatchCount").asInt(), "应当恰好标出 1 条差异");
        assertEquals(2, afterCorruption.get("matchedCount").asInt());
        assertEquals(0, afterCorruption.get("missingCount").asInt());

        JsonNode flagged = null;
        for (JsonNode row : afterCorruption.get("rows")) {
            if (row.get("memberId").asText().equals(victim)) {
                flagged = row;
            } else {
                assertEquals("MATCHED", row.get("status").asText());
                assertEquals(0, row.get("difference").decimalValue().compareTo(BigDecimal.ZERO));
            }
        }
        assertEquals("MISMATCH", flagged.get("status").asText());
        assertEquals(false, flagged.get("matched").asBoolean());
        assertNotEquals(0, flagged.get("difference").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0,
                flagged.get("difference").decimalValue().compareTo(new BigDecimal("1234.56000000")));

        // 4) 重新读取，差异被持久化、仍被标出
        JsonNode reloaded = objectMapper.readTree(mockMvc.perform(get("/api/reconciliations/{runId}", runId)
                        .header("Authorization", "Bearer " + operator))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, reloaded.get("mismatchCount").asInt());
    }
}
