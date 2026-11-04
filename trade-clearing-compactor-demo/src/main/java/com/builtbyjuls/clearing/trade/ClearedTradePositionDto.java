package com.builtbyjuls.clearing.trade;

import java.math.BigDecimal;

public record ClearedTradePositionDto(
        String tradeId,
        String memberCode,
        BigDecimal notionalAmount,
        BigDecimal requiredMargin,
        String marginEngine,
        String status,
        boolean marginCallIssued
) {
}
