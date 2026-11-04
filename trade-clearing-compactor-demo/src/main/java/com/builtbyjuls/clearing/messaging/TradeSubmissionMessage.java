package com.builtbyjuls.clearing.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeSubmissionMessage(
        @NotBlank String tradeId,
        @NotBlank String memberCode,
        @NotBlank String instrumentSymbol,
        @NotBlank String assetClass,
        @NotNull @Positive BigDecimal notionalAmount,
        @NotNull LocalDate tradeDate
) {
}
