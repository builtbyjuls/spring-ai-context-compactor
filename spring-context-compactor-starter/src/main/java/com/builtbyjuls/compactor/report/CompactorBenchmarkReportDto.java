package com.builtbyjuls.compactor.report;

import java.util.List;
import java.util.Objects;

public record CompactorBenchmarkReportDto(
        String sourceRoot,
        int scannedJavaFiles,
        int rawSourceTotalLines,
        int compactedTotalLines,
        long rawSourceTotalBytes,
        long compactedTotalBytes,
        int rawSourceEstimatedTokens,
        int compactedEstimatedTokens,
        double repositoryTokenReductionPercent,
        int rawSurgicalEditTokens,
        int compactedSurgicalEditTokens,
        double surgicalEditTokenReductionPercent,
        double mcpSurgicalWorkflowLatencyMs,
        int totalSpringContractsEvaluated,
        int retainedSpringContracts,
        double springContractRetentionPercent,
        int totalSeededProxyTraps,
        int inlineRetainedProxyTraps,
        int naiveSkeletonDetectedTraps,
        double inlineProxyTrapRecallPercent,
        int advisorPromptOriginalTokens,
        int advisorPromptCompactedTokens,
        double advisorPromptTokenReductionPercent,
        int projectedSavedTokensPer1000Prompts,
        List<BenchmarkMetricRowDto> metrics
) {
    public CompactorBenchmarkReportDto {
        Objects.requireNonNull(sourceRoot, "sourceRoot must not be null");
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
