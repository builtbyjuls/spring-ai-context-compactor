package com.builtbyjuls.clearing;

import com.builtbyjuls.compactor.report.BenchmarkMetricRowDto;
import com.builtbyjuls.compactor.report.CompactorBenchmarkReportDto;
import com.builtbyjuls.compactor.report.CompactorEfficiencyBenchmarkService;
import com.builtbyjuls.compactor.report.CompactorReportExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CompactorBenchmarkIntegrationTest extends ClearingTestContainersBase {

    @Autowired
    private CompactorEfficiencyBenchmarkService benchmarkService;

    @Autowired
    private CompactorReportExporter reportExporter;

    @Test
    @DisplayName("Runs full 5-metric portfolio benchmark and exports CI/CD reports for trade-clearing-compactor-demo")
    void runsFullPortfolioBenchmarkAndExportsCiCdReports() throws IOException {
        Path sourceRoot = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java")
                : Path.of("trade-clearing-compactor-demo/src/main/java");

        CompactorBenchmarkReportDto report = benchmarkService.runBenchmark(sourceRoot);

        assertThat(report.scannedJavaFiles()).isEqualTo(21);
        assertThat(report.repositoryTokenReductionPercent()).isGreaterThanOrEqualTo(50.0);
        assertThat(report.surgicalEditTokenReductionPercent()).isGreaterThanOrEqualTo(60.0);
        assertThat(report.springContractRetentionPercent()).isEqualTo(100.0);
        assertThat(report.totalSeededProxyTraps()).isEqualTo(4);
        assertThat(report.inlineRetainedProxyTraps()).isEqualTo(4);
        assertThat(report.inlineProxyTrapRecallPercent()).isEqualTo(100.0);
        assertThat(report.advisorPromptTokenReductionPercent()).isGreaterThanOrEqualTo(50.0);

        List<BenchmarkMetricRowDto> metrics = report.metrics();
        assertThat(metrics).hasSize(5);

        Path outputDir = reportExporter.exportReports(sourceRoot);

        Path budgetJson = outputDir.resolve("compaction-budget.json");
        Path benchmarkJson = outputDir.resolve("benchmark-report.json");
        Path summaryMd = outputDir.resolve("compactor-summary.md");
        Path junitXml = outputDir.resolve("junit-compactor.xml");

        assertThat(budgetJson).exists();
        assertThat(Files.size(budgetJson)).isGreaterThan(0L);

        assertThat(benchmarkJson).exists();
        assertThat(Files.size(benchmarkJson)).isGreaterThan(0L);

        assertThat(summaryMd).exists();
        assertThat(Files.size(summaryMd)).isGreaterThan(0L);

        assertThat(junitXml).exists();
        assertThat(Files.size(junitXml)).isGreaterThan(0L);
    }
}
