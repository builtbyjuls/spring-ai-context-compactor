package com.builtbyjuls.clearing.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("standardSpanMarginCalculator")
@ConditionalOnProperty(name = "clearing.margin.engine", havingValue = "span", matchIfMissing = true)
public class StandardSpanMarginCalculator implements MarginCalculator {

    private static final BigDecimal FX_RATE = new BigDecimal("0.08");
    private static final BigDecimal DERIVATIVES_RATE = new BigDecimal("0.12");
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.10");

    @Override
    public BigDecimal calculateRequiredMargin(String instrumentSymbol, String assetClass, BigDecimal notionalAmount) {
        validateInputs(instrumentSymbol, assetClass, notionalAmount);
        BigDecimal rate = resolveMarginRate(assetClass);
        return notionalAmount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public String engineName() {
        return "SPAN";
    }

    private void validateInputs(String instrumentSymbol, String assetClass, BigDecimal notionalAmount) {
        if (instrumentSymbol == null || instrumentSymbol.isBlank()) {
            throw new IllegalArgumentException("Instrument symbol must not be blank");
        }
        if (assetClass == null || assetClass.isBlank()) {
            throw new IllegalArgumentException("Asset class must not be blank");
        }
        if (notionalAmount == null || notionalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Notional amount must be strictly positive");
        }
    }

    private BigDecimal resolveMarginRate(String assetClass) {
        if ("FX".equalsIgnoreCase(assetClass)) {
            return FX_RATE;
        }
        if ("RATES".equalsIgnoreCase(assetClass) || "DERIVATIVES".equalsIgnoreCase(assetClass)) {
            return DERIVATIVES_RATE;
        }
        return DEFAULT_RATE;
    }
}
