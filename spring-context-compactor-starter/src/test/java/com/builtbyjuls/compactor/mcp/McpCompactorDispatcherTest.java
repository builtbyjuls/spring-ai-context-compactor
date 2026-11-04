package com.builtbyjuls.compactor.mcp;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.MethodBodySliceDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.SliceCompactionReportDto;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpCompactorDispatcherTest {

    private McpCompactorDispatcher dispatcher;
    private CompactorProperties properties;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        createSampleSourceTree(tempDir);

        properties = new CompactorProperties();
        properties.setSourceRoot(tempDir.toString());

        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        SpringAstCompactor astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);
        SpringSliceExpander sliceExpander = new SpringSliceExpander(astCompactor, trapScanner, properties);
        CompactionBudgetService budgetService = new CompactionBudgetService(astCompactor);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        dispatcher = new McpCompactorDispatcher(astCompactor, sliceExpander, budgetService, properties, objectMapper);
    }

    private void createSampleSourceTree(Path root) throws IOException {
        Path tradePkg = root.resolve("com/builtbyjuls/clearing/trade");
        Files.createDirectories(tradePkg);

        String entitySource = """
                package com.builtbyjuls.clearing.trade;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import java.math.BigDecimal;
                import java.util.UUID;

                @Entity
                @Table(name = "cleared_trades")
                public class ClearedTrade {

                    @Id
                    @Column(name = "trade_id")
                    private UUID tradeId;

                    @Column(name = "amount")
                    private BigDecimal amount;

                    public UUID getTradeId() {
                        return tradeId;
                    }

                    public void setTradeId(UUID tradeId) {
                        this.tradeId = tradeId;
                    }
                }
                """;

        String serviceSource = """
                package com.builtbyjuls.clearing.trade;

                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                @Service
                public class TradeClearingService {

                    @Transactional
                    public void clearTrade(String tradeRef) {
                        applyMargin(tradeRef);
                    }

                    @Transactional
                    public void applyMargin(String tradeRef) {
                        // margin calculation
                    }
                }
                """;

        Files.writeString(tradePkg.resolve("ClearedTrade.java"), entitySource);
        Files.writeString(tradePkg.resolve("TradeClearingService.java"), serviceSource);
    }

    @Test
    @DisplayName("initialize handshake returns protocolVersion 2024-11-05 and serverInfo")
    void initializeHandshake() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "initialize", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1);
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("protocolVersion")).isEqualTo("2024-11-05");

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertThat(capabilities).containsKey("tools");

        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo("spring-ai-context-compactor");
        assertThat(serverInfo.get("version")).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("notifications/* return null response")
    void notificationsReturnNull() {
        McpJsonRpcRequest notification = new McpJsonRpcRequest("2.0", null, "notifications/initialized", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(notification);
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("ping returns empty success result")
    void pingReturnsEmptySuccess() {
        McpJsonRpcRequest ping = new McpJsonRpcRequest("2.0", "ping-123", "ping", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(ping);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("ping-123");
        assertThat(response.error()).isNull();
        assertThat(response.result()).isEqualTo(Map.of());
    }

    @Test
    @DisplayName("tools/list returns 4 registered compactor tools")
    void toolsListReturnsFourTools() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 2, "tools/list", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(request);

        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<McpToolDefinition> tools = (List<McpToolDefinition>) result.get("tools");
        assertThat(tools).hasSize(4);

        List<String> names = tools.stream().map(McpToolDefinition::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "compact_spring_file",
                "compact_spring_slice",
                "read_spring_method_body",
                "analyze_compaction_budget"
        );
    }

    @Test
    @DisplayName("tools/call compact_spring_file succeeds with valid class query")
    void toolsCallCompactSpringFile() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 3, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "ClearedTrade",
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("isError")).isEqualTo(false);
        assertThat(result.get("structuredContent")).isInstanceOf(CompactedFileResultDto.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        String text = (String) content.get(0).get("text");
        assertThat(text).contains("[SPRING COMPACTOR]");
        assertThat(text).contains("class ClearedTrade");
    }

    @Test
    @DisplayName("tools/call compact_spring_file returns -32602 when query is missing or not found")
    void toolsCallCompactSpringFileErrors() {
        McpJsonRpcRequest missingQueryReq = new McpJsonRpcRequest("2.0", 4, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of()
        ));
        McpJsonRpcResponse missingResponse = dispatcher.dispatch(missingQueryReq);
        assertThat(missingResponse.error()).isNotNull();
        assertThat(missingResponse.error().code()).isEqualTo(-32602);

        McpJsonRpcRequest notFoundReq = new McpJsonRpcRequest("2.0", 5, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of("query", "NonExistentService", "sourceRoot", tempDir.toString())
        ));
        McpJsonRpcResponse notFoundResponse = dispatcher.dispatch(notFoundReq);
        assertThat(notFoundResponse.error()).isNotNull();
        assertThat(notFoundResponse.error().code()).isEqualTo(-32602);
    }

    @Test
    @DisplayName("tools/call compact_spring_slice succeeds with slice filter and maxTokenBudget")
    void toolsCallCompactSpringSlice() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 6, "tools/call", Map.of(
                "name", "compact_spring_slice",
                "arguments", Map.of(
                        "sliceQuery", "trade",
                        "maxTokenBudget", 5000,
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("isError")).isEqualTo(false);
        assertThat(result.get("structuredContent")).isInstanceOf(SliceCompactionReportDto.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        String text = (String) content.get(0).get("text");
        assertThat(text).contains("ClearedTrade");
        assertThat(text).contains("TradeClearingService");
    }

    @Test
    @DisplayName("tools/call read_spring_method_body unfolds line-numbered method slice")
    void toolsCallReadSpringMethodBody() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 7, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of(
                        "classQuery", "TradeClearingService",
                        "methodName", "clearTrade",
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("isError")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<MethodBodySliceDto> slices = (List<MethodBodySliceDto>) result.get("structuredContent");
        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).methodName()).isEqualTo("clearTrade");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertThat(text).contains("// [METHOD SLICE]");
        assertThat(text).contains("TradeClearingService#clearTrade");
        assertThat(text).contains("applyMargin(tradeRef);");
        // Verify raw un-numbered source lines (no line-number prefix like "   1: ")
        assertThat(text).doesNotMatch("(?m)^\\s*\\d+:\\s");
    }

    @Test
    @DisplayName("tools/call read_spring_method_body returns -32602 on missing args or unknown method")
    void toolsCallReadSpringMethodBodyErrors() {
        McpJsonRpcRequest missingArgsReq = new McpJsonRpcRequest("2.0", 8, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of("classQuery", "TradeClearingService")
        ));
        McpJsonRpcResponse missingResponse = dispatcher.dispatch(missingArgsReq);
        assertThat(missingResponse.error()).isNotNull();
        assertThat(missingResponse.error().code()).isEqualTo(-32602);

        McpJsonRpcRequest notFoundReq = new McpJsonRpcRequest("2.0", 9, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of(
                        "classQuery", "TradeClearingService",
                        "methodName", "nonExistentMethod",
                        "sourceRoot", tempDir.toString()
                )
        ));
        McpJsonRpcResponse notFoundResponse = dispatcher.dispatch(notFoundReq);
        assertThat(notFoundResponse.error()).isNotNull();
        assertThat(notFoundResponse.error().code()).isEqualTo(-32602);
    }

    @Test
    @DisplayName("tools/call analyze_compaction_budget returns project-wide compaction report")
    void toolsCallAnalyzeCompactionBudget() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 10, "tools/call", Map.of(
                "name", "analyze_compaction_budget",
                "arguments", Map.of("sourceRoot", tempDir.toString())
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("isError")).isEqualTo(false);
        assertThat(result.get("structuredContent")).isInstanceOf(ProjectCompactionBudgetDto.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String jsonText = (String) content.get(0).get("text");
        assertThat(jsonText).contains("totalFiles");
        assertThat(jsonText).contains("tokensSaved");
    }

    @Test
    @DisplayName("tools/call with unknown tool name returns -32601")
    void toolsCallUnknownTool() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 11, "tools/call", Map.of(
                "name", "unknown_magic_tool",
                "arguments", Map.of()
        ));
        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo(-32601);
    }

    @Test
    @DisplayName("Invalid request with null or blank method returns -32600")
    void invalidRequestNullMethod() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 12, "", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo(-32600);
    }

    @Test
    @DisplayName("Unknown protocol method returns -32601")
    void unknownMethod() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 13, "some/nonexistent/method", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo(-32601);
    }

    @Test
    @DisplayName("resolveSourceRoot respects override, configured, and fallback paths")
    void resolveSourceRootPrecedence() {
        Path override = dispatcher.resolveSourceRoot("/custom/path");
        assertThat(override).isEqualTo(Path.of("/custom/path"));

        Path defaultPath = dispatcher.resolveSourceRoot(null);
        assertThat(defaultPath).isEqualTo(tempDir);
    }

    @Test
    @DisplayName("McpJsonRpcRequest tolerates null values inside params map")
    void requestToleratesNullValuesInParams() {
        Map<String, Object> mapWithNull = new java.util.HashMap<>();
        mapWithNull.put("query", "TradeClearingService");
        mapWithNull.put("optionalField", null);

        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 99, "tools/call", mapWithNull);
        assertThat(request.params()).containsKey("optionalField");
        assertThat(request.params().get("optionalField")).isNull();
    }

    @Test
    @DisplayName("tools/call compact_spring_file automatically auto-expands proxy trap method slices")
    void toolsCallCompactSpringFileAutoExpandsTraps() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 20, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "TradeClearingService",
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");

        assertThat(text).contains("// [AUTO-EXPANDED METHOD SLICES] (Exact raw source lines ready for edit_file Target)");
        assertThat(text).contains("TradeClearingService#clearTrade");
        assertThat(text).contains("TradeClearingService#applyMargin");
        assertThat(text).contains("applyMargin(tradeRef);");
        // Verify raw un-numbered source lines (no line-number prefix like "   1: ")
        assertThat(text).doesNotMatch("(?m)^\\s*\\d+:\\s");
    }

    @Test
    @DisplayName("tools/call compact_spring_file honors expandTrappedMethods=false by omitting auto-expanded section")
    void toolsCallCompactSpringFileDisablesAutoExpandTraps() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 21, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "TradeClearingService",
                        "expandTrappedMethods", false,
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");

        assertThat(text).doesNotContain("// [AUTO-EXPANDED METHOD SLICES]");
    }

    @Test
    @DisplayName("tools/call compact_spring_file unfolds explicitly requested expandMethods")
    void toolsCallCompactSpringFileWithExpandMethods() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 22, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "ClearedTrade",
                        "expandMethods", "getTradeId, setTradeId",
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");

        assertThat(text).contains("// [AUTO-EXPANDED METHOD SLICES] (Exact raw source lines ready for edit_file Target)");
        assertThat(text).contains("ClearedTrade#getTradeId");
        assertThat(text).contains("ClearedTrade#setTradeId");
    }

    @Test
    @DisplayName("tools/call read_spring_method_body supports comma-separated batch method unfolding")
    void toolsCallReadSpringMethodBodyBatchUnfolding() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 23, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of(
                        "classQuery", "TradeClearingService",
                        "methodName", "clearTrade, applyMargin",
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<MethodBodySliceDto> slices = (List<MethodBodySliceDto>) result.get("structuredContent");
        assertThat(slices).hasSize(2);
        assertThat(slices.stream().map(MethodBodySliceDto::methodName).toList())
                .containsExactly("clearTrade", "applyMargin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertThat(text).contains("TradeClearingService#clearTrade");
        assertThat(text).contains("TradeClearingService#applyMargin");
    }

    @Test
    @DisplayName("tools/call read_spring_method_body supports methodNames parameter as string or collection")
    void toolsCallReadSpringMethodBodyMethodNamesParam() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 24, "tools/call", Map.of(
                "name", "read_spring_method_body",
                "arguments", Map.of(
                        "classQuery", "TradeClearingService",
                        "methodNames", List.of("clearTrade", "applyMargin"),
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<MethodBodySliceDto> slices = (List<MethodBodySliceDto>) result.get("structuredContent");
        assertThat(slices).hasSize(2);
    }

    @Test
    @DisplayName("tools/list schema includes focusMode and wildcard documentation for compact_spring_file")
    void toolsListSchemaIncludesFocusMode() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 25, "tools/list", Map.of());
        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<McpToolDefinition> tools = (List<McpToolDefinition>) result.get("tools");

        McpToolDefinition compactFileTool = tools.stream()
                .filter(t -> "compact_spring_file".equals(t.name()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) compactFileTool.inputSchema().get("properties");
        assertThat(properties).containsKey("focusMode");

        @SuppressWarnings("unchecked")
        Map<String, Object> focusModeProp = (Map<String, Object>) properties.get("focusMode");
        assertThat(focusModeProp.get("type")).isEqualTo("boolean");

        @SuppressWarnings("unchecked")
        Map<String, Object> expandMethodsProp = (Map<String, Object>) properties.get("expandMethods");
        assertThat((String) expandMethodsProp.get("description")).contains("wildcard patterns");
    }

    @Test
    @DisplayName("tools/call compact_spring_file auto-expands intra-class callees and enables focused skeleton mode")
    void toolsCallCompactSpringFileWithCalleeExpansionAndFocusedSkeleton() throws IOException {
        Path servicePkg = tempDir.resolve("com/builtbyjuls/clearing/order");
        Files.createDirectories(servicePkg);

        String multiMethodSource = """
                package com.builtbyjuls.clearing.order;

                import org.springframework.stereotype.Service;

                @Service
                public class OrderProcessingService {

                    public void placeOrder() {
                        this.validateOrderItems();
                    }

                    private void validateOrderItems() {
                        calculateTax();
                    }

                    private void calculateTax() {
                        System.out.println("tax calculated");
                    }

                    public void trackOrderStatus() {
                        System.out.println("status tracking");
                    }

                    public void cancelActiveOrder() {
                        System.out.println("order cancelled");
                    }
                }
                """;
        Files.writeString(servicePkg.resolve("OrderProcessingService.java"), multiMethodSource);

        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 26, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "OrderProcessingService",
                        "expandMethods", "placeOrder",
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");

        // Verify intra-class helper callees (validateOrderItems and calculateTax) are unfolded
        assertThat(text).contains("// [AUTO-EXPANDED METHOD SLICES]");
        assertThat(text).contains("OrderProcessingService#placeOrder");
        assertThat(text).contains("OrderProcessingService#validateOrderItems");
        assertThat(text).contains("OrderProcessingService#calculateTax");

        // Verify focused skeleton mode collapses untouched methods into summary comment
        assertThat(text).contains("// Other Methods (2 omitted): trackOrderStatus(L");
        assertThat(text).contains("cancelActiveOrder(L");
        assertThat(text).doesNotContain("public void trackOrderStatus() /*");
        assertThat(text).doesNotContain("public void cancelActiveOrder() /*");

        // Verify placeOrder signature remains in skeleton
        assertThat(text).contains("public void placeOrder() /*");
    }

    @Test
    @DisplayName("tools/call compact_spring_file respects focusMode: false override")
    void toolsCallCompactSpringFileWithFocusModeFalse() throws IOException {
        Path servicePkg = tempDir.resolve("com/builtbyjuls/clearing/order");
        Files.createDirectories(servicePkg);

        String multiMethodSource = """
                package com.builtbyjuls.clearing.order;

                import org.springframework.stereotype.Service;

                @Service
                public class OrderQueryService {

                    public void queryOne() {
                        System.out.println("1");
                    }

                    public void queryTwo() {
                        System.out.println("2");
                    }
                }
                """;
        Files.writeString(servicePkg.resolve("OrderQueryService.java"), multiMethodSource);

        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 27, "tools/call", Map.of(
                "name", "compact_spring_file",
                "arguments", Map.of(
                        "query", "OrderQueryService",
                        "expandMethods", "queryOne",
                        "focusMode", false,
                        "sourceRoot", tempDir.toString()
                )
        ));

        McpJsonRpcResponse response = dispatcher.dispatch(request);
        assertThat(response).isNotNull();
        assertThat(response.error()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");

        // With focusMode: false, queryTwo remains intact in skeleton, not omitted
        assertThat(text).contains("public void queryTwo()");
        assertThat(text).doesNotContain("// Other Methods");
    }
}
