package com.builtbyjuls.compactor.model;

import java.util.List;
import java.util.Objects;

public record CompactedFileResultDto(
        String filePath,
        String packageName,
        String className,
        SpringStereotype stereotype,
        List<String> injectedDependencies,
        int originalLines,
        int compactedLines,
        int originalEstimatedTokens,
        int compactedEstimatedTokens,
        double tokenReductionPercent,
        String compactedSkeleton,
        List<CompactedMethodSummaryDto> methods,
        List<MethodTrapNoteDto> trapNotes
) {
    public CompactedFileResultDto {
        filePath = filePath == null ? "" : filePath;
        packageName = packageName == null ? "" : packageName;
        className = className == null ? "" : className;
        Objects.requireNonNull(stereotype, "stereotype must not be null");
        injectedDependencies = injectedDependencies == null ? List.of() : List.copyOf(injectedDependencies);
        compactedSkeleton = compactedSkeleton == null ? "" : compactedSkeleton;
        methods = methods == null ? List.of() : List.copyOf(methods);
        trapNotes = trapNotes == null ? List.of() : List.copyOf(trapNotes);
    }
}
