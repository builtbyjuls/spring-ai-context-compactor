package com.builtbyjuls.clearing.margin;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSimmMarginCalculatorTest {

    private final PortfolioSimmMarginCalculator calculator = new PortfolioSimmMarginCalculator();

    @Test
    @DisplayName("SIMM engine calculates 6.5% initial margin for FX asset class")
    void calculateRequiredMarginFx() {
        BigDecimal notional = new BigDecimal("1000000.0000");
        BigDecimal margin = calculator.calculateRequiredMargin("EUR/USD", "FX", notional);
        assertThat(margin).isEqualByComparingTo(new BigDecimal("65000.0000"));
    }

    @Test
    @DisplayName("SIMM engine calculates 9.5% initial margin for RATES and DERIVATIVES asset class")
    void calculateRequiredMarginRatesAndDerivatives() {
        BigDecimal notional = new BigDecimal("1000000.0000");
        BigDecimal ratesMargin = calculator.calculateRequiredMargin("US10Y", "RATES", notional);
        BigDecimal derivativesMargin = calculator.calculateRequiredMargin("OPT-SPX", "DERIVATIVES", notional);

        assertThat(ratesMargin).isEqualByComparingTo(new BigDecimal("95000.0000"));
        assertThat(derivativesMargin).isEqualByComparingTo(new BigDecimal("95000.0000"));
    }

    @Test
    @DisplayName("SIMM engine calculates 8.5% initial margin for default asset class")
    void calculateRequiredMarginDefault() {
        BigDecimal notional = new BigDecimal("1000000.0000");
        BigDecimal margin = calculator.calculateRequiredMargin("COMMODITY-GOLD", "COMMODITIES", notional);
        assertThat(margin).isEqualByComparingTo(new BigDecimal("85000.0000"));
    }

    @Test
    @DisplayName("SIMM engine returns SIMM as engine name")
    void engineName() {
        assertThat(calculator.engineName()).isEqualTo("SIMM");
    }

    @Test
    @DisplayName("SIMM engine rejects invalid inputs")
    void rejectsInvalidInputs() {
        BigDecimal notional = new BigDecimal("1000000.0000");

        assertThatThrownBy(() -> calculator.calculateRequiredMargin("", "FX", notional))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculateRequiredMargin("EUR/USD", "", notional))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculateRequiredMargin("EUR/USD", "FX", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
