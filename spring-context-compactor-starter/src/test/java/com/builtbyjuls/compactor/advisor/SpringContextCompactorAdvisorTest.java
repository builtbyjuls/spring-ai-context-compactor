package com.builtbyjuls.compactor.advisor;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.AdvisorCompactionStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringContextCompactorAdvisorTest {

    private SpringAstCompactor astCompactor;
    private CompactorProperties properties;
    private SpringContextCompactorAdvisor advisor;
    private AtomicReference<Prompt> capturedPrompt;
    private ChatClient chatClient;

    private static final String VERBOSE_ENTITY_SOURCE = """
            package com.builtbyjuls.clearing.trade;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Table;
            import jakarta.persistence.Id;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.GenerationType;
            import jakarta.persistence.Column;
            import jakarta.persistence.ManyToOne;
            import jakarta.persistence.JoinColumn;
            import jakarta.persistence.FetchType;
            import jakarta.persistence.Enumerated;
            import jakarta.persistence.EnumType;
            import jakarta.persistence.Version;
            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.Positive;
            import java.math.BigDecimal;
            import java.time.Instant;
            import java.util.Objects;
            import java.util.UUID;
            import com.builtbyjuls.clearing.model.TradeStatus;
            import com.builtbyjuls.clearing.member.ClearingMember;

            @Entity
            @Table(name = "cleared_trades")
            public class ClearedTrade {

                @Id
                @GeneratedValue(strategy = GenerationType.UUID)
                @Column(name = "trade_id", nullable = false, updatable = false)
                private UUID tradeId;

                @NotNull
                @Column(name = "trade_reference", nullable = false, unique = true)
                private String tradeReference;

                @NotNull
                @ManyToOne(fetch = FetchType.LAZY)
                @JoinColumn(name = "clearing_member_id", nullable = false)
                private ClearingMember clearingMember;

                @NotNull
                @Positive
                @Column(name = "notional_amount", nullable = false, precision = 19, scale = 4)
                private BigDecimal notionalAmount;

                @NotNull
                @Column(name = "currency", nullable = false, length = 3)
                private String currency;

                @NotNull
                @Enumerated(EnumType.STRING)
                @Column(name = "status", nullable = false, length = 32)
                private TradeStatus status;

                @NotNull
                @Column(name = "cleared_at", nullable = false)
                private Instant clearedAt;

                @Version
                @Column(name = "version")
                private Long version;

                public ClearedTrade() {
                }

                public ClearedTrade(UUID tradeId, String tradeReference, ClearingMember clearingMember,
                                    BigDecimal notionalAmount, String currency, TradeStatus status, Instant clearedAt) {
                    this.tradeId = tradeId;
                    this.tradeReference = tradeReference;
                    this.clearingMember = clearingMember;
                    this.notionalAmount = notionalAmount;
                    this.currency = currency;
                    this.status = status;
                    this.clearedAt = clearedAt;
                }

                public UUID getTradeId() {
                    return tradeId;
                }

                public void setTradeId(UUID tradeId) {
                    this.tradeId = tradeId;
                }

                public String getTradeReference() {
                    return tradeReference;
                }

                public void setTradeReference(String tradeReference) {
                    this.tradeReference = tradeReference;
                }

                public ClearingMember getClearingMember() {
                    return clearingMember;
                }

                public void setClearingMember(ClearingMember clearingMember) {
                    this.clearingMember = clearingMember;
                }

                public BigDecimal getNotionalAmount() {
                    return notionalAmount;
                }

                public void setNotionalAmount(BigDecimal notionalAmount) {
                    this.notionalAmount = notionalAmount;
                }

                public String getCurrency() {
                    return currency;
                }

                public void setCurrency(String currency) {
                    this.currency = currency;
                }

                public TradeStatus getStatus() {
                    return status;
                }

                public void setStatus(TradeStatus status) {
                    this.status = status;
                }

                public Instant getClearedAt() {
                    return clearedAt;
                }

                public void setClearedAt(Instant clearedAt) {
                    this.clearedAt = clearedAt;
                }

                public Long getVersion() {
                    return version;
                }

                public void setVersion(Long version) {
                    this.version = version;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;
                    ClearedTrade that = (ClearedTrade) o;
                    return Objects.equals(tradeId, that.tradeId);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(tradeId);
                }

                @Override
                public String toString() {
                    return "ClearedTrade{" +
                            "tradeId=" + tradeId +
                            ", tradeReference='" + tradeReference + '\\'' +
                            ", notionalAmount=" + notionalAmount +
                            ", currency='" + currency + '\\'' +
                            ", status=" + status +
                            ", clearedAt=" + clearedAt +
                            '}';
                }

                public static class Builder {
                    private UUID tradeId;
                    private String tradeReference;
                    private ClearingMember clearingMember;
                    private BigDecimal notionalAmount;
                    private String currency;
                    private TradeStatus status;
                    private Instant clearedAt;

                    public Builder tradeId(UUID tradeId) {
                        this.tradeId = tradeId;
                        return this;
                    }

                    public Builder tradeReference(String tradeReference) {
                        this.tradeReference = tradeReference;
                        return this;
                    }

                    public Builder clearingMember(ClearingMember clearingMember) {
                        this.clearingMember = clearingMember;
                        return this;
                    }

                    public Builder notionalAmount(BigDecimal notionalAmount) {
                        this.notionalAmount = notionalAmount;
                        return this;
                    }

                    public Builder currency(String currency) {
                        this.currency = currency;
                        return this;
                    }

                    public Builder status(TradeStatus status) {
                        this.status = status;
                        return this;
                    }

                    public Builder clearedAt(Instant clearedAt) {
                        this.clearedAt = clearedAt;
                        return this;
                    }

                    public ClearedTrade build() {
                        return new ClearedTrade(tradeId, tradeReference, clearingMember, notionalAmount, currency, status, clearedAt);
                    }
                }
            }
            """;

    private static final String VERBOSE_SERVICE_SOURCE = """
            package com.builtbyjuls.clearing.service;

            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            import java.util.UUID;
            import java.math.BigDecimal;
            import com.builtbyjuls.clearing.trade.ClearedTrade;
            import com.builtbyjuls.clearing.repository.ClearedTradeRepository;
            import com.builtbyjuls.clearing.audit.ClearingAuditService;

            @Service
            public class TradeClearingService {

                private final ClearedTradeRepository tradeRepository;
                private final ClearingAuditService auditService;

                public TradeClearingService(ClearedTradeRepository tradeRepository, ClearingAuditService auditService) {
                    this.tradeRepository = tradeRepository;
                    this.auditService = auditService;
                }

                @Transactional(readOnly = true)
                public ClearedTrade findTrade(UUID tradeId) {
                    if (tradeId == null) {
                        throw new IllegalArgumentException("tradeId must not be null");
                    }
                    ClearedTrade trade = tradeRepository.findById(tradeId).orElse(null);
                    if (trade != null) {
                        auditService.logRead(tradeId);
                        System.out.println("Audit logged for trade read event: " + tradeId);
                    }
                    return trade;
                }

                public ClearedTrade novateAndClear(ClearedTrade trade) {
                    if (trade == null) {
                        throw new IllegalArgumentException("trade must not be null");
                    }
                    System.out.println("Beginning novation process for trade: " + trade.getTradeReference());
                    if (trade.getNotionalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalStateException("Notional amount must be strictly positive");
                    }
                    System.out.println("Validating currency constraints: " + trade.getCurrency());
                    return executeNovation(trade);
                }

                @Transactional
                public ClearedTrade executeNovation(ClearedTrade trade) {
                    // Institutional multi-step trade novation and risk verification workflow
                    System.out.println("Beginning bilateral trade novation sequence for clearing member ID: " + trade.getClearingMember());
                    if (trade.getTradeReference() == null || trade.getTradeReference().isBlank()) {
                        throw new IllegalArgumentException("Trade reference identifier cannot be null or blank during novation lifecycle");
                    }
                    System.out.println("Validating clearing counterparty margin buffers against centralized risk threshold");
                    BigDecimal calculatedExposure = trade.getNotionalAmount().multiply(new BigDecimal("0.1250"));
                    System.out.println("Calculated initial margin buffer requirement for novated trade: " + calculatedExposure);
                    auditService.logNovation(trade.getTradeId());
                    System.out.println("Persisting novated trade record to relational database: " + trade.getTradeId());
                    ClearedTrade saved = tradeRepository.save(trade);
                    auditService.logAuditRecord("TRADE_NOVATION_CONFIRMED", trade.getTradeId().toString());
                    System.out.println("Successfully finalized trade novation workflow for trade reference: " + saved.getTradeReference());
                    return saved;
                }

                @Transactional(readOnly = true)
                public ClearedTrade inspectAndClear(UUID tradeId) {
                    // High-level clearing inspection wrapper invoking internal trade query and novation
                    System.out.println("Executing clearing inspection for trade identifier: " + tradeId);
                    ClearedTrade trade = findTrade(tradeId);
                    if (trade == null) {
                        throw new IllegalArgumentException("Trade record was not found for clearing inspection: " + tradeId);
                    }
                    System.out.println("Triggering downstream novation from inspect method with ID: " + tradeId);
                    return executeNovation(trade);
                }

                @Transactional
                void internalPackagePrivateClear(ClearedTrade trade) {
                    // Non-public transactional method intended for internal package-scoped reconciliation
                    System.out.println("Running package-private clearing logic without proxy interception for trade: " + trade.getTradeId());
                    tradeRepository.save(trade);
                }
            }
            """;

    @BeforeEach
    void setUp() {
        properties = new CompactorProperties();
        properties.setAdvisorOrder(5);
        properties.setDetectProxyTraps(true);

        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);

        advisor = new SpringContextCompactorAdvisor(astCompactor, properties);

        capturedPrompt = new AtomicReference<>();
        ChatModel stubChatModel = prompt -> {
            capturedPrompt.set(prompt);
            AssistantMessage assistantMessage = new AssistantMessage("Compacted context processed successfully.");
            return new ChatResponse(List.of(new Generation(assistantMessage)));
        };

        chatClient = ChatClient.builder(stubChatModel)
                .defaultAdvisors(advisor)
                .build();
    }

    @Test
    @DisplayName("AdvisorCompactionStatsDto rejects negative constructor arguments")
    void statsRecordRejectsNegativeValues() {
        assertThatThrownBy(() -> new AdvisorCompactionStatsDto(-1, 0, 100, 50, 50, 50.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdvisorCompactionStatsDto(1, -1, 100, 50, 50, 50.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdvisorCompactionStatsDto(1, 1, -100, 50, 50, 50.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdvisorCompactionStatsDto(1, 1, 100, -50, 50, 50.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdvisorCompactionStatsDto(1, 1, 100, 50, -1, 50.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdvisorCompactionStatsDto(1, 1, 100, 50, 50, -5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Advisor metadata returns configured name and order")
    void advisorMetadata() {
        assertThat(advisor.getName()).isEqualTo("SpringContextCompactorAdvisor");
        assertThat(advisor.getOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("Compacts fenced entity and service blocks, preserves prose and proxy traps, and achieves >60% reduction")
    void compactsFencedJavaBlocksInUserMessage() {
        String userPrompt = "Please review and optimize these two Spring components for institutional trade clearing:\n\n"
                + "```java\n" + VERBOSE_ENTITY_SOURCE + "```\n\n"
                + "Here is the service coordinating trade novation:\n\n"
                + "```java\n" + VERBOSE_SERVICE_SOURCE + "```\n\n"
                + "What concurrency bottlenecks or proxy traps do you detect?";

        ChatClientResponse response = chatClient.prompt()
                .user(userPrompt)
                .call()
                .chatClientResponse();

        assertThat(response).isNotNull();

        // 1. Verify downstream Prompt received by ChatModel
        Prompt downstreamPrompt = capturedPrompt.get();
        assertThat(downstreamPrompt).isNotNull();
        String downstreamText = downstreamPrompt.getUserMessage().getText();

        // Prose instructions preserved
        assertThat(downstreamText).contains("Please review and optimize these two Spring components for institutional trade clearing:");
        assertThat(downstreamText).contains("Here is the service coordinating trade novation:");
        assertThat(downstreamText).contains("What concurrency bottlenecks or proxy traps do you detect?");

        // Compacted entity structure preserved without boilerplate
        assertThat(downstreamText).contains("@Entity");
        assertThat(downstreamText).contains("@Table(name = \"cleared_trades\")");
        assertThat(downstreamText).contains("private UUID tradeId;");
        assertThat(downstreamText).contains("private ClearingMember clearingMember;");
        assertThat(downstreamText).doesNotContain("getTradeId()");
        assertThat(downstreamText).doesNotContain("public boolean equals(");
        assertThat(downstreamText).doesNotContain("class Builder");

        // Compacted service structure preserved with inline proxy traps
        assertThat(downstreamText).contains("@Service");
        assertThat(downstreamText).contains("private final ClearedTradeRepository tradeRepository;");
        assertThat(downstreamText).contains("private final ClearingAuditService auditService;");
        assertThat(downstreamText).contains("[TRAP: SELF_INVOCATION_BYPASS]");
        assertThat(downstreamText).doesNotContain("System.out.println");

        // 2. Verify compaction stats in ChatClientResponse context
        Object rawStats = response.context().get(SpringContextCompactorAdvisor.STATS_CONTEXT_KEY);
        assertThat(rawStats).isInstanceOf(AdvisorCompactionStatsDto.class);
        AdvisorCompactionStatsDto stats = (AdvisorCompactionStatsDto) rawStats;

        assertThat(stats.codeBlocksDetected()).isEqualTo(2);
        assertThat(stats.codeBlocksCompacted()).isEqualTo(2);
        assertThat(stats.originalTokens()).isGreaterThan(stats.compactedTokens());
        assertThat(stats.tokensSaved()).isGreaterThan(0);
        assertThat(stats.tokenReductionPercent()).isGreaterThan(60.0);

        // 3. Verify in-memory metrics tracking
        List<AdvisorCompactionStatsDto> recorded = advisor.getRecordedStats();
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0)).isEqualTo(stats);

        AdvisorCompactionStatsDto cumulative = advisor.getCumulativeStats();
        assertThat(cumulative).isEqualTo(stats);
    }

    @Test
    @DisplayName("Plain English prose prompt is passed through unchanged with zero compaction")
    void plainEnglishPromptPassedThroughUnchanged() {
        String prosePrompt = "Can you explain how Spring transactional proxies handle rollback exceptions?";

        ChatClientResponse response = chatClient.prompt()
                .user(prosePrompt)
                .call()
                .chatClientResponse();

        Prompt downstreamPrompt = capturedPrompt.get();
        assertThat(downstreamPrompt.getUserMessage().getText()).isEqualTo(prosePrompt);

        AdvisorCompactionStatsDto stats = (AdvisorCompactionStatsDto) response.context()
                .get(SpringContextCompactorAdvisor.STATS_CONTEXT_KEY);
        assertThat(stats.codeBlocksDetected()).isEqualTo(0);
        assertThat(stats.codeBlocksCompacted()).isEqualTo(0);
        assertThat(stats.tokensSaved()).isEqualTo(0);
        assertThat(stats.tokenReductionPercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Tiny snippet that would not save tokens is passed through untouched")
    void tinyJavaSnippetPassedThroughUntouched() {
        String promptWithTinySnippet = "Here is an interface:\n\n```java\npublic interface Marker {}\n```\nWhat do you think?";

        ChatClientResponse response = chatClient.prompt()
                .user(promptWithTinySnippet)
                .call()
                .chatClientResponse();

        Prompt downstreamPrompt = capturedPrompt.get();
        assertThat(downstreamPrompt.getUserMessage().getText()).isEqualTo(promptWithTinySnippet);

        AdvisorCompactionStatsDto stats = (AdvisorCompactionStatsDto) response.context()
                .get(SpringContextCompactorAdvisor.STATS_CONTEXT_KEY);
        assertThat(stats.codeBlocksDetected()).isEqualTo(1);
        assertThat(stats.codeBlocksCompacted()).isEqualTo(0);
        assertThat(stats.tokensSaved()).isEqualTo(0);
    }

    @Test
    @DisplayName("Compacts Java code inside SystemMessage")
    void compactsSystemMessage() {
        ChatClientResponse response = chatClient.prompt()
                .system("You are an institutional trading assistant specializing in this domain model:\n\n```java\n" + VERBOSE_ENTITY_SOURCE + "```")
                .user("List the required validation annotations for ClearedTrade.")
                .call()
                .chatClientResponse();

        Prompt downstreamPrompt = capturedPrompt.get();
        SystemMessage sysMsg = downstreamPrompt.getSystemMessage();
        assertThat(sysMsg).isNotNull();
        assertThat(sysMsg.getText()).contains("You are an institutional trading assistant");
        assertThat(sysMsg.getText()).contains("@Entity");
        assertThat(sysMsg.getText()).doesNotContain("getTradeId()");

        AdvisorCompactionStatsDto stats = (AdvisorCompactionStatsDto) response.context()
                .get(SpringContextCompactorAdvisor.STATS_CONTEXT_KEY);
        assertThat(stats.codeBlocksCompacted()).isEqualTo(1);
        assertThat(stats.tokensSaved()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Compacts raw Java compilation unit without markdown fences")
    void compactsRawJavaSourcePrompt() {
        ChatClientResponse response = chatClient.prompt()
                .user(VERBOSE_SERVICE_SOURCE)
                .call()
                .chatClientResponse();

        Prompt downstreamPrompt = capturedPrompt.get();
        String downstreamText = downstreamPrompt.getUserMessage().getText();
        assertThat(downstreamText).contains("// [SPRING COMPACTOR]");
        assertThat(downstreamText).contains("@Service");
        assertThat(downstreamText).contains("[TRAP: SELF_INVOCATION_BYPASS]");
        assertThat(downstreamText).doesNotContain("System.out.println");

        AdvisorCompactionStatsDto stats = (AdvisorCompactionStatsDto) response.context()
                .get(SpringContextCompactorAdvisor.STATS_CONTEXT_KEY);
        assertThat(stats.codeBlocksDetected()).isEqualTo(1);
        assertThat(stats.codeBlocksCompacted()).isEqualTo(1);
        assertThat(stats.tokensSaved()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Cumulative stats accumulate across calls and clearStats resets tracking")
    void cumulativeStatsAndClear() {
        chatClient.prompt().user("Plain question 1").call().content();
        chatClient.prompt().user("```java\n" + VERBOSE_ENTITY_SOURCE + "```").call().content();
        chatClient.prompt().user("```java\n" + VERBOSE_SERVICE_SOURCE + "```").call().content();

        assertThat(advisor.getRecordedStats()).hasSize(3);
        AdvisorCompactionStatsDto cumulative = advisor.getCumulativeStats();
        assertThat(cumulative.codeBlocksDetected()).isEqualTo(2);
        assertThat(cumulative.codeBlocksCompacted()).isEqualTo(2);
        assertThat(cumulative.tokensSaved()).isGreaterThan(0);
        assertThat(cumulative.tokenReductionPercent()).isGreaterThan(50.0);

        advisor.clearStats();
        assertThat(advisor.getRecordedStats()).isEmpty();
        AdvisorCompactionStatsDto cleared = advisor.getCumulativeStats();
        assertThat(cleared.codeBlocksDetected()).isEqualTo(0);
        assertThat(cleared.codeBlocksCompacted()).isEqualTo(0);
        assertThat(cleared.tokensSaved()).isEqualTo(0);
        assertThat(cleared.tokenReductionPercent()).isEqualTo(0.0);
    }
}
