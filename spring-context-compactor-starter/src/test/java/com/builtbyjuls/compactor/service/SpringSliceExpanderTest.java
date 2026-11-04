package com.builtbyjuls.compactor.service;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.MethodBodySliceDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.builtbyjuls.compactor.model.SliceCompactionReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSliceExpanderTest {

    private SpringSliceExpander sliceExpander;

    @BeforeEach
    void setUp() {
        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        CompactorProperties properties = new CompactorProperties();
        SpringAstCompactor astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);
        sliceExpander = new SpringSliceExpander(astCompactor, trapScanner, properties);
    }

    private void createSampleSourceTree(Path root) throws IOException {
        Path tradePkg = root.resolve("com/builtbyjuls/clearing/trade");
        Path ledgerPkg = root.resolve("com/builtbyjuls/clearing/ledger");
        Files.createDirectories(tradePkg);
        Files.createDirectories(ledgerPkg);

        String verboseEntity = """
                package com.builtbyjuls.clearing.trade;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import jakarta.persistence.Version;
                import jakarta.validation.constraints.NotNull;
                import jakarta.validation.constraints.Positive;
                import java.math.BigDecimal;
                import java.time.Instant;
                import java.util.Objects;
                import java.util.UUID;

                @Entity
                @Table(name = "cleared_trades")
                public class ClearedTrade {

                    @Id
                    @GeneratedValue(strategy = GenerationType.UUID)
                    @Column(name = "trade_id", nullable = false)
                    private UUID tradeId;

                    @NotNull
                    @Column(name = "trade_reference", nullable = false, unique = true)
                    private String tradeReference;

                    @NotNull
                    @Positive
                    @Column(name = "notional_amount", nullable = false)
                    private BigDecimal notionalAmount;

                    @NotNull
                    @Column(name = "currency", nullable = false, length = 3)
                    private String currency;

                    @NotNull
                    @Column(name = "cleared_at", nullable = false)
                    private Instant clearedAt;

                    @Version
                    @Column(name = "version")
                    private Long version;

                    public ClearedTrade() {
                    }

                    public ClearedTrade(UUID tradeId, String tradeReference, BigDecimal notionalAmount,
                                        String currency, Instant clearedAt, Long version) {
                        this.tradeId = tradeId;
                        this.tradeReference = tradeReference;
                        this.notionalAmount = notionalAmount;
                        this.currency = currency;
                        this.clearedAt = clearedAt;
                        this.version = version;
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

                    public String getCurrency() {
                        return currency;
                    }

                    public void setCurrency(String currency) {
                        this.currency = currency;
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
                                '}';
                    }

                    public static class Builder {
                        private UUID tradeId;
                        private String tradeReference;
                        private BigDecimal notionalAmount;
                        private String currency;
                        private Instant clearedAt;
                        private Long version;

                        public Builder tradeId(UUID tradeId) {
                            this.tradeId = tradeId;
                            return this;
                        }

                        public Builder tradeReference(String tradeReference) {
                            this.tradeReference = tradeReference;
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

                        public Builder clearedAt(Instant clearedAt) {
                            this.clearedAt = clearedAt;
                            return this;
                        }

                        public Builder version(Long version) {
                            this.version = version;
                            return this;
                        }

                        public ClearedTrade build() {
                            return new ClearedTrade(tradeId, tradeReference, notionalAmount, currency, clearedAt, version);
                        }
                    }
                }
                """;
        Files.writeString(tradePkg.resolve("ClearedTrade.java"), verboseEntity);

        String repoSource = """
                package com.builtbyjuls.clearing.trade;

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
        Files.writeString(tradePkg.resolve("TradeRepository.java"), repoSource);

        String verboseService = """
                package com.builtbyjuls.clearing.trade;

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
                        // Business logic validation and intra-class self-invocation trap
                        if (tradeId == null) {
                            throw new IllegalArgumentException("tradeId must not be null");
                        }
                        this.auditTrade(tradeId);
                    }

                    @Transactional
                    public void bookTrade(UUID tradeId, boolean bypassLimits) {
                        if (bypassLimits) {
                            System.out.println("Booking trade bypassing limit checks: " + tradeId);
                        } else {
                            System.out.println("Booking standard trade: " + tradeId);
                        }
                    }

                    @Transactional
                    public void auditTrade(UUID tradeId) {
                        System.out.println("Auditing trade execution record: " + tradeId);
                    }

                    public void calculateNetPosition(String memberId) {
                        if (memberId == null || memberId.isBlank()) {
                            throw new IllegalArgumentException("memberId must not be null or blank for netting computation");
                        }
                        for (int i = 0; i < 50; i++) {
                            System.out.println("Netting calculation iteration: " + i);
                        }
                    }

                    public boolean verifyCounterpartyLimits(String counterparty, double notional) {
                        if (counterparty == null || counterparty.isBlank()) {
                            throw new IllegalArgumentException("counterparty must not be null or blank");
                        }
                        if (notional > 1_000_000.0) {
                            System.out.println("High exposure counterparty check");
                            return false;
                        }
                        return true;
                    }
                }
                """;
        Files.writeString(tradePkg.resolve("TradeService.java"), verboseService);

        String verboseLedgerEntity = """
                package com.builtbyjuls.clearing.ledger;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import java.math.BigDecimal;
                import java.time.Instant;
                import java.util.Objects;
                import java.util.UUID;

                @Entity
                @Table(name = "ledger_entries")
                public class LedgerEntry {

                    @Id
                    @Column(name = "entry_id")
                    private UUID entryId;

                    @Column(name = "amount")
                    private BigDecimal amount;

                    @Column(name = "recorded_at")
                    private Instant recordedAt;

                    public LedgerEntry() {
                    }

                    public LedgerEntry(UUID entryId, BigDecimal amount, Instant recordedAt) {
                        this.entryId = entryId;
                        this.amount = amount;
                        this.recordedAt = recordedAt;
                    }

                    public UUID getEntryId() {
                        return entryId;
                    }

                    public void setEntryId(UUID entryId) {
                        this.entryId = entryId;
                    }

                    public BigDecimal getAmount() {
                        return amount;
                    }

                    public void setAmount(BigDecimal amount) {
                        this.amount = amount;
                    }

                    public Instant getRecordedAt() {
                        return recordedAt;
                    }

                    public void setRecordedAt(Instant recordedAt) {
                        this.recordedAt = recordedAt;
                    }

                    @Override
                    public boolean equals(Object o) {
                        if (this == o) return true;
                        if (o == null || getClass() != o.getClass()) return false;
                        LedgerEntry that = (LedgerEntry) o;
                        return Objects.equals(entryId, that.entryId);
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(entryId);
                    }
                }
                """;
        Files.writeString(ledgerPkg.resolve("LedgerEntry.java"), verboseLedgerEntity);

        String ledgerService = """
                package com.builtbyjuls.clearing.ledger;

                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.UUID;

                @Service
                public class LedgerService {

                    @Transactional(readOnly = true)
                    public void verifyEntry(UUID entryId) {
                        System.out.println("Verifying entry: " + entryId);
                    }
                }
                """;
        Files.writeString(ledgerPkg.resolve("LedgerService.java"), ledgerService);
    }

    @Test
    @DisplayName("compactSlice filters by package query and achieves >60% token reduction")
    void compactSliceFiltersByPackage(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        SliceCompactionReportDto report = sliceExpander.compactSlice(
                tempDir,
                "com.builtbyjuls.clearing.trade"
        );

        assertThat(report.totalFilesCompacted()).isEqualTo(3);
        assertThat(report.files()).hasSize(3);
        assertThat(report.files())
                .extracting(f -> f.className())
                .containsExactlyInAnyOrder("ClearedTrade", "TradeRepository", "TradeService");
        assertThat(report.overallTokenReductionPercent()).isGreaterThan(60.0);
        assertThat(report.tokenBudgetExceeded()).isFalse();
        assertThat(report.combinedSkeleton()).contains("class ClearedTrade");
        assertThat(report.combinedSkeleton()).contains("interface TradeRepository");
        assertThat(report.combinedSkeleton()).contains("class TradeService");
    }

    @Test
    @DisplayName("compactSlice with blank query compacts all files in source root")
    void compactSliceAllFiles(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        SliceCompactionReportDto report = sliceExpander.compactSlice(tempDir, "");

        assertThat(report.totalFilesCompacted()).isEqualTo(5);
        assertThat(report.tokenBudgetExceeded()).isFalse();
    }

    @Test
    @DisplayName("compactSlice enforces maxTokenBudget and flags tokenBudgetExceeded")
    void compactSliceEnforcesBudget(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        // Budget capped tightly to force budget exceedance
        SliceCompactionReportDto report = sliceExpander.compactSlice(
                tempDir,
                "com.builtbyjuls.clearing.trade",
                50
        );

        assertThat(report.totalFilesCompacted()).isGreaterThanOrEqualTo(1);
        assertThat(report.tokenBudgetExceeded()).isTrue();
    }

    @Test
    @DisplayName("compactSlice returns empty report for non-existent source root")
    void compactSliceNonExistentRoot(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does_not_exist");

        SliceCompactionReportDto report = sliceExpander.compactSlice(missing, "trade");

        assertThat(report.totalFilesCompacted()).isEqualTo(0);
        assertThat(report.files()).isEmpty();
        assertThat(report.combinedSkeleton()).isEmpty();
        assertThat(report.tokenBudgetExceeded()).isFalse();
    }

    @Test
    @DisplayName("expandMethodBody extracts 1-based numbered lines for overloaded methods")
    void expandMethodBodyOverloadedMethods(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        List<MethodBodySliceDto> slices = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "bookTrade"
        );

        assertThat(slices).hasSize(2);

        MethodBodySliceDto first = slices.get(0);
        assertThat(first.className()).isEqualTo("TradeService");
        assertThat(first.methodName()).isEqualTo("bookTrade");
        assertThat(first.startLine()).isPositive();
        assertThat(first.endLine()).isGreaterThan(first.startLine());
        assertThat(first.lineCount()).isEqualTo(first.endLine() - first.startLine() + 1);
        assertThat(first.numberedSourceLines()).contains(String.format("%4d: ", first.startLine()));
        assertThat(first.trapNotes()).hasSize(1);
        assertThat(first.trapNotes().get(0).trapType()).isEqualTo(ProxyTrapType.SELF_INVOCATION_BYPASS);

        MethodBodySliceDto second = slices.get(1);
        assertThat(second.className()).isEqualTo("TradeService");
        assertThat(second.methodName()).isEqualTo("bookTrade");
        assertThat(second.signature()).contains("boolean bypassLimits");
        assertThat(second.trapNotes()).isEmpty();
    }

    @Test
    @DisplayName("expandMethodBody returns empty list for unknown method or file")
    void expandMethodBodyUnknown(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        List<MethodBodySliceDto> unknownMethod = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "nonExistentMethod"
        );
        assertThat(unknownMethod).isEmpty();

        List<MethodBodySliceDto> unknownFile = sliceExpander.expandMethodBody(
                tempDir,
                "NonExistentService",
                "bookTrade"
        );
        assertThat(unknownFile).isEmpty();
    }

    @Test
    @DisplayName("expandMethodBody supports comma-separated method names and wildcard *")
    void expandMethodBodyBatchAndWildcard(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        List<MethodBodySliceDto> batchSlices = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "auditTrade, calculateNetPosition"
        );

        assertThat(batchSlices).hasSize(2);
        assertThat(batchSlices.stream().map(MethodBodySliceDto::methodName).toList())
                .containsExactly("auditTrade", "calculateNetPosition");

        List<MethodBodySliceDto> allSlices = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "*"
        );
        assertThat(allSlices).hasSize(5);
        assertThat(allSlices.stream().map(MethodBodySliceDto::methodName).toList())
                .contains("bookTrade", "auditTrade", "calculateNetPosition", "verifyCounterpartyLimits");
    }

    @Test
    @DisplayName("expandMethodBodies convenience overload delegates and unfolds multiple methods")
    void expandMethodBodiesCollectionOverload(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        List<MethodBodySliceDto> slices = sliceExpander.expandMethodBodies(
                tempDir,
                "TradeService",
                List.of("auditTrade", "verifyCounterpartyLimits")
        );

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).methodName()).isEqualTo("auditTrade");
        assertThat(slices.get(1).methodName()).isEqualTo("verifyCounterpartyLimits");

        assertThat(sliceExpander.expandMethodBodies(tempDir, "TradeService", List.of())).isEmpty();
        assertThat(sliceExpander.expandMethodBodies(tempDir, "TradeService", null)).isEmpty();
    }

    @Test
    @DisplayName("expandMethodBody supports wildcard glob patterns like *Trade* and verify*")
    void expandMethodBodyGlobPatterns(@TempDir Path tempDir) throws IOException {
        createSampleSourceTree(tempDir);

        List<MethodBodySliceDto> tradeSlices = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "*Trade*"
        );
        assertThat(tradeSlices.stream().map(MethodBodySliceDto::methodName).toList())
                .containsExactly("bookTrade", "bookTrade", "auditTrade");

        List<MethodBodySliceDto> verifySlices = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "verify*"
        );
        assertThat(verifySlices).hasSize(1);
        assertThat(verifySlices.get(0).methodName()).isEqualTo("verifyCounterpartyLimits");

        List<MethodBodySliceDto> positionSlices = sliceExpander.expandMethodBody(
                tempDir,
                "TradeService",
                "*Position"
        );
        assertThat(positionSlices).hasSize(1);
        assertThat(positionSlices.get(0).methodName()).isEqualTo("calculateNetPosition");
    }

    @Test
    @DisplayName("expandMethodBodiesWithCallees transitively unfolds intra-class helpers in source order")
    void expandMethodBodiesWithCalleesTransitive(@TempDir Path tempDir) throws IOException {
        Path serviceDir = tempDir.resolve("com/example/demo");
        Files.createDirectories(serviceDir);

        String sampleSource = """
                package com.example.demo;

                import org.springframework.stereotype.Service;

                @Service
                public class ChainedWorkflowService {

                    public void unrelatedMethod() {
                        System.out.println("not invoked");
                    }

                    public void startWorkflow() {
                        System.out.println("workflow start");
                        this.processIntermediate();
                    }

                    private void processIntermediate() {
                        System.out.println("intermediate step");
                        finishWorkflow();
                    }

                    private void finishWorkflow() {
                        System.out.println("workflow done");
                    }
                }
                """;
        Files.writeString(serviceDir.resolve("ChainedWorkflowService.java"), sampleSource);

        List<MethodBodySliceDto> slices = sliceExpander.expandMethodBodiesWithCallees(
                tempDir,
                "ChainedWorkflowService",
                List.of("startWorkflow")
        );

        // startWorkflow -> processIntermediate -> finishWorkflow
        // Order must be declaration order: startWorkflow, processIntermediate, finishWorkflow
        assertThat(slices.stream().map(MethodBodySliceDto::methodName).toList())
                .containsExactly("startWorkflow", "processIntermediate", "finishWorkflow");
        assertThat(slices.stream().map(MethodBodySliceDto::methodName).toList())
                .doesNotContain("unrelatedMethod");
    }

    @Test
    @DisplayName("expandMethodBodiesWithCallees handles recursion and mutual calls safely")
    void expandMethodBodiesWithCalleesRecursionSafety(@TempDir Path tempDir) throws IOException {
        Path serviceDir = tempDir.resolve("com/example/demo");
        Files.createDirectories(serviceDir);

        String sampleSource = """
                package com.example.demo;

                import org.springframework.stereotype.Service;

                @Service
                public class RecursiveService {

                    public void selfRecursive(int count) {
                        if (count > 0) {
                            this.selfRecursive(count - 1);
                        }
                    }

                    public void ping() {
                        this.pong();
                    }

                    public void pong() {
                        ping();
                    }
                }
                """;
        Files.writeString(serviceDir.resolve("RecursiveService.java"), sampleSource);

        List<MethodBodySliceDto> selfSlices = sliceExpander.expandMethodBodiesWithCallees(
                tempDir,
                "RecursiveService",
                List.of("selfRecursive")
        );
        assertThat(selfSlices).hasSize(1);
        assertThat(selfSlices.get(0).methodName()).isEqualTo("selfRecursive");

        List<MethodBodySliceDto> mutualSlices = sliceExpander.expandMethodBodiesWithCallees(
                tempDir,
                "RecursiveService",
                List.of("ping")
        );
        assertThat(mutualSlices.stream().map(MethodBodySliceDto::methodName).toList())
                .containsExactly("ping", "pong");
    }

    @Test
    @DisplayName("Resolves and compacts sibling src/test/java when sourceRoot points to src/main/java")
    void resolvesAndCompactsSiblingTestRootWhenSourceRootIsMainJava(@TempDir Path root) throws IOException {
        Path mainJava = root.resolve("src/main/java/com/builtbyjuls/clearing/trade");
        Path testJava = root.resolve("src/test/java/com/builtbyjuls/clearing/trade");
        Path targetDir = root.resolve("target/classes");
        Files.createDirectories(mainJava);
        Files.createDirectories(testJava);
        Files.createDirectories(targetDir);

        String serviceSource = """
                package com.builtbyjuls.clearing.trade;
                import org.springframework.stereotype.Service;
                @Service
                public class TradeClearingService {
                    public void clearTrade(String ref) {}
                }
                """;
        Files.writeString(mainJava.resolve("TradeClearingService.java"), serviceSource);

        String testSource = """
                package com.builtbyjuls.clearing.trade;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                public class TradeClearingServiceTests {
                    @BeforeEach
                    void setUp() {}

                    @Test
                    void testClearTradeSuccess() {
                        helperCheck();
                    }

                    private void helperCheck() {}
                }
                """;
        Files.writeString(testJava.resolve("TradeClearingServiceTests.java"), testSource);

        // Put a file in target/ that should be ignored when walking
        Files.writeString(targetDir.resolve("IgnoredClass.java"), "public class IgnoredClass {}");

        Path mainRoot = root.resolve("src/main/java");

        // 1. compactSlice with sourceRoot = src/main/java finds test in sibling src/test/java
        SliceCompactionReportDto sliceReport = sliceExpander.compactSlice(mainRoot, "TradeClearingServiceTests");
        assertThat(sliceReport.totalFilesCompacted()).isEqualTo(1);
        assertThat(sliceReport.files().get(0).className()).isEqualTo("TradeClearingServiceTests");
        assertThat(sliceReport.files().get(0).stereotype()).isEqualTo(com.builtbyjuls.compactor.model.SpringStereotype.TEST);

        // 2. expandMethodBody with sourceRoot = src/main/java
        List<MethodBodySliceDto> singleSlices = sliceExpander.expandMethodBody(
                mainRoot,
                "TradeClearingServiceTests",
                "testClearTradeSuccess"
        );
        assertThat(singleSlices).hasSize(1);
        assertThat(singleSlices.get(0).methodName()).isEqualTo("testClearTradeSuccess");
        assertThat(singleSlices.get(0).filePath()).isEqualTo("com/builtbyjuls/clearing/trade/TradeClearingServiceTests.java");

        // 3. expandMethodBodiesWithCallees with sourceRoot = src/main/java
        List<MethodBodySliceDto> calleeSlices = sliceExpander.expandMethodBodiesWithCallees(
                mainRoot,
                "TradeClearingServiceTests",
                List.of("testClearTrade*")
        );
        assertThat(calleeSlices.stream().map(MethodBodySliceDto::methodName).toList())
                .containsExactly("testClearTradeSuccess", "helperCheck");
        assertThat(calleeSlices.get(0).filePath()).isEqualTo("com/builtbyjuls/clearing/trade/TradeClearingServiceTests.java");
    }
}
