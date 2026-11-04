package com.builtbyjuls.clearing;

import com.builtbyjuls.clearing.ledger.ClearedTrade;
import com.builtbyjuls.clearing.ledger.ClearedTradeRepository;
import com.builtbyjuls.clearing.ledger.ClearingAuditLog;
import com.builtbyjuls.clearing.ledger.ClearingAuditLogRepository;
import com.builtbyjuls.clearing.ledger.ClearingMember;
import com.builtbyjuls.clearing.ledger.ClearingMemberRepository;
import com.builtbyjuls.clearing.ledger.MarginCallRecord;
import com.builtbyjuls.clearing.ledger.MarginCallRecordRepository;
import com.builtbyjuls.clearing.messaging.ClearingAmqpConfig;
import com.builtbyjuls.clearing.messaging.MarginCallEvent;
import com.builtbyjuls.clearing.messaging.TradeSubmissionMessage;
import com.builtbyjuls.clearing.trade.ClearedTradePositionDto;
import com.builtbyjuls.clearing.trade.TradeClearingService;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.builtbyjuls.compactor.model.SliceCompactionReportDto;
import com.builtbyjuls.compactor.model.SpringStereotype;
import com.builtbyjuls.compactor.model.StereotypeBudgetRowDto;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class TradeClearingDomainIntegrationTest extends ClearingTestContainersBase {

    @Autowired
    private TradeClearingService tradeClearingService;

    @Autowired
    private ClearingMemberRepository clearingMemberRepository;

    @Autowired
    private ClearedTradeRepository clearedTradeRepository;

    @Autowired
    private MarginCallRecordRepository marginCallRecordRepository;

    @Autowired
    private ClearingAuditLogRepository clearingAuditLogRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private CompactionBudgetService compactionBudgetService;

    @Autowired
    private SpringSliceExpander springSliceExpander;

    @BeforeEach
    void seedClearingMembers() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        clearingAuditLogRepository.deleteAll();
        marginCallRecordRepository.deleteAll();
        clearedTradeRepository.deleteAll();
        amqpAdmin.purgeQueue(ClearingAmqpConfig.MARGIN_CALL_QUEUE, false);
        amqpAdmin.purgeQueue(ClearingAmqpConfig.TRADE_INBOUND_QUEUE, false);
        amqpAdmin.purgeQueue(ClearingAmqpConfig.TRADE_DLQ, false);

        if (clearingMemberRepository.findByMemberCode("MBR-GOLDMAN").isEmpty()) {
            clearingMemberRepository.save(ClearingMember.builder()
                    .memberCode("MBR-GOLDMAN")
                    .legalName("Goldman Sachs Clearing LLC")
                    .tier("TIER_1")
                    .initialMarginCollateral(new BigDecimal("10000000.0000"))
                    .active(true)
                    .build());
        }

        if (clearingMemberRepository.findByMemberCode("MBR-CITADEL").isEmpty()) {
            clearingMemberRepository.save(ClearingMember.builder()
                    .memberCode("MBR-CITADEL")
                    .legalName("Citadel Securities Clearing Ltd")
                    .tier("TIER_1")
                    .initialMarginCollateral(new BigDecimal("500000.0000"))
                    .active(true)
                    .build());
        }
    }

    @Test
    @DisplayName("Novate and clear trade within collateral persists trade and records audit events without margin call")
    void novateAndClearTradeWithinMarginCollateralSucceeds() {
        String tradeId = "TRD-GOLDMAN-001";
        TradeSubmissionMessage message = new TradeSubmissionMessage(
                tradeId,
                "MBR-GOLDMAN",
                "EUR/USD",
                "FX",
                new BigDecimal("1000000.0000"),
                LocalDate.now()
        );

        ClearedTradePositionDto position = tradeClearingService.novateAndClearTrade(message);

        assertThat(position.status()).isEqualTo("CLEARED");
        assertThat(position.marginCallIssued()).isFalse();
        assertThat(position.marginEngine()).isEqualTo("SPAN");
        assertThat(position.requiredMargin()).isEqualByComparingTo(new BigDecimal("80000.0000"));

        ClearedTrade clearedTrade = clearedTradeRepository.findByTradeId(tradeId).orElseThrow();
        assertThat(clearedTrade.getStatus()).isEqualTo("CLEARED");
        assertThat(clearedTrade.getMarginEngineUsed()).isEqualTo("SPAN");

        List<ClearingAuditLog> auditLogs = clearingAuditLogRepository.findByTradeIdOrderByIdAsc(tradeId);
        assertThat(auditLogs).hasSize(2);
        assertThat(auditLogs.get(0).getEventType()).isEqualTo("TRADE_NOVATION");
        assertThat(auditLogs.get(1).getEventType()).isEqualTo("INTERNAL_COMPLIANCE_STAMP");

        List<MarginCallRecord> calls = marginCallRecordRepository.findByMemberCode("MBR-GOLDMAN");
        assertThat(calls.stream().filter(c -> tradeId.equals(c.getTradeId()))).isEmpty();
    }

    @Test
    @DisplayName("Novate and clear trade exceeding collateral creates MarginCallRecord and publishes MarginCallEvent")
    void novateAndClearTradeExceedingMarginCollateralTriggersMarginCall() {
        String tradeId = "TRD-CITADEL-CALL-001";
        TradeSubmissionMessage message = new TradeSubmissionMessage(
                tradeId,
                "MBR-CITADEL",
                "USD/JPY",
                "FX",
                new BigDecimal("10000000.0000"),
                LocalDate.now()
        );

        ClearedTradePositionDto position = tradeClearingService.novateAndClearTrade(message);

        assertThat(position.status()).isEqualTo("MARGIN_CALL_PENDING");
        assertThat(position.marginCallIssued()).isTrue();
        assertThat(position.requiredMargin()).isEqualByComparingTo(new BigDecimal("800000.0000"));

        List<MarginCallRecord> calls = marginCallRecordRepository.findByMemberCode("MBR-CITADEL");
        MarginCallRecord record = calls.stream()
                .filter(c -> tradeId.equals(c.getTradeId()))
                .findFirst()
                .orElseThrow();
        assertThat(record.getDeficitAmount()).isEqualByComparingTo(new BigDecimal("300000.0000"));
        assertThat(record.getStatus()).isEqualTo("ISSUED");

        Object received = rabbitTemplate.receiveAndConvert(ClearingAmqpConfig.MARGIN_CALL_QUEUE, 5000);
        assertThat(received).isNotNull();
        assertThat(received).isInstanceOf(MarginCallEvent.class);
        MarginCallEvent event = (MarginCallEvent) received;
        assertThat(event.tradeId()).isEqualTo(tradeId);
        assertThat(event.memberCode()).isEqualTo("MBR-CITADEL");
        assertThat(event.deficitAmount()).isEqualByComparingTo(new BigDecimal("300000.0000"));
    }

    @Test
    @DisplayName("Compliance audit logs are preserved even when trade novation fails and rolls back")
    void complianceAuditLogsPreservedWhenTradeNovationFails() {
        String tradeId = "TRD-FAIL-COLLATERAL-001";

        String conflictingCallRef = "MC-" + tradeId;
        marginCallRecordRepository.save(MarginCallRecord.builder()
                .callReference(conflictingCallRef)
                .tradeId("PREVIOUS-TRD")
                .memberCode("MBR-CITADEL")
                .deficitAmount(new BigDecimal("50000.0000"))
                .status("ISSUED")
                .createdAt(Instant.now())
                .build());

        TradeSubmissionMessage message = new TradeSubmissionMessage(
                tradeId,
                "MBR-CITADEL",
                "USD/JPY",
                "FX",
                new BigDecimal("10000000.0000"),
                LocalDate.now()
        );

        assertThatThrownBy(() -> tradeClearingService.novateAndClearTrade(message))
                .isInstanceOf(Exception.class);

        assertThat(clearedTradeRepository.findByTradeId(tradeId)).isEmpty();

        List<ClearingAuditLog> auditLogs = clearingAuditLogRepository.findByTradeIdOrderByIdAsc(tradeId);
        assertThat(auditLogs)
                .extracting(ClearingAuditLog::getEventType)
                .containsExactly("TRADE_NOVATION", "INTERNAL_COMPLIANCE_STAMP");
    }

    @Test
    @DisplayName("RabbitListener consumes TradeSubmissionMessage from topic exchange and clears trade end-to-end")
    void rabbitListenerConsumesSubmittedTradeFromTopicExchange() {
        String tradeId = "TRD-AMQP-E2E-001";
        TradeSubmissionMessage submission = new TradeSubmissionMessage(
                tradeId,
                "MBR-GOLDMAN",
                "GBP/USD",
                "FX",
                new BigDecimal("2000000.0000"),
                LocalDate.now()
        );

        rabbitTemplate.convertAndSend(
                ClearingAmqpConfig.TRADE_TOPIC_EXCHANGE,
                "clearing.trade.submitted.fx",
                submission
        );

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ClearedTrade trade = clearedTradeRepository.findByTradeId(tradeId).orElse(null);
            assertThat(trade).isNotNull();
            assertThat(trade.getStatus()).isEqualTo("CLEARED");
            assertThat(trade.getRequiredMargin()).isEqualByComparingTo(new BigDecimal("160000.0000"));
        });
    }

    @Test
    @DisplayName("inspectMemberPortfolioWithSnapshotTouch fails at runtime due to deliberate READ_ONLY_CALLING_WRITE_REQUIRED proxy bypass")
    void inspectMemberPortfolioFailsDueToReadOnlyViolation() {
        assertThatThrownBy(() -> tradeClearingService.inspectMemberPortfolioWithSnapshotTouch("MBR-GOLDMAN"))
                .isInstanceOf(Exception.class)
                .hasMessageFindingMatch("(?i)read-only transaction");
    }

    @Test
    @DisplayName("TradeClearingController endpoints handle trade novation and member trade retrieval")
    void tradeClearingControllerEndpointsOperateCorrectly() throws Exception {
        String tradeId = "TRD-REST-001";
        TradeSubmissionMessage submission = new TradeSubmissionMessage(
                tradeId,
                "MBR-GOLDMAN",
                "EUR/USD",
                "FX",
                new BigDecimal("500000.0000"),
                LocalDate.now()
        );

        mockMvc.perform(post("/api/v1/clearing/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(tradeId))
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.marginCallIssued").value(false));

        mockMvc.perform(get("/api/v1/clearing/members/{memberCode}/trades", "MBR-GOLDMAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tradeId == '" + tradeId + "')]").exists());
    }

    @Test
    @DisplayName("CompactionBudgetService and SpringSliceExpander compact trade-clearing-compactor-demo with >55% token reduction and detect all proxy traps")
    void compactorAnalyzesDemoDomainWithHighTokenReductionAndAllTraps() {
        Path sourceRoot = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java")
                : Path.of("trade-clearing-compactor-demo/src/main/java");

        ProjectCompactionBudgetDto budget = compactionBudgetService.analyzeProjectBudget(sourceRoot);

        assertThat(budget.totalFiles()).isGreaterThanOrEqualTo(10);
        assertThat(budget.overallTokenReductionPercent())
                .as("Overall demo token reduction should be >= 55.0%")
                .isGreaterThanOrEqualTo(55.0);

        StereotypeBudgetRowDto entityRow = budget.stereotypeBreakdown().stream()
                .filter(r -> r.stereotype() == SpringStereotype.ENTITY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ENTITY stereotype breakdown row missing"));

        assertThat(entityRow.tokenReductionPercent())
                .as("Entity token reduction should be >= 65.0%")
                .isGreaterThanOrEqualTo(65.0);

        Set<SpringStereotype> detectedStereotypes = budget.stereotypeBreakdown().stream()
                .map(StereotypeBudgetRowDto::stereotype)
                .collect(Collectors.toSet());

        assertThat(detectedStereotypes)
                .as("All 7 Spring stereotypes must be present in demo")
                .containsExactlyInAnyOrder(SpringStereotype.values());

        List<MethodTrapNoteDto> serviceTraps = budget.detectedTraps().stream()
                .filter(t -> "TradeClearingService.java".equals(t.callerMethod())
                        || "inspectMemberPortfolioWithSnapshotTouch".equals(t.callerMethod())
                        || "archiveExpiredTradeNotice".equals(t.callerMethod())
                        || "archiveExpiredTradeNotice".equals(t.targetMethod()))
                .toList();

        Set<ProxyTrapType> trapTypes = serviceTraps.stream()
                .map(MethodTrapNoteDto::trapType)
                .collect(Collectors.toSet());

        assertThat(trapTypes).contains(
                ProxyTrapType.SELF_INVOCATION_BYPASS,
                ProxyTrapType.READ_ONLY_CALLING_WRITE_REQUIRED,
                ProxyTrapType.NON_PUBLIC_TRANSACTIONAL
        );

        SliceCompactionReportDto sliceReport = springSliceExpander.compactSlice(sourceRoot, "com.builtbyjuls.clearing.trade", 10000);
        assertThat(sliceReport.totalFilesCompacted()).isGreaterThanOrEqualTo(3);
        assertThat(sliceReport.overallTokenReductionPercent()).isGreaterThan(40.0);
    }
}
