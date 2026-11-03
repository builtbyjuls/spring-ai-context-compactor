package com.builtbyjuls.compactor.model;

public record AdvisorCompactionStatsDto(
        int codeBlocksDetected,
        int codeBlocksCompacted,
        int originalTokens,
        int compactedTokens,
        int tokensSaved,
        double tokenReductionPercent
) {
    public AdvisorCompactionStatsDto {
        if (codeBlocksDetected < 0) {
            throw new IllegalArgumentException("codeBlocksDetected must not be negative");
        }
        if (codeBlocksCompacted < 0) {
            throw new IllegalArgumentException("codeBlocksCompacted must not be negative");
        }
        if (originalTokens < 0) {
            throw new IllegalArgumentException("originalTokens must not be negative");
        }
        if (compactedTokens < 0) {
            throw new IllegalArgumentException("compactedTokens must not be negative");
        }
        if (tokensSaved < 0) {
            throw new IllegalArgumentException("tokensSaved must not be negative");
        }
        if (tokenReductionPercent < 0.0) {
            throw new IllegalArgumentException("tokenReductionPercent must not be negative");
        }
    }
}
