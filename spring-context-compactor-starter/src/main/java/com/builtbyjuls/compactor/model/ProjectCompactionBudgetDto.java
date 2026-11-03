package com.builtbyjuls.compactor.model;

import java.util.List;

public record ProjectCompactionBudgetDto(
        String sourceRoot,
        int totalFiles,
        int originalTotalLines,
        int compactedTotalLines,
        int originalEstimatedTokens,
        int compactedEstimatedTokens,
        int tokensSaved,
        double overallTokenReductionPercent,
        List<StereotypeBudgetRowDto> stereotypeBreakdown,
        List<MethodTrapNoteDto> detectedTraps
) {
    public ProjectCompactionBudgetDto {
        sourceRoot = sourceRoot != null ? sourceRoot : "";
        stereotypeBreakdown = stereotypeBreakdown != null ? List.copyOf(stereotypeBreakdown) : List.of();
        detectedTraps = detectedTraps != null ? List.copyOf(detectedTraps) : List.of();
    }
}
