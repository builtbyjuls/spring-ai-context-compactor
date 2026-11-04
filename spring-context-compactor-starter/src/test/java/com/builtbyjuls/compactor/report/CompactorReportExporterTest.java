package com.builtbyjuls.compactor.report;

import com.builtbyjuls.compactor.advisor.SpringContextCompactorAdvisor;
import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.mcp.McpCompactorDispatcher;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactorReportExporterTest {

    @TempDir
    Path tempDir;

    private Path sourceRoot;
    private Path reportDir;
    private CompactorProperties properties;
    private CompactorEfficiencyBenchmarkService benchmarkService;
    private CompactorReportExporter reportExporter;

    @BeforeEach
    void setUp() throws IOException {
        sourceRoot = tempDir.resolve("src/main/java");
        reportDir = tempDir.resolve("target/compactor-reports");
        Files.createDirectories(sourceRoot.resolve("com/example/demo"));

        writeSampleSources(sourceRoot.resolve("com/example/demo"));

        properties = new CompactorProperties();
        properties.setSourceRoot(sourceRoot.toString());
        properties.setReportOutputDir(reportDir.toString());
        properties.setDetectProxyTraps(true);
        properties.setIncludeLineNumbers(true);
        properties.setLogConsoleSummary(true);
        properties.setFailOnProxyTraps(false);

        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        SpringAstCompactor astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);
        SpringSliceExpander sliceExpander = new SpringSliceExpander(astCompactor, trapScanner, properties);
        CompactionBudgetService budgetService = new CompactionBudgetService(astCompactor);
        SpringContextCompactorAdvisor advisor = new SpringContextCompactorAdvisor(astCompactor, properties);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        McpCompactorDispatcher mcpDispatcher = new McpCompactorDispatcher(
                astCompactor, sliceExpander, budgetService, properties, objectMapper
        );

        benchmarkService = new CompactorEfficiencyBenchmarkService(
                astCompactor, sliceExpander, budgetService, advisor, mcpDispatcher
        );
        reportExporter = new CompactorReportExporter(
                budgetService, benchmarkService, properties, objectMapper
        );
    }

    @Test
    @DisplayName("BenchmarkMetricRowDto and CompactorBenchmarkReportDto validate required fields")
    void dtoValidationChecks() {
        assertThatThrownBy(() -> new BenchmarkMetricRowDto(null, "name", "base", "res", "gain", "detail"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompactorBenchmarkReportDto(
                null, 0, 0, 0, 0L, 0L, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0, 0, 0.0, 0, 0, 0, 0.0, 0, 0, 0.0, 0, null
        )).isInstanceOf(NullPointerException.class);

        CompactorBenchmarkReportDto emptyMetricsDto = new CompactorBenchmarkReportDto(
                "src/main/java", 0, 0, 0, 0L, 0L, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0, 0, 0.0, 0, 0, 0, 0.0, 0, 0, 0.0, 0, null
        );
        assertThat(emptyMetricsDto.metrics()).isEmpty();
    }

    @Test
    @DisplayName("runBenchmark populates all 5 portfolio metrics with positive reduction and 100% contract and trap recall")
    void runBenchmarkPopulatesAllFivePortfolioMetrics() {
        CompactorBenchmarkReportDto report = benchmarkService.runBenchmark(sourceRoot);

        assertThat(report.scannedJavaFiles()).isEqualTo(4);
        assertThat(report.rawSourceTotalLines()).isGreaterThan(report.compactedTotalLines());
        assertThat(report.rawSourceTotalBytes()).isGreaterThan(report.compactedTotalBytes());
        assertThat(report.rawSourceEstimatedTokens()).isGreaterThan(report.compactedEstimatedTokens());
        assertThat(report.repositoryTokenReductionPercent()).isGreaterThan(40.0);

        assertThat(report.rawSurgicalEditTokens()).isGreaterThan(report.compactedSurgicalEditTokens());
        assertThat(report.surgicalEditTokenReductionPercent()).isGreaterThan(40.0);
        assertThat(report.mcpSurgicalWorkflowLatencyMs()).isGreaterThanOrEqualTo(0.0);

        assertThat(report.totalSpringContractsEvaluated()).isGreaterThan(0);
        assertThat(report.retainedSpringContracts()).isEqualTo(report.totalSpringContractsEvaluated());
        assertThat(report.springContractRetentionPercent()).isEqualTo(100.0);

        assertThat(report.totalSeededProxyTraps()).isEqualTo(2);
        assertThat(report.inlineRetainedProxyTraps()).isEqualTo(2);
        assertThat(report.naiveSkeletonDetectedTraps()).isEqualTo(0);
        assertThat(report.inlineProxyTrapRecallPercent()).isEqualTo(100.0);

        assertThat(report.advisorPromptOriginalTokens()).isGreaterThan(report.advisorPromptCompactedTokens());
        assertThat(report.advisorPromptTokenReductionPercent()).isGreaterThan(40.0);
        assertThat(report.projectedSavedTokensPer1000Prompts()).isGreaterThan(0);

        List<BenchmarkMetricRowDto> metrics = report.metrics();
        assertThat(metrics).hasSize(5);
        assertThat(metrics)
                .extracting(BenchmarkMetricRowDto::metricKey)
                .containsExactly(
                        "repositoryTokenConsumption",
                        "surgicalEditWorkflow",
                        "springContractRetention",
                        "inlineProxyTrapDetection",
                        "springAiAdvisorSavings"
                );
    }

    @Test
    @DisplayName("exportReports writes all 4 report artifacts in outputDir")
    void exportReportsWritesAllFourFiles() throws IOException {
        Path exportedDir = reportExporter.exportReports(sourceRoot);

        assertThat(exportedDir).isEqualTo(reportDir);
        Path budgetJson = exportedDir.resolve("compaction-budget.json");
        Path benchmarkJson = exportedDir.resolve("benchmark-report.json");
        Path summaryMd = exportedDir.resolve("compactor-summary.md");
        Path junitXml = exportedDir.resolve("junit-compactor.xml");

        assertThat(budgetJson).exists();
        assertThat(benchmarkJson).exists();
        assertThat(summaryMd).exists();
        assertThat(junitXml).exists();

        String mdContent = Files.readString(summaryMd, StandardCharsets.UTF_8);
        assertThat(mdContent).contains("# Spring AI Context Compactor Report");
        assertThat(mdContent).contains("## Overview");
        assertThat(mdContent).contains("## Portfolio Evidence & Performance Gains Matrix");
        assertThat(mdContent).contains("## Per-Stereotype Compaction Breakdown");
        assertThat(mdContent).contains("## Detected @Transactional Proxy Traps");
        assertThat(mdContent).contains("## Compaction Architecture Flow");
        assertThat(mdContent).contains("```mermaid");
        assertThat(mdContent).contains("flowchart LR");

        String xmlContent = Files.readString(junitXml, StandardCharsets.UTF_8);
        assertThat(xmlContent).contains("<testsuite name=\"spring-ai-context-compactor\"");
        assertThat(xmlContent).contains("failures=\"0\"");
        assertThat(xmlContent).contains("<testcase");
        assertThat(xmlContent).doesNotContain("<failure");
    }

    @Test
    @DisplayName("exportReports with failOnProxyTraps=true writes all 4 files with JUnit failures before throwing CompactorTrapViolationException")
    void exportReportsThrowsAfterWritingFilesWhenFailOnProxyTrapsEnabled() throws IOException {
        properties.setFailOnProxyTraps(true);

        assertThatThrownBy(() -> reportExporter.exportReports(sourceRoot))
                .isInstanceOf(CompactorTrapViolationException.class)
                .hasMessageContaining("SELF_INVOCATION_BYPASS");

        Path budgetJson = reportDir.resolve("compaction-budget.json");
        Path benchmarkJson = reportDir.resolve("benchmark-report.json");
        Path summaryMd = reportDir.resolve("compactor-summary.md");
        Path junitXml = reportDir.resolve("junit-compactor.xml");

        assertThat(budgetJson).exists();
        assertThat(benchmarkJson).exists();
        assertThat(summaryMd).exists();
        assertThat(junitXml).exists();

        String xmlContent = Files.readString(junitXml, StandardCharsets.UTF_8);
        assertThat(xmlContent).contains("<failure");
        assertThat(xmlContent).contains("SELF_INVOCATION_BYPASS");
    }

    private void writeSampleSources(Path pkgDir) throws IOException {
        Files.writeString(pkgDir.resolve("SampleEntity.java"), """
                package com.example.demo;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import jakarta.persistence.Version;
                import java.math.BigDecimal;
                import java.time.Instant;
                import java.util.Objects;

                @Entity
                @Table(name = "sample_orders")
                public class SampleEntity {

                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;

                    @Column(name = "order_ref", nullable = false, unique = true, length = 64)
                    private String orderRef;

                    @Column(name = "notional_amount", nullable = false, precision = 19, scale = 4)
                    private BigDecimal notionalAmount;

                    @Column(name = "status", nullable = false, length = 32)
                    private String status;

                    @Column(name = "created_at", nullable = false)
                    private Instant createdAt;

                    @Version
                    @Column(name = "version")
                    private Long version;

                    protected SampleEntity() {
                    }

                    public SampleEntity(Long id, String orderRef, BigDecimal notionalAmount, String status, Instant createdAt) {
                        this.id = id;
                        this.orderRef = orderRef;
                        this.notionalAmount = notionalAmount;
                        this.status = status;
                        this.createdAt = createdAt;
                    }

                    public Long getId() {
                        return id;
                    }

                    public void setId(Long id) {
                        this.id = id;
                    }

                    public String getOrderRef() {
                        return orderRef;
                    }

                    public void setOrderRef(String orderRef) {
                        this.orderRef = orderRef;
                    }

                    public BigDecimal getNotionalAmount() {
                        return notionalAmount;
                    }

                    public void setNotionalAmount(BigDecimal notionalAmount) {
                        this.notionalAmount = notionalAmount;
                    }

                    public String getStatus() {
                        return status;
                    }

                    public void setStatus(String status) {
                        this.status = status;
                    }

                    public Instant getCreatedAt() {
                        return createdAt;
                    }

                    public void setCreatedAt(Instant createdAt) {
                        this.createdAt = createdAt;
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
                        SampleEntity that = (SampleEntity) o;
                        return Objects.equals(orderRef, that.orderRef);
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(orderRef);
                    }

                    @Override
                    public String toString() {
                        return "SampleEntity{" +
                                "id=" + id +
                                ", orderRef='" + orderRef + '\\'' +
                                ", notionalAmount=" + notionalAmount +
                                ", status='" + status + '\\'' +
                                ", createdAt=" + createdAt +
                                '}';
                    }

                    public static class Builder {
                        private Long id;
                        private String orderRef;
                        private BigDecimal notionalAmount;
                        private String status;
                        private Instant createdAt;

                        public Builder id(Long id) {
                            this.id = id;
                            return this;
                        }

                        public Builder orderRef(String orderRef) {
                            this.orderRef = orderRef;
                            return this;
                        }

                        public Builder notionalAmount(BigDecimal notionalAmount) {
                            this.notionalAmount = notionalAmount;
                            return this;
                        }

                        public Builder status(String status) {
                            this.status = status;
                            return this;
                        }

                        public Builder createdAt(Instant createdAt) {
                            this.createdAt = createdAt;
                            return this;
                        }

                        public SampleEntity build() {
                            return new SampleEntity(id, orderRef, notionalAmount, status, createdAt);
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkgDir.resolve("SampleRepository.java"), """
                package com.example.demo;

                import java.util.List;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.data.repository.query.Param;

                public interface SampleRepository extends JpaRepository<SampleEntity, Long> {

                    @Query("SELECT s FROM SampleEntity s WHERE s.status = :status")
                    List<SampleEntity> findByStatusCustom(@Param("status") String status);
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkgDir.resolve("SampleService.java"), """
                package com.example.demo;

                import java.math.BigDecimal;
                import java.time.Instant;
                import java.util.List;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;

                @Service
                public class SampleService {

                    private final SampleRepository sampleRepository;

                    public SampleService(SampleRepository sampleRepository) {
                        this.sampleRepository = sampleRepository;
                    }

                    @Transactional
                    public SampleEntity processOrder(String orderRef, BigDecimal notional) {
                        validateOrder(orderRef, notional);
                        SampleEntity entity = new SampleEntity(null, orderRef, notional, "CLEARED", Instant.now());
                        SampleEntity saved = sampleRepository.save(entity);
                        normalizeAuditStatus(saved.getOrderRef());
                        return saved;
                    }

                    @Transactional(propagation = Propagation.REQUIRES_NEW)
                    public void recordAudit(String orderRef) {
                        SampleEntity entity = new SampleEntity(null, orderRef, BigDecimal.ONE, "AUDIT", Instant.now());
                        sampleRepository.save(entity);
                    }

                    @Transactional(readOnly = true)
                    public List<SampleEntity> inspectActiveOrders(String status) {
                        List<SampleEntity> items = sampleRepository.findByStatusCustom(status);
                        this.recordAudit("INSPECT-" + status);
                        return items;
                    }

                    private void validateOrder(String orderRef, BigDecimal notional) {
                        if (orderRef == null || orderRef.isBlank()) {
                            throw new IllegalArgumentException("orderRef must not be blank");
                        }
                        if (notional == null || notional.compareTo(BigDecimal.ZERO) <= 0) {
                            throw new IllegalArgumentException("notional must be positive");
                        }
                    }

                    private void normalizeAuditStatus(String orderRef) {
                        if (orderRef.length() > 64) {
                            throw new IllegalStateException("orderRef exceeds max length");
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkgDir.resolve("SampleController.java"), """
                package com.example.demo;

                import java.math.BigDecimal;
                import java.util.List;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/v1/orders")
                public class SampleController {

                    private final SampleService sampleService;

                    public SampleController(SampleService sampleService) {
                        this.sampleService = sampleService;
                    }

                    @PostMapping
                    public ResponseEntity<SampleEntity> createOrder(
                            @RequestParam("orderRef") String orderRef,
                            @RequestParam("notional") BigDecimal notional) {
                        SampleEntity created = sampleService.processOrder(orderRef, notional);
                        return ResponseEntity.ok(created);
                    }

                    @GetMapping
                    public ResponseEntity<List<SampleEntity>> getOrders(@RequestParam("status") String status) {
                        return ResponseEntity.ok(sampleService.inspectActiveOrders(status));
                    }
                }
                """, StandardCharsets.UTF_8);
    }
}
