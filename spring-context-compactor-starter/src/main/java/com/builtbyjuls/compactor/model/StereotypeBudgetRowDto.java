package com.builtbyjuls.compactor.model;

import java.util.Objects;

public record StereotypeBudgetRowDto(
        SpringStereotype stereotype,
        int fileCount,
        int originalLines,
        int compactedLines,
        int originalEstimatedTokens,
        int compactedEstimatedTokens,
        double tokenReductionPercent
) {
    public StereotypeBudgetRowDto {
        Objects.requireNonNull(stereotype, "stereotype must not be null");
    }
}
