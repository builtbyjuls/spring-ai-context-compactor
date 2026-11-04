package com.builtbyjuls.clearing.margin;

import java.math.BigDecimal;

public interface MarginCalculator {

    BigDecimal calculateRequiredMargin(String instrumentSymbol, String assetClass, BigDecimal notionalAmount);

    String engineName();
}
