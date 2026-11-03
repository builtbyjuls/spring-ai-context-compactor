package com.builtbyjuls.compactor.config;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SpringContextCompactorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringContextCompactorAutoConfiguration.class));

    @Test
    @DisplayName("Registers compactor beans by default")
    void registersBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StereotypeClassifier.class);
            assertThat(context).hasSingleBean(SpringProxyTrapScanner.class);
            assertThat(context).hasSingleBean(SpringAstCompactor.class);
            assertThat(context).hasSingleBean(SpringSliceExpander.class);
            assertThat(context).hasSingleBean(CompactionBudgetService.class);
            assertThat(context).hasSingleBean(CompactorProperties.class);
        });
    }

    @Test
    @DisplayName("Backs off when spring.compactor.enabled is false")
    void backsOffWhenDisabled() {
        contextRunner.withPropertyValues("spring.compactor.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(StereotypeClassifier.class);
                    assertThat(context).doesNotHaveBean(SpringProxyTrapScanner.class);
                    assertThat(context).doesNotHaveBean(SpringAstCompactor.class);
                    assertThat(context).doesNotHaveBean(SpringSliceExpander.class);
                    assertThat(context).doesNotHaveBean(CompactionBudgetService.class);
                });
    }
}
