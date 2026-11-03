package com.builtbyjuls.compactor.model;

import java.util.List;
import java.util.Objects;

public record CompactedMethodSummaryDto(
        String methodName,
        String signature,
        int startLine,
        int endLine,
        int omittedLineCount,
        List<String> retainedAnnotations,
        List<MethodTrapNoteDto> trapNotes
) {
    public CompactedMethodSummaryDto {
        Objects.requireNonNull(methodName, "methodName must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        retainedAnnotations = retainedAnnotations == null ? List.of() : List.copyOf(retainedAnnotations);
        trapNotes = trapNotes == null ? List.of() : List.copyOf(trapNotes);
    }
}
