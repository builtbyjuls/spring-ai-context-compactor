package com.builtbyjuls.compactor.mcp;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.MethodBodySliceDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.SliceCompactionReportDto;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class McpCompactorDispatcher {

    private final SpringAstCompactor astCompactor;
    private final SpringSliceExpander sliceExpander;
    private final CompactionBudgetService budgetService;
    private final CompactorProperties properties;
    private final ObjectMapper objectMapper;

    public McpCompactorDispatcher(
            SpringAstCompactor astCompactor,
            SpringSliceExpander sliceExpander,
            CompactionBudgetService budgetService,
            CompactorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.astCompactor = Objects.requireNonNull(astCompactor, "astCompactor must not be null");
        this.sliceExpander = Objects.requireNonNull(sliceExpander, "sliceExpander must not be null");
        this.budgetService = Objects.requireNonNull(budgetService, "budgetService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public Path resolveSourceRoot(String overrideRoot) {
        if (overrideRoot != null && !overrideRoot.isBlank()) {
            return Path.of(overrideRoot.trim());
        }
        Path configured = Path.of(properties.getSourceRoot());
        if (Files.exists(configured)) {
            return configured;
        }
        Path fallback = Path.of("trade-clearing-compactor-demo", properties.getSourceRoot());
        if (Files.exists(fallback)) {
            return fallback;
        }
        return configured;
    }

    public McpJsonRpcResponse dispatch(McpJsonRpcRequest request) {
        if (request == null || request.method() == null || request.method().isBlank()) {
            return McpJsonRpcResponse.error(request != null ? request.id() : null, -32600, "Invalid Request");
        }

        String method = request.method().trim();

        if ("initialize".equals(method)) {
            return McpJsonRpcResponse.success(request.id(), Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("tools", Map.of("listChanged", false)),
                    "serverInfo", Map.of("name", "spring-ai-context-compactor", "version", "0.1.0")
            ));
        }

        if (method.startsWith("notifications/")) {
            return null;
        }

        if ("ping".equals(method)) {
            return McpJsonRpcResponse.success(request.id(), Map.of());
        }

        if ("tools/list".equals(method)) {
            return McpJsonRpcResponse.success(request.id(), Map.of("tools", listTools()));
        }

        if ("tools/call".equals(method)) {
            return handleToolsCall(request);
        }

        return McpJsonRpcResponse.error(request.id(), -32601, "Method not found: " + method);
    }

    public List<McpToolDefinition> listTools() {
        return List.of(
                new McpToolDefinition(
                        "compact_spring_file",
                        "Compacts a single .java Spring source file into a line-anchored skeleton preserving Spring annotations, JPA mappings, and proxy traps",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "Relative file path or simple class name (e.g. TradeClearingService or ClearedTrade.java)"),
                                        "fileOrClassQuery", Map.of("type", "string", "description", "Alternative query parameter name for relative path or class name"),
                                        "sourceRoot", Map.of("type", "string", "description", "Optional override for Java source root directory")
                                ),
                                "required", List.of("query")
                        )
                ),
                new McpToolDefinition(
                        "compact_spring_slice",
                        "Compacts all Spring source files in a package or directory slice into token-budgeted skeleton bundles",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "sliceQuery", Map.of("type", "string", "description", "Package name, directory path, or class name filter substring (empty matches all files)"),
                                        "maxTokenBudget", Map.of("type", "integer", "description", "Maximum token budget for compacted output (0 for unlimited)"),
                                        "sourceRoot", Map.of("type", "string", "description", "Optional override for Java source root directory")
                                )
                        )
                ),
                new McpToolDefinition(
                        "read_spring_method_body",
                        "Unfolds exact 1-based line-numbered raw source code for a specific method body on demand",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "classQuery", Map.of("type", "string", "description", "Target class name or file path"),
                                        "fileOrClassQuery", Map.of("type", "string", "description", "Alternative query parameter name for target class name"),
                                        "methodName", Map.of("type", "string", "description", "Target method name to unfold"),
                                        "sourceRoot", Map.of("type", "string", "description", "Optional override for Java source root directory")
                                ),
                                "required", List.of("classQuery", "methodName")
                        )
                ),
                new McpToolDefinition(
                        "analyze_compaction_budget",
                        "Calculates project-wide and per-stereotype token savings, line reductions, and detected Spring proxy traps",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "sourceRoot", Map.of("type", "string", "description", "Optional override for Java source root directory")
                                )
                        )
                )
        );
    }

    @SuppressWarnings("unchecked")
    private McpJsonRpcResponse handleToolsCall(McpJsonRpcRequest request) {
        Map<String, Object> params = request.params();
        String toolName = (String) params.get("name");
        if (toolName == null || toolName.isBlank()) {
            return McpJsonRpcResponse.error(request.id(), -32602, "Missing required parameter 'name' in tools/call");
        }

        Map<String, Object> arguments;
        Object rawArgs = params.get("arguments");
        if (rawArgs instanceof Map<?, ?> argMap) {
            arguments = (Map<String, Object>) argMap;
        } else {
            arguments = params;
        }

        return switch (toolName.trim()) {
            case "compact_spring_file" -> callCompactSpringFile(request.id(), arguments);
            case "compact_spring_slice" -> callCompactSpringSlice(request.id(), arguments);
            case "read_spring_method_body" -> callReadSpringMethodBody(request.id(), arguments);
            case "analyze_compaction_budget" -> callAnalyzeCompactionBudget(request.id(), arguments);
            default -> McpJsonRpcResponse.error(request.id(), -32601, "Unknown tool: " + toolName);
        };
    }

    private McpJsonRpcResponse callCompactSpringFile(Object requestId, Map<String, Object> arguments) {
        String query = extractString(arguments, "query");
        if (query == null || query.isBlank()) {
            query = extractString(arguments, "fileOrClassQuery");
        }
        if (query == null || query.isBlank()) {
            return McpJsonRpcResponse.error(requestId, -32602, "Missing required argument: query");
        }

        String sourceRootOverride = extractString(arguments, "sourceRoot");
        Path root = resolveSourceRoot(sourceRootOverride);

        SliceCompactionReportDto report = sliceExpander.compactSlice(root, query, 0);
        if (report.files().isEmpty()) {
            return McpJsonRpcResponse.error(requestId, -32602, "No Java file found matching query: " + query);
        }

        String targetQuery = query;
        CompactedFileResultDto matchedFile = report.files().stream()
                .filter(f -> f.className().equalsIgnoreCase(targetQuery)
                        || f.filePath().equalsIgnoreCase(targetQuery)
                        || f.filePath().endsWith("/" + targetQuery)
                        || f.filePath().endsWith("\\" + targetQuery))
                .findFirst()
                .orElse(report.files().get(0));

        return McpJsonRpcResponse.success(requestId, Map.of(
                "content", List.of(Map.of("type", "text", "text", matchedFile.compactedSkeleton())),
                "structuredContent", matchedFile,
                "isError", false
        ));
    }

    private McpJsonRpcResponse callCompactSpringSlice(Object requestId, Map<String, Object> arguments) {
        String sliceQuery = extractString(arguments, "sliceQuery");
        if (sliceQuery == null) {
            sliceQuery = extractString(arguments, "query");
        }
        if (sliceQuery == null) {
            sliceQuery = "";
        }

        int maxBudget = extractInt(arguments, "maxTokenBudget", 0);
        String sourceRootOverride = extractString(arguments, "sourceRoot");
        Path root = resolveSourceRoot(sourceRootOverride);

        SliceCompactionReportDto report = sliceExpander.compactSlice(root, sliceQuery, maxBudget);

        return McpJsonRpcResponse.success(requestId, Map.of(
                "content", List.of(Map.of("type", "text", "text", report.combinedSkeleton())),
                "structuredContent", report,
                "isError", false
        ));
    }

    private McpJsonRpcResponse callReadSpringMethodBody(Object requestId, Map<String, Object> arguments) {
        String classQuery = extractString(arguments, "classQuery");
        if (classQuery == null || classQuery.isBlank()) {
            classQuery = extractString(arguments, "fileOrClassQuery");
        }
        String methodName = extractString(arguments, "methodName");

        if (classQuery == null || classQuery.isBlank() || methodName == null || methodName.isBlank()) {
            return McpJsonRpcResponse.error(requestId, -32602, "Missing required argument: classQuery and methodName are required");
        }

        String sourceRootOverride = extractString(arguments, "sourceRoot");
        Path root = resolveSourceRoot(sourceRootOverride);

        List<MethodBodySliceDto> slices = sliceExpander.expandMethodBody(root, classQuery, methodName);
        if (slices.isEmpty()) {
            return McpJsonRpcResponse.error(requestId, -32602, "Method '" + methodName + "' not found in class matching: " + classQuery);
        }

        String formattedText = slices.stream()
                .map(slice -> "// [METHOD SLICE] " + slice.filePath() + " | " + slice.className() + "#" + slice.methodName()
                        + " (L" + slice.startLine() + "-L" + slice.endLine() + ", ~" + slice.estimatedTokens() + " tokens)\n"
                        + slice.numberedSourceLines())
                .collect(Collectors.joining("\n\n"));

        return McpJsonRpcResponse.success(requestId, Map.of(
                "content", List.of(Map.of("type", "text", "text", formattedText)),
                "structuredContent", slices,
                "isError", false
        ));
    }

    private McpJsonRpcResponse callAnalyzeCompactionBudget(Object requestId, Map<String, Object> arguments) {
        String sourceRootOverride = extractString(arguments, "sourceRoot");
        Path root = resolveSourceRoot(sourceRootOverride);

        ProjectCompactionBudgetDto budget = budgetService.analyzeProjectBudget(root);
        String jsonText;
        try {
            jsonText = objectMapper.writeValueAsString(budget);
        } catch (JsonProcessingException e) {
            jsonText = budget.toString();
        }

        return McpJsonRpcResponse.success(requestId, Map.of(
                "content", List.of(Map.of("type", "text", "text", jsonText)),
                "structuredContent", budget,
                "isError", false
        ));
    }

    private String extractString(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private int extractInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
