package com.builtbyjuls.compactor.config;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CompactorProperties.class)
@ConditionalOnProperty(prefix = "spring.compactor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringContextCompactorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StereotypeClassifier stereotypeClassifier() {
        return new StereotypeClassifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringProxyTrapScanner springProxyTrapScanner() {
        return new SpringProxyTrapScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringAstCompactor springAstCompactor(
            StereotypeClassifier stereotypeClassifier,
            SpringProxyTrapScanner springProxyTrapScanner,
            CompactorProperties compactorProperties
    ) {
        return new SpringAstCompactor(stereotypeClassifier, springProxyTrapScanner, compactorProperties);
    }
}
