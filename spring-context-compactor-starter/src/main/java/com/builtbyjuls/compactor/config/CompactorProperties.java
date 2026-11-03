package com.builtbyjuls.compactor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.compactor")
public class CompactorProperties {

    private boolean enabled = true;
    private String sourceRoot = "src/main/java";
    private boolean includeLineNumbers = true;
    private boolean detectProxyTraps = true;
    private boolean mcpEndpointEnabled = true;
    private int advisorOrder = 0;
    private String reportOutputDir = "target/compactor-reports";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public boolean isIncludeLineNumbers() {
        return includeLineNumbers;
    }

    public void setIncludeLineNumbers(boolean includeLineNumbers) {
        this.includeLineNumbers = includeLineNumbers;
    }

    public boolean isDetectProxyTraps() {
        return detectProxyTraps;
    }

    public void setDetectProxyTraps(boolean detectProxyTraps) {
        this.detectProxyTraps = detectProxyTraps;
    }

    public boolean isMcpEndpointEnabled() {
        return mcpEndpointEnabled;
    }

    public void setMcpEndpointEnabled(boolean mcpEndpointEnabled) {
        this.mcpEndpointEnabled = mcpEndpointEnabled;
    }

    public int getAdvisorOrder() {
        return advisorOrder;
    }

    public void setAdvisorOrder(int advisorOrder) {
        this.advisorOrder = advisorOrder;
    }

    public String getReportOutputDir() {
        return reportOutputDir;
    }

    public void setReportOutputDir(String reportOutputDir) {
        this.reportOutputDir = reportOutputDir;
    }
}
