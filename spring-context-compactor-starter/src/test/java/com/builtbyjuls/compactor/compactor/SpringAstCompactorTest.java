package com.builtbyjuls.compactor.compactor;

import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
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

class SpringAstCompactorTest {

    private SpringAstCompactor compactor;

    @BeforeEach
    void setUp() {
        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        CompactorProperties properties = new CompactorProperties();
        compactor = new SpringAstCompactor(classifier, trapScanner, properties);
    }

    @Test
    @DisplayName("Compacts verbose 150+ line @Entity, stripping getters/setters/builder with >65% token reduction")
    void compactsEntityWithSubstantialTokenReduction() {
        String entitySource = """
                package com.builtbyjuls.clearing.trade;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.EnumType;
                import jakarta.persistence.Enumerated;
                import jakarta.persistence.FetchType;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;
                import jakarta.persistence.JoinColumn;
                import jakarta.persistence.ManyToOne;
                import jakarta.persistence.Table;
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

        CompactedFileResultDto result = compactor.compactSource(
                "src/main/java/com/builtbyjuls/clearing/trade/ClearedTrade.java",
                entitySource
        );

        assertThat(result.stereotype()).isEqualTo(SpringStereotype.ENTITY);
        assertThat(result.className()).isEqualTo("ClearedTrade");
        assertThat(result.packageName()).isEqualTo("com.builtbyjuls.clearing.trade");
        assertThat(result.originalLines()).isGreaterThanOrEqualTo(150);
        assertThat(result.tokenReductionPercent()).isGreaterThan(65.0);

        String skeleton = result.compactedSkeleton();
        assertThat(skeleton).contains("// [SPRING COMPACTOR] File: src/main/java/com/builtbyjuls/clearing/trade/ClearedTrade.java");
        assertThat(skeleton).contains("Stereotype: ENTITY");
        assertThat(skeleton).contains("@Entity");
        assertThat(skeleton).contains("@Table(name = \"cleared_trades\")");
        assertThat(skeleton).contains("@Id");
        assertThat(skeleton).contains("@GeneratedValue(strategy = GenerationType.UUID)");
        assertThat(skeleton).contains("@Column(name = \"trade_id\", nullable = false, updatable = false)");
        assertThat(skeleton).contains("private UUID tradeId;");
        assertThat(skeleton).contains("@ManyToOne(fetch = FetchType.LAZY)");
        assertThat(skeleton).contains("@JoinColumn(name = \"clearing_member_id\", nullable = false)");
        assertThat(skeleton).contains("private ClearingMember clearingMember;");
        assertThat(skeleton).contains("@Version");

        // Verify boilerplate stripped
        assertThat(skeleton).doesNotContain("getTradeId()");
        assertThat(skeleton).doesNotContain("setTradeId");
        assertThat(skeleton).doesNotContain("public boolean equals(");
        assertThat(skeleton).doesNotContain("public int hashCode()");
        assertThat(skeleton).doesNotContain("public String toString()");
        assertThat(skeleton).doesNotContain("class Builder");
    }

    @Test
    @DisplayName("Compacts @Service, preserving injected beans and surfacing all 3 proxy trap types inline")
    void compactsServiceAndSurfacesProxyTrapsInline() {
        String serviceSource = """
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
                    public ClearedTrade verifyAndNovate(UUID tradeId) {
                        this.recordInternalComplianceCheck(tradeId);
                        return this.tradeRepository.findById(tradeId).orElseThrow();
                    }

                    @Transactional
                    public void executeTradeSettlement(UUID tradeId, BigDecimal amount) {
                        tradeRepository.updateSettlement(tradeId, amount);
                    }

                    @Transactional
                    protected void recordInternalComplianceCheck(UUID tradeId) {
                        auditService.recordComplianceCheck(tradeId);
                    }
                }
                """;

        CompactedFileResultDto result = compactor.compactSource(
                "src/main/java/com/builtbyjuls/clearing/service/TradeClearingService.java",
                serviceSource
        );

        assertThat(result.stereotype()).isEqualTo(SpringStereotype.SERVICE);
        assertThat(result.injectedDependencies()).containsExactly("ClearedTradeRepository", "ClearingAuditService");

        // Verify trap notes detected in result
        assertThat(result.trapNotes())
                .extracting(t -> t.trapType())
                .contains(
                        ProxyTrapType.SELF_INVOCATION_BYPASS,
                        ProxyTrapType.READ_ONLY_CALLING_WRITE_REQUIRED,
                        ProxyTrapType.NON_PUBLIC_TRANSACTIONAL
                );

        String skeleton = result.compactedSkeleton();
        assertThat(skeleton).contains("// Injected Beans: [ClearedTradeRepository, ClearingAuditService]");
        assertThat(skeleton).contains("private final ClearedTradeRepository tradeRepository;");
        assertThat(skeleton).contains("private final ClearingAuditService auditService;");

        // Verify line markers and inline trap notes
        assertThat(skeleton).contains("/* L");
        assertThat(skeleton).contains("body omitted");
        assertThat(skeleton).contains("[TRAP: SELF_INVOCATION_BYPASS]");
        assertThat(skeleton).contains("[TRAP: READ_ONLY_CALLING_WRITE_REQUIRED]");
        assertThat(skeleton).contains("[TRAP: NON_PUBLIC_TRANSACTIONAL]");

        // Verify DI constructor was stripped
        assertThat(skeleton).doesNotContain("public TradeClearingService(ClearedTradeRepository");
    }

    @Test
    @DisplayName("Compacts @RestController and @Repository interface, preserving @Query JPQL strings and mapping annotations")
    void compactsRestControllerAndRepositoryPreservingQueries() {
        String controllerSource = """
                package com.builtbyjuls.clearing.controller;

                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.http.ResponseEntity;
                import jakarta.validation.Valid;
                import java.util.UUID;
                import com.builtbyjuls.clearing.service.TradeClearingService;
                import com.builtbyjuls.clearing.dto.TradeNovationRequest;
                import com.builtbyjuls.clearing.dto.ClearedTradeResponse;

