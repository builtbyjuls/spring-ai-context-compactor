package com.builtbyjuls.compactor.service;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.builtbyjuls.compactor.model.SpringStereotype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionBudgetServiceTest {

    private CompactionBudgetService budgetService;

    @BeforeEach
    void setUp() {
        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        CompactorProperties properties = new CompactorProperties();
        SpringAstCompactor astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);
        budgetService = new CompactionBudgetService(astCompactor);
    }

    private void createSampleSourceTree(Path root) throws IOException {
        Path packageDir = root.resolve("com/builtbyjuls/clearing");
        Files.createDirectories(packageDir);

        String verboseEntity = """
                package com.builtbyjuls.clearing;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import java.math.BigDecimal;
                import java.time.Instant;
                import java.util.Objects;
                import java.util.UUID;

                @Entity
                @Table(name = "cleared_trades")
                public class ClearedTrade {

                    @Id
                    @Column(name = "trade_id")
                    private UUID tradeId;

                    @Column(name = "trade_reference")
                    private String tradeReference;

                    @Column(name = "notional_amount")
                    private BigDecimal notionalAmount;

                    @Column(name = "cleared_at")
                    private Instant clearedAt;

                    public ClearedTrade() {
                    }

                    public ClearedTrade(UUID tradeId, String tradeReference, BigDecimal notionalAmount, Instant clearedAt) {
                        this.tradeId = tradeId;
                        this.tradeReference = tradeReference;
                        this.notionalAmount = notionalAmount;
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

                    public BigDecimal getNotionalAmount() {
                        return notionalAmount;
                    }

                    public void setNotionalAmount(BigDecimal notionalAmount) {
                        this.notionalAmount = notionalAmount;
                    }

                    public Instant getClearedAt() {
                        return clearedAt;
                    }

                    public void setClearedAt(Instant clearedAt) {
                        this.clearedAt = clearedAt;
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
                }
                """;
        Files.writeString(packageDir.resolve("ClearedTrade.java"), verboseEntity);

        String repoSource = """
                package com.builtbyjuls.clearing;

                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.data.repository.query.Param;
                import org.springframework.stereotype.Repository;
                import java.util.Optional;
                import java.util.UUID;

                @Repository
                public interface TradeRepository extends JpaRepository<ClearedTrade, UUID> {

                    @Query("SELECT t FROM ClearedTrade t WHERE t.tradeId = :id")
                    Optional<ClearedTrade> findByIdWithDetails(@Param("id") UUID id);
                }
                """;
        Files.writeString(packageDir.resolve("TradeRepository.java"), repoSource);

        String serviceSource = """
                package com.builtbyjuls.clearing;

                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.UUID;

                @Service
                public class TradeService {

                    private final TradeRepository tradeRepository;

                    public TradeService(TradeRepository tradeRepository) {
                        this.tradeRepository = tradeRepository;
                    }

                    @Transactional
                    public void bookTrade(UUID tradeId) {
                        if (tradeId == null) {
                            throw new IllegalArgumentException("tradeId required");
                        }
                        this.auditTrade(tradeId);
                    }

                    @Transactional
                    public void auditTrade(UUID tradeId) {
                        System.out.println("Auditing: " + tradeId);
                    }
                }
                """;
        Files.writeString(packageDir.resolve("TradeService.java"), serviceSource);
    }

    @Test
    @DisplayName("analyzeProjectBudget aggregates lines, tokens, stereotype breakdowns, and traps")
    void analyzeProjectBudgetAggregatesCorrectly(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        ProjectCompactionBudgetDto budget = budgetService.analyzeProjectBudget(tempDir);

        assertThat(budget.totalFiles()).isEqualTo(3);
        assertThat(budget.originalTotalLines()).isPositive();
        assertThat(budget.compactedTotalLines()).isPositive();
        assertThat(budget.compactedTotalLines()).isLessThan(budget.originalTotalLines());
        assertThat(budget.originalEstimatedTokens()).isPositive();
        assertThat(budget.compactedEstimatedTokens()).isPositive();
        assertThat(budget.tokensSaved()).isPositive();
        assertThat(budget.overallTokenReductionPercent()).isGreaterThan(0.0);

        assertThat(budget.stereotypeBreakdown())
                .extracting(row -> row.stereotype())
                .contains(SpringStereotype.ENTITY, SpringStereotype.REPOSITORY, SpringStereotype.SERVICE);

        assertThat(budget.detectedTraps()).hasSize(1);
        assertThat(budget.detectedTraps().get(0).trapType()).isEqualTo(ProxyTrapType.SELF_INVOCATION_BYPASS);
        assertThat(budget.detectedTraps().get(0).callerMethod()).isEqualTo("bookTrade");
        assertThat(budget.detectedTraps().get(0).targetMethod()).isEqualTo("auditTrade");
    }

    @Test
    @DisplayName("analyzeProjectBudget returns empty budget for non-existent source root")
    void analyzeProjectBudgetNonExistentRoot(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing_dir");

        ProjectCompactionBudgetDto budget = budgetService.analyzeProjectBudget(missing);

        assertThat(budget.totalFiles()).isEqualTo(0);
        assertThat(budget.originalTotalLines()).isEqualTo(0);
        assertThat(budget.compactedTotalLines()).isEqualTo(0);
        assertThat(budget.tokensSaved()).isEqualTo(0);
        assertThat(budget.overallTokenReductionPercent()).isEqualTo(0.0);
        assertThat(budget.stereotypeBreakdown()).isEmpty();
        assertThat(budget.detectedTraps()).isEmpty();
    }
}
