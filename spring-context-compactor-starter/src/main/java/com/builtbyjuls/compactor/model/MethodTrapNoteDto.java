package com.builtbyjuls.compactor.model;

import java.util.Objects;

public record MethodTrapNoteDto(
        String callerMethod,
        String targetMethod,
        int callSiteLine,
        ProxyTrapType trapType,
        String summary
) {
    public MethodTrapNoteDto {
        Objects.requireNonNull(callerMethod, "callerMethod must not be null");
        Objects.requireNonNull(targetMethod, "targetMethod must not be null");
        Objects.requireNonNull(trapType, "trapType must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }
}