                @RestController
                @RequestMapping("/api/v1/clearing")
                public class TradeClearingController {

                    private final TradeClearingService clearingService;

                    public TradeClearingController(TradeClearingService clearingService) {
                        this.clearingService = clearingService;
                    }

                    @PostMapping("/novate")
                    public ResponseEntity<ClearedTradeResponse> novateTrade(@Valid @RequestBody TradeNovationRequest request) {
                        ClearedTradeResponse response = clearingService.novate(request);
                        return ResponseEntity.ok(response);
                    }

                    @GetMapping("/trades/{tradeId}")
                    public ResponseEntity<ClearedTradeResponse> getTrade(@PathVariable UUID tradeId) {
                        return ResponseEntity.ok(clearingService.getTrade(tradeId));
                    }
                }
                """;

        CompactedFileResultDto controllerResult = compactor.compactSource(
                "src/main/java/com/builtbyjuls/clearing/controller/TradeClearingController.java",
                controllerSource
        );

        assertThat(controllerResult.stereotype()).isEqualTo(SpringStereotype.CONTROLLER);
        String controllerSkeleton = controllerResult.compactedSkeleton();
        assertThat(controllerSkeleton).contains("@RestController");
        assertThat(controllerSkeleton).contains("@RequestMapping(\"/api/v1/clearing\")");
        assertThat(controllerSkeleton).contains("@PostMapping(\"/novate\")");
        assertThat(controllerSkeleton).contains("@Valid @RequestBody TradeNovationRequest request");
        assertThat(controllerSkeleton).contains("@GetMapping(\"/trades/{tradeId}\")");
        assertThat(controllerSkeleton).contains("@PathVariable UUID tradeId");

        String repositorySource = """
                package com.builtbyjuls.clearing.repository;

                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.data.jpa.repository.Modifying;
                import org.springframework.data.repository.query.Param;
                import org.springframework.stereotype.Repository;
                import java.util.UUID;
                import java.util.List;
                import java.util.Optional;
                import com.builtbyjuls.clearing.trade.ClearedTrade;
                import com.builtbyjuls.clearing.model.TradeStatus;

