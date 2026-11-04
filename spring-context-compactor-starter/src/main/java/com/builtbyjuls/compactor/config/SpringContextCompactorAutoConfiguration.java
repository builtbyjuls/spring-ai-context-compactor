package com.builtbyjuls.compactor.config;

import com.builtbyjuls.compactor.advisor.SpringContextCompactorAdvisor;
import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.mcp.CompactorMcpHttpController;
import com.builtbyjuls.compactor.mcp.McpCompactorDispatcher;
import com.builtbyjuls.compactor.mcp.McpStdioServer;
import com.builtbyjuls.compactor.report.CompactorEfficiencyBenchmarkService;
import com.builtbyjuls.compactor.report.CompactorReportExporter;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    @Bean
    @ConditionalOnMissingBean
    public SpringSliceExpander springSliceExpander(
            SpringAstCompactor springAstCompactor,
            SpringProxyTrapScanner springProxyTrapScanner,
            CompactorProperties compactorProperties
    ) {
        return new SpringSliceExpander(springAstCompactor, springProxyTrapScanner, compactorProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CompactionBudgetService compactionBudgetService(SpringAstCompactor springAstCompactor) {
        return new CompactionBudgetService(springAstCompactor);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringContextCompactorAdvisor springContextCompactorAdvisor(
            SpringAstCompactor springAstCompactor,
            CompactorProperties compactorProperties
    ) {
        return new SpringContextCompactorAdvisor(springAstCompactor, compactorProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper compactorObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpCompactorDispatcher mcpCompactorDispatcher(
            SpringAstCompactor springAstCompactor,
            SpringSliceExpander springSliceExpander,
            CompactionBudgetService compactionBudgetService,
            CompactorProperties compactorProperties,
            ObjectMapper compactorObjectMapper
    ) {
        return new McpCompactorDispatcher(
                springAstCompactor,
                springSliceExpander,
                compactionBudgetService,
                compactorProperties,
                compactorObjectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public McpStdioServer mcpStdioServer(
            McpCompactorDispatcher mcpCompactorDispatcher,
            ObjectMapper compactorObjectMapper
    ) {
        return new McpStdioServer(mcpCompactorDispatcher, compactorObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.compactor", name = "mcp-endpoint-enabled", havingValue = "true", matchIfMissing = true)
    public CompactorMcpHttpController compactorMcpHttpController(
            CompactionBudgetService compactionBudgetService,
            McpCompactorDispatcher mcpCompactorDispatcher
    ) {
        return new CompactorMcpHttpController(compactionBudgetService, mcpCompactorDispatcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public CompactorEfficiencyBenchmarkService compactorEfficiencyBenchmarkService(
            SpringAstCompactor springAstCompactor,
            SpringSliceExpander springSliceExpander,
            CompactionBudgetService compactionBudgetService,
            SpringContextCompactorAdvisor springContextCompactorAdvisor,
            McpCompactorDispatcher mcpCompactorDispatcher
    ) {
        return new CompactorEfficiencyBenchmarkService(
                springAstCompactor,
                springSliceExpander,
                compactionBudgetService,
                springContextCompactorAdvisor,
                mcpCompactorDispatcher
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CompactorReportExporter compactorReportExporter(
            CompactionBudgetService compactionBudgetService,
            CompactorEfficiencyBenchmarkService compactorEfficiencyBenchmarkService,
            CompactorProperties compactorProperties,
            ObjectMapper compactorObjectMapper
    ) {
        return new CompactorReportExporter(
                compactionBudgetService,
                compactorEfficiencyBenchmarkService,
                compactorProperties,
                compactorObjectMapper
        );
    }
}
