package com.builtbyjuls.compactor.model;

import java.util.List;

public record SliceCompactionReportDto(
        String sliceTarget,
        int totalFilesCompacted,
        int originalTotalLines,
        int compactedTotalLines,
        int originalEstimatedTokens,
        int compactedEstimatedTokens,
        double overallTokenReductionPercent,
        boolean tokenBudgetExceeded,
        List<CompactedFileResultDto> files,
        String combinedSkeleton
) {
    public SliceCompactionReportDto {
        sliceTarget = sliceTarget != null ? sliceTarget : "";
        combinedSkeleton = combinedSkeleton != null ? combinedSkeleton : "";
        files = files != null ? List.copyOf(files) : List.of();
    }
}
