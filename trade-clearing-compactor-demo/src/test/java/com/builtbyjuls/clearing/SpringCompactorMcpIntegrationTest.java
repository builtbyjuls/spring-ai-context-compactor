package com.builtbyjuls.clearing;

import com.builtbyjuls.compactor.mcp.McpJsonRpcRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SpringCompactorMcpIntegrationTest extends ClearingTestContainersBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("GET /internal/spring-compactor/budget returns full demo domain budget analysis")
    void getBudgetEndpoint() throws Exception {
        mockMvc.perform(get("/internal/spring-compactor/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles", greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.overallTokenReductionPercent", greaterThanOrEqualTo(50.0)))
                .andExpect(jsonPath("$.stereotypeBreakdown").isArray())
                .andExpect(jsonPath("$.detectedTraps").isArray());
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp initialize handshake")
    void mcpInitializeHandshake() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "initialize", Map.of());

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("spring-ai-context-compactor"))
                .andExpect(jsonPath("$.result.serverInfo.version").value("0.1.0"));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp notification returns 202 Accepted")
    void mcpNotificationAccepted() throws Exception {
        McpJsonRpcRequest notification = new McpJsonRpcRequest("2.0", null, "notifications/initialized", Map.of());

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notification)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/list returns four registered tools")
    void mcpToolsList() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 2, "tools/list", Map.of());

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.result.tools", hasSize(4)))
                .andExpect(jsonPath("$.result.tools[*].name", hasItem("compact_spring_file")))
                .andExpect(jsonPath("$.result.tools[*].name", hasItem("compact_spring_slice")))
                .andExpect(jsonPath("$.result.tools[*].name", hasItem("read_spring_method_body")))
                .andExpect(jsonPath("$.result.tools[*].name", hasItem("analyze_compaction_budget")));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/call compact_spring_file compacts TradeClearingService and auto-expands proxy trap slices")
    void mcpToolCallCompactSpringFile() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 3, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of("query", "TradeClearingService")
        ));

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.className").value("TradeClearingService"))
                .andExpect(jsonPath("$.result.structuredContent.stereotype").value("SERVICE"))
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("// [AUTO-EXPANDED METHOD SLICES] (Exact raw source lines ready for edit_file Target)")))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("inspectMemberPortfolioWithSnapshotTouch")))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("recordInternalComplianceStamp")));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/call compact_spring_file unfolds explicitly requested expandMethods")
    void mcpToolCallCompactSpringFileExpandMethods() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 31, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "TradeClearingService",
                        "expandMethods", "novateAndClearTrade"
                )
        ));

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("// [AUTO-EXPANDED METHOD SLICES] (Exact raw source lines ready for edit_file Target)")))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("TradeClearingService#novateAndClearTrade")));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/call compact_spring_slice compacts trade package")
    void mcpToolCallCompactSpringSlice() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 4, "tools/call", Map.of(
                "name", "compact_spring_slice",
                "arguments", Map.of(
                        "sliceQuery", "trade",
                        "maxTokenBudget", 50000
                )
        ));

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.totalFilesCompacted", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.result.content[0].type").value("text"));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/call read_spring_method_body unfolds novateAndClearTrade")
    void mcpToolCallReadSpringMethodBody() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 5, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of(
                        "classQuery", "TradeClearingService",
                        "methodName", "novateAndClearTrade"
                )
        ));

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent[0].methodName").value("novateAndClearTrade"))
                .andExpect(jsonPath("$.result.content[0].text").isString())
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("// [METHOD SLICE]")))
                .andExpect(jsonPath("$.result.content[0].text").value(not(matchesRegex("(?s).*(?m)^\\s*\\d+:\\s.*"))));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/call read_spring_method_body unfolds batch method names in single round-trip")
    void mcpToolCallReadSpringMethodBodyBatch() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 51, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of(
                        "classQuery", "TradeClearingService",
                        "methodName", "novateAndClearTrade, recordInternalComplianceStamp"
                )
        ));

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(51))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent", hasSize(2)))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("TradeClearingService#novateAndClearTrade")))
                .andExpect(jsonPath("$.result.content[0].text").value(containsString("TradeClearingService#recordInternalComplianceStamp")));
    }

    @Test
    @DisplayName("POST /internal/spring-compactor/mcp tools/call analyze_compaction_budget returns live budget")
    void mcpToolCallAnalyzeCompactionBudget() throws Exception {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 6, "tools/call", Map.of(
                "name", "analyze_compaction_budget",
                "arguments", Map.of()
        ));

        mockMvc.perform(post("/internal/spring-compactor/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.totalFiles", greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.result.content[0].type").value("text"));
    }
}
