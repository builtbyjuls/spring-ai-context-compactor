package com.builtbyjuls.compactor.report;

import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.StereotypeBudgetRowDto;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CompactorReportExporter {

    private static final Logger log = LoggerFactory.getLogger(CompactorReportExporter.class);

    private final CompactionBudgetService budgetService;
    private final CompactorEfficiencyBenchmarkService benchmarkService;
    private final CompactorProperties properties;
    private final ObjectMapper objectMapper;

    public CompactorReportExporter(
            CompactionBudgetService budgetService,
            CompactorEfficiencyBenchmarkService benchmarkService,
            CompactorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.budgetService = Objects.requireNonNull(budgetService, "budgetService must not be null");
        this.benchmarkService = Objects.requireNonNull(benchmarkService, "benchmarkService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public Path exportReports(Path sourceRoot) {
        Objects.requireNonNull(sourceRoot, "sourceRoot must not be null");

        Path outputDir = Path.of(properties.getReportOutputDir());
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create report output directory: " + outputDir, e);
        }

        ProjectCompactionBudgetDto budget = budgetService.analyzeProjectBudget(sourceRoot);
        CompactorBenchmarkReportDto benchmark = benchmarkService.runBenchmark(sourceRoot);
        Map<MethodTrapNoteDto, String> trapClassNames = benchmarkService.resolveTrapClassNames(sourceRoot);

        try {
            String budgetJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(budget);
            Files.writeString(outputDir.resolve("compaction-budget.json"), budgetJson, StandardCharsets.UTF_8);

            String benchmarkJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(benchmark);
            Files.writeString(outputDir.resolve("benchmark-report.json"), benchmarkJson, StandardCharsets.UTF_8);

            String summaryMd = buildMarkdownSummary(budget, benchmark, trapClassNames);
            Files.writeString(outputDir.resolve("compactor-summary.md"), summaryMd, StandardCharsets.UTF_8);

            String junitXml = buildJunitXml(budget, trapClassNames, properties.isFailOnProxyTraps());
            Files.writeString(outputDir.resolve("junit-compactor.xml"), junitXml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write compactor report artifacts to: " + outputDir, e);
        }

        if (properties.isLogConsoleSummary()) {
            logConsoleBanner(budget, benchmark);
        }

        if (properties.isFailOnProxyTraps() && !budget.detectedTraps().isEmpty()) {
            String trapSummary = budget.detectedTraps().stream()
                    .map(t -> {
                        String cls = trapClassNames.getOrDefault(t, "UnknownClass");
                        return t.trapType().name() + " in " + cls + "#" + t.callerMethod() + " (L" + t.callSiteLine() + "): " + t.summary();
                    })
                    .collect(Collectors.joining("; "));
            throw new CompactorTrapViolationException(
                    "Detected " + budget.detectedTraps().size() + " @Transactional proxy trap(s): " + trapSummary
            );
        }

        return outputDir;
    }

    private String buildMarkdownSummary(
            ProjectCompactionBudgetDto budget,
            CompactorBenchmarkReportDto benchmark,
            Map<MethodTrapNoteDto, String> trapClassNames
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Spring AI Context Compactor Report\n\n");

        sb.append("## Overview\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("| --- | --- |\n");
        sb.append(String.format(Locale.ROOT, "| **Source Root** | `%s` |\n", budget.sourceRoot()));
        sb.append(String.format(Locale.ROOT, "| **Scanned Java Files** | `%d` |\n", budget.totalFiles()));
        sb.append(String.format(Locale.ROOT, "| **Original Lines** | `%,d` |\n", budget.originalTotalLines()));
        sb.append(String.format(Locale.ROOT, "| **Compacted Lines** | `%,d` |\n", budget.compactedTotalLines()));
        sb.append(String.format(Locale.ROOT, "| **Original Estimated Tokens** | `~%,d` |\n", budget.originalEstimatedTokens()));
        sb.append(String.format(Locale.ROOT, "| **Compacted Estimated Tokens** | `~%,d` |\n", budget.compactedEstimatedTokens()));
        sb.append(String.format(Locale.ROOT, "| **Tokens Saved** | `%,d` |\n", budget.tokensSaved()));
        sb.append(String.format(Locale.ROOT, "| **Overall Token Reduction %%** | **`%.1f%%`** |\n", budget.overallTokenReductionPercent()));
        sb.append(String.format(Locale.ROOT, "| **Detected `@Transactional` Proxy Traps** | `%d` |\n\n", budget.detectedTraps().size()));

        sb.append("## Portfolio Evidence & Performance Gains Matrix\n\n");
        sb.append("| Metric | Raw File / Naive Baseline | Spring Compactor Result | Measured Gain | Architectural Evidence |\n");
        sb.append("| --- | --- | --- | --- | --- |\n");
        for (BenchmarkMetricRowDto row : benchmark.metrics()) {
            sb.append(String.format(
                    Locale.ROOT,
                    "| **%s** | %s | %s | **%s** | %s |\n",
                    row.metricName(),
                    row.rawBaseline(),
                    row.compactedResult(),
                    row.measuredGain(),
                    row.evidenceDetail()
            ));
        }
        sb.append("\n");

        sb.append("## Per-Stereotype Compaction Breakdown\n\n");
        sb.append("| Stereotype | Files | Original Lines | Compacted Lines | Original Tokens | Compacted Tokens | Token Reduction % |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (StereotypeBudgetRowDto row : budget.stereotypeBreakdown()) {
            sb.append(String.format(
                    Locale.ROOT,
                    "| `%s` | %d | %,d | %,d | ~%,d | ~%,d | **%.1f%%** |\n",
                    row.stereotype().name(),
                    row.fileCount(),
                    row.originalLines(),
                    row.compactedLines(),
                    row.originalEstimatedTokens(),
                    row.compactedEstimatedTokens(),
                    row.tokenReductionPercent()
            ));
        }
        sb.append("\n");

        sb.append("## Detected @Transactional Proxy Traps\n\n");
        sb.append("| Class | Method | Line | Trap Type | Description |\n");
        sb.append("| --- | --- | --- | --- | --- |\n");
        if (budget.detectedTraps().isEmpty()) {
            sb.append("| _None_ | - | - | - | No proxy traps detected |\n");
        } else {
            for (MethodTrapNoteDto trap : budget.detectedTraps()) {
                String cls = trapClassNames.getOrDefault(trap, "UnknownClass");
                sb.append(String.format(
                        Locale.ROOT,
                        "| `%s` | `%s` | `L%d` | `%s` | %s |\n",
                        cls,
                        trap.callerMethod(),
                        trap.callSiteLine(),
                        trap.trapType().name(),
                        trap.summary()
                ));
            }
        }
        sb.append("\n");

        sb.append("## Compaction Architecture Flow\n\n");
        sb.append("```mermaid\n");
        sb.append("flowchart LR\n");
        sb.append("    Source[\".java Source Files\"] --> Scanner[\"SpringProxyTrapScanner (Pre-Prune)\"]\n");
        sb.append("    Scanner --> Compactor[\"SpringAstCompactor (Stereotype Rules)\"]\n");
        sb.append("    Compactor --> Delivery[\"MCP Tools & Spring AI CallAdvisor\"]\n");
        sb.append("```\n");

        return sb.toString();
    }

    private String buildJunitXml(
            ProjectCompactionBudgetDto budget,
            Map<MethodTrapNoteDto, String> trapClassNames,
            boolean failOnProxyTraps
    ) {
        List<StereotypeBudgetRowDto> stereotypes = budget.stereotypeBreakdown();
        List<MethodTrapNoteDto> traps = budget.detectedTraps();

        int totalTests = stereotypes.size() + Math.max(1, traps.size());
        int failures = failOnProxyTraps ? traps.size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(
                Locale.ROOT,
                "<testsuite name=\"spring-ai-context-compactor\" tests=\"%d\" failures=\"%d\" errors=\"0\" skipped=\"0\">\n",
                totalTests,
                failures
        ));

        for (StereotypeBudgetRowDto row : stereotypes) {
            sb.append(String.format(
                    Locale.ROOT,
                    "  <testcase classname=\"com.builtbyjuls.compactor.StereotypeCompaction\" name=\"compact_%s\">\n",
                    escapeXml(row.stereotype().name())
            ));
            sb.append(String.format(
                    Locale.ROOT,
                    "    <system-out>Files: %d, Original Tokens: %d, Compacted Tokens: %d, Reduction: %.1f%%</system-out>\n",
                    row.fileCount(),
                    row.originalEstimatedTokens(),
                    row.compactedEstimatedTokens(),
                    row.tokenReductionPercent()
            ));
            sb.append("  </testcase>\n");
        }

        if (traps.isEmpty()) {
            sb.append("  <testcase classname=\"com.builtbyjuls.compactor.ProxyTrapScanner\" name=\"verify_no_proxy_traps\">\n");
            sb.append("    <system-out>Zero @Transactional proxy traps detected.</system-out>\n");
            sb.append("  </testcase>\n");
        } else {
            for (int i = 0; i < traps.size(); i++) {
                MethodTrapNoteDto trap = traps.get(i);
                String cls = trapClassNames.getOrDefault(trap, "UnknownClass");
                String testName = String.format(Locale.ROOT, "trap_%d_%s_%s_%s_L%d",
                        i + 1, trap.trapType().name(), cls, trap.callerMethod(), trap.callSiteLine());
                sb.append(String.format(
                        Locale.ROOT,
                        "  <testcase classname=\"com.builtbyjuls.compactor.ProxyTrapScanner\" name=\"%s\">\n",
                        escapeXml(testName)
                ));
                String detail = String.format(
                        Locale.ROOT,
                        "[TRAP: %s] %s#%s -> %s at L%d: %s",
                        trap.trapType().name(),
                        cls,
                        trap.callerMethod(),
                        trap.targetMethod(),
                        trap.callSiteLine(),
                        trap.summary()
                );
                if (failOnProxyTraps) {
                    sb.append(String.format(
                            Locale.ROOT,
                            "    <failure message=\"%s\" type=\"%s\">%s</failure>\n",
                            escapeXml(trap.summary()),
                            escapeXml(trap.trapType().name()),
                            escapeXml(detail)
                    ));
                } else {
                    sb.append(String.format(
                            Locale.ROOT,
                            "    <system-out>%s</system-out>\n",
                            escapeXml(detail)
                    ));
                }
                sb.append("  </testcase>\n");
            }
        }

        sb.append("</testsuite>\n");
        return sb.toString();
    }

    private void logConsoleBanner(ProjectCompactionBudgetDto budget, CompactorBenchmarkReportDto benchmark) {
        String banner = String.format(
                Locale.ROOT,
                """
                
                +----------------------------------------------------------------------------------------------------+
                | SPRING AI CONTEXT COMPACTOR - PORTFOLIO BENCHMARK SUMMARY                                          |
                +----------------------------------------------------------------------------------------------------+
                | Scanned Java Files: %d | Raw Tokens: ~%,d | Compacted Tokens: ~%,d | Saved: %,d (-%.1f%%)
                | Surgical Edit Workflow: ~%,d -> ~%,d tokens (-%.1f%%) | MCP Latency: %.2f ms
                | Spring Contract Retention: %.1f%% (%d/%d) | Inline @Transactional Trap Recall: %.1f%% (%d/%d traps)
                | Spring AI CallAdvisor: ~%,d -> ~%,d tokens (-%.1f%%) | Saved/1k Prompts: %,d tokens
                +----------------------------------------------------------------------------------------------------+""",
                benchmark.scannedJavaFiles(),
                benchmark.rawSourceEstimatedTokens(),
                benchmark.compactedEstimatedTokens(),
                budget.tokensSaved(),
                benchmark.repositoryTokenReductionPercent(),
                benchmark.rawSurgicalEditTokens(),
                benchmark.compactedSurgicalEditTokens(),
                benchmark.surgicalEditTokenReductionPercent(),
                benchmark.mcpSurgicalWorkflowLatencyMs(),
                benchmark.springContractRetentionPercent(),
                benchmark.retainedSpringContracts(),
                benchmark.totalSpringContractsEvaluated(),
                benchmark.inlineProxyTrapRecallPercent(),
                benchmark.inlineRetainedProxyTraps(),
                benchmark.totalSeededProxyTraps(),
                benchmark.advisorPromptOriginalTokens(),
                benchmark.advisorPromptCompactedTokens(),
                benchmark.advisorPromptTokenReductionPercent(),
                benchmark.projectedSavedTokensPer1000Prompts()
        );
        log.info("{}", banner);
    }

    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