                @Repository
                public interface ClearedTradeRepository extends JpaRepository<ClearedTrade, UUID> {

                    @Query("SELECT t FROM ClearedTrade t WHERE t.clearingMember.id = :memberId AND t.status = :status")
                    List<ClearedTrade> findByMemberAndStatus(@Param("memberId") Long memberId, @Param("status") TradeStatus status);

                    @Modifying
                    @Query("UPDATE ClearedTrade t SET t.status = :status WHERE t.tradeId = :tradeId")
                    int updateStatus(@Param("tradeId") UUID tradeId, @Param("status") TradeStatus status);
                }
                """;

        CompactedFileResultDto repoResult = compactor.compactSource(
                "src/main/java/com/builtbyjuls/clearing/repository/ClearedTradeRepository.java",
                repositorySource
        );

        assertThat(repoResult.stereotype()).isEqualTo(SpringStereotype.REPOSITORY);
        String repoSkeleton = repoResult.compactedSkeleton();
        assertThat(repoSkeleton).contains("public interface ClearedTradeRepository extends JpaRepository<ClearedTrade, UUID>");
        assertThat(repoSkeleton).contains("@Query(\"SELECT t FROM ClearedTrade t WHERE t.clearingMember.id = :memberId AND t.status = :status\")");
        assertThat(repoSkeleton).contains("List<ClearedTrade> findByMemberAndStatus(@Param(\"memberId\") Long memberId, @Param(\"status\") TradeStatus status);");
        assertThat(repoSkeleton).contains("@Modifying");
        assertThat(repoSkeleton).contains("@Query(\"UPDATE ClearedTrade t SET t.status = :status WHERE t.tradeId = :tradeId\")");
        assertThat(repoSkeleton).contains("int updateStatus(@Param(\"tradeId\") UUID tradeId, @Param(\"status\") TradeStatus status);");
    }

    @Test
    @DisplayName("Compacts @Configuration preserving single-line @Bean return statements and omitting multi-line bodies")
    void compactsConfigurationWithBeanMethods() {
        String configSource = """
                package com.builtbyjuls.clearing.config;

                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Primary;
                import com.builtbyjuls.clearing.margin.MarginCalculator;
                import com.builtbyjuls.clearing.margin.StandardSpanMarginCalculator;

                @Configuration
                public class ClearingMarginConfig {

                    @Bean
                    @Primary
                    public MarginCalculator standardMarginCalculator() {
                        return new StandardSpanMarginCalculator();
                    }

                    @Bean
                    public MarginCalculator complexMarginCalculator() {
                        StandardSpanMarginCalculator calc = new StandardSpanMarginCalculator();
                        calc.setThreshold(5000);
                        return calc;
                    }
                }
                """;

        CompactedFileResultDto result = compactor.compactSource(
                "src/main/java/com/builtbyjuls/clearing/config/ClearingMarginConfig.java",
                configSource
        );

        assertThat(result.stereotype()).isEqualTo(SpringStereotype.CONFIGURATION);
        String skeleton = result.compactedSkeleton();
        assertThat(skeleton).contains("@Configuration");
        assertThat(skeleton).contains("@Bean");
        assertThat(skeleton).contains("@Primary");
        assertThat(skeleton).contains("return new StandardSpanMarginCalculator();");
        assertThat(skeleton).contains("body omitted");
    }

    @Test
    @DisplayName("compactFile reads source from file system and outputs relative path in banner")
    void compactFileReadsFromPath(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("com/builtbyjuls/demo");
        Files.createDirectories(packageDir);

        Path javaFile = packageDir.resolve("DemoService.java");
        String code = """
                package com.builtbyjuls.demo;

