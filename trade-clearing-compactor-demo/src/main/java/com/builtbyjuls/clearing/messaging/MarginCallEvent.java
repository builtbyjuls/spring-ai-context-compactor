package com.builtbyjuls.clearing.messaging;

import java.math.BigDecimal;
import java.time.Instant;

public record MarginCallEvent(
        String callReference,
        String tradeId,
        String memberCode,
        BigDecimal deficitAmount,
        Instant createdAt
) {
}
