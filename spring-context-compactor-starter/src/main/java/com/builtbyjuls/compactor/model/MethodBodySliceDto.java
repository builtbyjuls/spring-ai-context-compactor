package com.builtbyjuls.compactor.model;

import java.util.List;

public record MethodBodySliceDto(
        String filePath,
        String className,
        String methodName,
        String signature,
        int startLine,
        int endLine,
        int lineCount,
        int estimatedTokens,
        String numberedSourceLines,
        List<MethodTrapNoteDto> trapNotes
) {
    public MethodBodySliceDto {
        filePath = filePath != null ? filePath : "";
        className = className != null ? className : "";
        methodName = methodName != null ? methodName : "";
        signature = signature != null ? signature : "";
        numberedSourceLines = numberedSourceLines != null ? numberedSourceLines : "";
        trapNotes = trapNotes != null ? List.copyOf(trapNotes) : List.of();
    }
}