                import org.springframework.stereotype.Service;

                @Service
                public class DemoService {
                    public void run() {
                        System.out.println("Demo");
                    }
                }
                """;
        Files.writeString(javaFile, code);

        CompactedFileResultDto result = compactor.compactFile(sourceRoot, javaFile);

        assertThat(result.filePath()).isEqualTo("com/builtbyjuls/demo/DemoService.java");
        assertThat(result.stereotype()).isEqualTo(SpringStereotype.SERVICE);
        assertThat(result.compactedSkeleton()).contains("// [SPRING COMPACTOR] File: com/builtbyjuls/demo/DemoService.java");
    }

    @Test
    @DisplayName("compactFile normalizes mixed relative and absolute paths without throwing")
    void compactFileNormalizesMixedPaths(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("com/builtbyjuls/demo");
        Files.createDirectories(packageDir);

        Path javaFile = packageDir.resolve("DemoService.java");
        String code = """
                package com.builtbyjuls.demo;

                import org.springframework.stereotype.Service;

                @Service
                public class DemoService {
                    public void run() {
                        System.out.println("Demo");
                    }
                }
                """;
        Files.writeString(javaFile, code);

        // Call with absolute javaFile and absolute sourceRoot
        CompactedFileResultDto result = compactor.compactFile(sourceRoot.toAbsolutePath(), javaFile);
        assertThat(result.filePath()).isEqualTo("com/builtbyjuls/demo/DemoService.java");
    }

    @Test
    @DisplayName("honors includeLineNumbers=false by omitting line range prefix")
    void honorsIncludeLineNumbersDisabled() {
        CompactorProperties customProps = new CompactorProperties();
        customProps.setIncludeLineNumbers(false);
        SpringAstCompactor noLineCompactor = new SpringAstCompactor(
                new StereotypeClassifier(),
                new SpringProxyTrapScanner(),
                customProps
        );

        String code = """
                package com.builtbyjuls.demo;

                import org.springframework.stereotype.Service;

                @Service
                public class DemoService {
                    public DemoService() {
                        System.out.println("Init");
                    }

                    public void run() {
                        System.out.println("Demo");
                    }
                }
                """;

        CompactedFileResultDto result = noLineCompactor.compactSource("DemoService.java", code);
        String skeleton = result.compactedSkeleton();
        assertThat(skeleton).contains("/* body omitted (");
        assertThat(skeleton).doesNotContain("/* L");
    }

    @Test
    @DisplayName("Strips redundant @Override annotations from methods while preserving @Transactional and mapping annotations")
    void stripsOverrideAnnotationWhilePreservingSpringWiring() {
        String serviceWithOverride = """
                package com.builtbyjuls.clearing.service;

                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                @Service
                public class TradeServiceImpl implements TradeService {

                    @Override
                    @Transactional
                    public void clearTrade(String tradeRef) {
                        System.out.println("clearing " + tradeRef);
                    }

                    @Override
                    public String getStatus(String tradeRef) {
                        return "CLEARED";
                    }
                }
                """;

        CompactedFileResultDto result = compactor.compactSource("TradeServiceImpl.java", serviceWithOverride);
        String skeleton = result.compactedSkeleton();

        assertThat(skeleton).doesNotContain("@Override");
        assertThat(skeleton).contains("@Transactional");
        assertThat(skeleton).contains("public void clearTrade(String tradeRef)");
        assertThat(skeleton).contains("public String getStatus(String tradeRef)");

        assertThat(result.methods()).hasSize(2);
        assertThat(result.methods().get(0).retainedAnnotations()).contains("@Transactional");
        assertThat(result.methods().get(0).retainedAnnotations()).noneMatch(a -> a.contains("Override"));
        assertThat(result.methods().get(1).retainedAnnotations()).noneMatch(a -> a.contains("Override"));
    }
}
