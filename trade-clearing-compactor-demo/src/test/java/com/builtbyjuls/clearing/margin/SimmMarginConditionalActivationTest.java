package com.builtbyjuls.clearing.margin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SimmMarginConditionalActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StandardSpanMarginCalculator.class, PortfolioSimmMarginCalculator.class);

    @Test
    @DisplayName("Default configuration activates StandardSpanMarginCalculator")
    void defaultActivatesSpan() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("standardSpanMarginCalculator");
            assertThat(context).doesNotHaveBean("portfolioSimmMarginCalculator");
            assertThat(context.getBean(MarginCalculator.class).engineName()).isEqualTo("SPAN");
        });
    }

    @Test
    @DisplayName("clearing.margin.engine=simm property activates PortfolioSimmMarginCalculator")
    void propertyActivatesSimm() {
        contextRunner
                .withPropertyValues("clearing.margin.engine=simm")
                .run(context -> {
                    assertThat(context).hasBean("portfolioSimmMarginCalculator");
                    assertThat(context).doesNotHaveBean("standardSpanMarginCalculator");
                    assertThat(context.getBean(MarginCalculator.class).engineName()).isEqualTo("SIMM");
                });
    }
}
