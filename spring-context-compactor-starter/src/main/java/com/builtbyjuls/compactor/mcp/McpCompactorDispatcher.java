package com.builtbyjuls.compactor.mcp;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.CompactedMethodSummaryDto;
import com.builtbyjuls.compactor.model.MethodBodySliceDto;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.SliceCompactionReportDto;
import com.builtbyjuls.compactor.model.SpringStereotype;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class McpCompactorDispatcher {

    private static final int MAX_INLINE_BEFORE_EACH_LINES = 25;
    private static final int MAX_INLINE_MCP_RESPONSE_CHARS = 9500;

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
                                        "expandMethods", Map.of("type", "string", "description", "Optional comma-separated method names or * wildcard patterns (e.g. 'savePet,*Visit*') to unfold with exact raw source lines"),
                                        "expandTrappedMethods", Map.of("type", "boolean", "description", "Automatically unfold 1-based line-numbered method bodies for any methods involved in detected @Transactional proxy traps (default: true)"),
                                        "focusMode", Map.of("type", "boolean", "description", "When true (default when expandMethods is provided), collapses untouched non-trapped methods into a compact 1-line summary index"),
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
                        "Unfolds exact 1-based line-numbered raw source code for one or more methods on demand. Pass a comma-separated list in methodName (e.g. 'savePet,findPetTypeById') or methodNames to unfold multiple methods in a single call",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "classQuery", Map.of("type", "string", "description", "Target class name or file path"),
                                        "fileOrClassQuery", Map.of("type", "string", "description", "Alternative query parameter name for target class name"),
                                        "methodName", Map.of("type", "string", "description", "Target method name to unfold"),
                                        "methodNames", Map.of("type", "string", "description", "Optional comma-separated list of method names to unfold in a single call"),
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

        Set<String> explicitMethods = new LinkedHashSet<>();
        Object rawExpandMethods = arguments.get("expandMethods");
        if (rawExpandMethods == null) {
            rawExpandMethods = arguments.get("methodNames");
        }
        if (rawExpandMethods instanceof String s && !s.isBlank()) {
            for (String m : s.split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty()) {
                    explicitMethods.add(trimmed);
                }
            }
        } else if (rawExpandMethods instanceof Collection<?> col) {
            for (Object item : col) {
                if (item != null) {
                    for (String m : item.toString().split(",")) {
                        String trimmed = m.trim();
                        if (!trimmed.isEmpty()) {
                            explicitMethods.add(trimmed);
                        }
                    }
                }
            }
        }

        boolean hasExplicitExpandMethods = !explicitMethods.isEmpty();

        boolean expandTrapped = extractBoolean(arguments, "expandTrappedMethods", properties.isAutoExpandTrappedMethodsInMcp());
        Set<String> methodsToExpand = new LinkedHashSet<>();

        if (expandTrapped && !matchedFile.trapNotes().isEmpty()) {
            for (MethodTrapNoteDto trap : matchedFile.trapNotes()) {
                if (trap.callerMethod() != null && !trap.callerMethod().isBlank()) {
                    methodsToExpand.add(trap.callerMethod().trim());
                }
                if (trap.targetMethod() != null && !trap.targetMethod().isBlank()) {
                    methodsToExpand.add(trap.targetMethod().trim());
                }
            }
        }

        if (matchedFile.stereotype() == SpringStereotype.TEST && expandTrapped) {
            for (CompactedMethodSummaryDto method : matchedFile.methods()) {
                boolean hasBeforeEach = method.retainedAnnotations().stream()
                        .anyMatch(ann -> "BeforeEach".equals(extractAnnotationSimpleName(ann)));
                if (hasBeforeEach && (method.omittedLineCount() <= MAX_INLINE_BEFORE_EACH_LINES || !hasExplicitExpandMethods)) {
                    methodsToExpand.add(method.methodName());
                }
            }
        }

        methodsToExpand.addAll(explicitMethods);

        List<MethodBodySliceDto> autoSlices = List.of();
        if (!methodsToExpand.isEmpty()) {
            autoSlices = sliceExpander.expandMethodBodiesWithCallees(root, matchedFile.filePath(), methodsToExpand);
        }

        boolean focusMode = extractBoolean(arguments, "focusMode", hasExplicitExpandMethods);
        String responseText = matchedFile.compactedSkeleton();
        if (focusMode && !autoSlices.isEmpty()) {
            responseText = renderFocusedSkeleton(matchedFile, autoSlices);
        }

        if (!autoSlices.isEmpty()) {
            String header = "\n\n// [AUTO-EXPANDED METHOD SLICES] (Exact raw source lines ready for edit_file Target)\n";
            StringBuilder slicesBuilder = new StringBuilder();
            int includedCount = 0;
            int baseLength = responseText.stripTrailing().length() + header.length();

            for (MethodBodySliceDto slice : autoSlices) {
                String formatted = formatRawMethodSlice(slice);
                int separatorLen = includedCount > 0 ? 2 : 0;
                if (includedCount > 0 && baseLength + slicesBuilder.length() + separatorLen + formatted.length() > MAX_INLINE_MCP_RESPONSE_CHARS) {
                    int remaining = autoSlices.size() - includedCount;
                    slicesBuilder.append("\n\n// [").append(remaining)
                            .append(" additional matched method slice(s) omitted to stay within inline tool limit; call read_spring_method_body for specific methods]");
                    break;
                }
                if (includedCount > 0) {
                    slicesBuilder.append("\n\n");
                }
                slicesBuilder.append(formatted);
                includedCount++;
            }
            responseText = responseText.stripTrailing() + header + slicesBuilder;
        }

        return McpJsonRpcResponse.success(requestId, Map.of(
                "content", List.of(Map.of("type", "text", "text", responseText)),
                "structuredContent", matchedFile,
                "isError", false
        ));
    }

    private String renderFocusedSkeleton(CompactedFileResultDto file, List<MethodBodySliceDto> expandedSlices) {
        Set<String> expandedMethodNames = expandedSlices.stream()
                .map(MethodBodySliceDto::methodName)
                .collect(Collectors.toSet());

        List<CompactedMethodSummaryDto> omittedMethods = new ArrayList<>();
        for (CompactedMethodSummaryDto method : file.methods()) {
            boolean isExpanded = expandedMethodNames.contains(method.methodName());
            boolean hasTraps = method.trapNotes() != null && !method.trapNotes().isEmpty();
            if (!isExpanded && !hasTraps) {
                omittedMethods.add(method);
            }
        }

        if (omittedMethods.isEmpty()) {
            return file.compactedSkeleton();
        }

        String skeleton = file.compactedSkeleton();
        for (CompactedMethodSummaryDto omitted : omittedMethods) {
            String block = buildMethodCompactedBlock(omitted, file.stereotype());
            if (skeleton.contains("\n" + block)) {
                skeleton = skeleton.replace("\n" + block, "");
            } else if (skeleton.contains(block)) {
                skeleton = skeleton.replace(block, "");
            } else {
                int sigIdx = skeleton.indexOf(omitted.signature());
                if (sigIdx >= 0) {
                    int lineStart = skeleton.lastIndexOf('\n', sigIdx);
                    lineStart = lineStart < 0 ? 0 : lineStart + 1;
                    int endIdx = skeleton.indexOf("\n", sigIdx);
                    if (endIdx < 0) {
                        endIdx = skeleton.length();
                    } else {
                        endIdx++;
                    }
                    int startIdx = lineStart;
                    for (int i = 0; i < omitted.retainedAnnotations().size(); i++) {
                        int prevLineEnd = startIdx - 1;
                        if (prevLineEnd >= 0 && skeleton.charAt(prevLineEnd) == '\n') {
                            int prevLineStart = skeleton.lastIndexOf('\n', prevLineEnd - 1);
                            prevLineStart = prevLineStart < 0 ? 0 : prevLineStart + 1;
                            String prevLine = skeleton.substring(prevLineStart, prevLineEnd).trim();
                            if (prevLine.startsWith("@")) {
                                startIdx = prevLineStart;
                            } else {
                                break;
                            }
                        }
                    }
                    if (startIdx > 0 && skeleton.charAt(startIdx - 1) == '\n') {
                        startIdx--;
                    }
                    skeleton = skeleton.substring(0, startIdx) + skeleton.substring(endIdx);
                }
            }
        }

        while (skeleton.contains("\n\n\n")) {
            skeleton = skeleton.replace("\n\n\n", "\n\n");
        }

        String summaryEntries = omittedMethods.stream()
                .map(m -> m.methodName() + "(L" + m.startLine() + "-L" + m.endLine() + ")")
                .collect(Collectors.joining(", "));
        String summaryLine = "    // Other Methods (" + omittedMethods.size() + " omitted): " + summaryEntries + "\n";

        int lastBraceIndex = skeleton.lastIndexOf('}');
        if (lastBraceIndex >= 0) {
            String prefix = skeleton.substring(0, lastBraceIndex);
            StringBuilder sb = new StringBuilder(prefix);
            if (!prefix.endsWith("\n")) {
                sb.append("\n");
            }
            if (!prefix.endsWith("\n\n")) {
                sb.append("\n");
            }
            sb.append(summaryLine);
            sb.append(skeleton.substring(lastBraceIndex));
            skeleton = sb.toString();
        } else {
            skeleton = skeleton + "\n" + summaryLine;
        }

        return skeleton;
    }

    private boolean isSingleLineTestMethod(CompactedMethodSummaryDto method, SpringStereotype stereotype) {
        if (stereotype != SpringStereotype.TEST || method.omittedLineCount() == 0) {
            return false;
        }
        for (String ann : method.retainedAnnotations()) {
            String simple = extractAnnotationSimpleName(ann);
            if ("Test".equals(simple) || "ParameterizedTest".equals(simple) || "RepeatedTest".equals(simple)) {
                return true;
            }
        }
        return false;
    }

    private String extractAnnotationSimpleName(String annotationStr) {
        String s = annotationStr.trim();
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        int paren = s.indexOf('(');
        if (paren >= 0) {
            s = s.substring(0, paren).trim();
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private String buildMethodCompactedBlock(CompactedMethodSummaryDto method, SpringStereotype stereotype) {
        if (isSingleLineTestMethod(method, stereotype)) {
            StringBuilder sb = new StringBuilder();
            sb.append("    ");
            for (String ann : method.retainedAnnotations()) {
                sb.append(ann).append(" ");
            }
            sb.append(method.signature());
            if (properties.isIncludeLineNumbers()) {
                sb.append(" /* L").append(method.startLine()).append("-L").append(method.endLine())
                        .append(": body omitted (").append(method.omittedLineCount()).append(" lines)");
            } else {
                sb.append(" /* body omitted (").append(method.omittedLineCount()).append(" lines)");
            }
            if (method.trapNotes() != null && !method.trapNotes().isEmpty()) {
                for (MethodTrapNoteDto trap : method.trapNotes()) {
                    sb.append(" | [TRAP: ").append(trap.trapType().name()).append("] ").append(trap.summary());
                }
            }
            sb.append(" */ ;\n");
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        for (String ann : method.retainedAnnotations()) {
            sb.append("    ").append(ann).append("\n");
        }
        sb.append("    ").append(method.signature());
        if (method.omittedLineCount() == 0) {
            sb.append(";\n");
        } else if (properties.isIncludeLineNumbers()) {
            sb.append(" /* L").append(method.startLine()).append("-L").append(method.endLine())
                    .append(": body omitted (").append(method.omittedLineCount()).append(" lines)");
            if (method.trapNotes() != null && !method.trapNotes().isEmpty()) {
                for (MethodTrapNoteDto trap : method.trapNotes()) {
                    sb.append(" | [TRAP: ").append(trap.trapType().name()).append("] ").append(trap.summary());
                }
            }
            sb.append(" */ ;\n");
        } else {
            sb.append(" /* body omitted (").append(method.omittedLineCount()).append(" lines)");
            if (method.trapNotes() != null && !method.trapNotes().isEmpty()) {
                for (MethodTrapNoteDto trap : method.trapNotes()) {
                    sb.append(" | [TRAP: ").append(trap.trapType().name()).append("] ").append(trap.summary());
                }
            }
            sb.append(" */ ;\n");
        }
        return sb.toString();
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

        Set<String> requestedMethods = new LinkedHashSet<>();
        Object rawMethodName = arguments.get("methodName");
        if (rawMethodName instanceof String s && !s.isBlank()) {
            for (String m : s.split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty()) {
                    requestedMethods.add(trimmed);
                }
            }
        }
        Object rawMethodNames = arguments.get("methodNames");
        if (rawMethodNames instanceof String s && !s.isBlank()) {
            for (String m : s.split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty()) {
                    requestedMethods.add(trimmed);
                }
            }
        } else if (rawMethodNames instanceof Collection<?> col) {
            for (Object item : col) {
                if (item != null) {
                    for (String m : item.toString().split(",")) {
                        String trimmed = m.trim();
                        if (!trimmed.isEmpty()) {
                            requestedMethods.add(trimmed);
                        }
                    }
                }
            }
        }

        if (classQuery == null || classQuery.isBlank() || requestedMethods.isEmpty()) {
            return McpJsonRpcResponse.error(requestId, -32602, "Missing required argument: classQuery and methodName are required");
        }

        String combinedMethodNames = String.join(",", requestedMethods);

        String sourceRootOverride = extractString(arguments, "sourceRoot");
        Path root = resolveSourceRoot(sourceRootOverride);

        List<MethodBodySliceDto> slices = sliceExpander.expandMethodBody(root, classQuery, combinedMethodNames);
        if (slices.isEmpty()) {
            return McpJsonRpcResponse.error(requestId, -32602, "Method '" + combinedMethodNames + "' not found in class matching: " + classQuery);
        }

        String formattedText = slices.stream()
                .map(this::formatRawMethodSlice)
                .collect(Collectors.joining("\n\n"));

        return McpJsonRpcResponse.success(requestId, Map.of(
                "content", List.of(Map.of("type", "text", "text", formattedText)),
                "structuredContent", slices,
                "isError", false
        ));
    }

    private String formatRawMethodSlice(MethodBodySliceDto slice) {
        String rawLines = slice.numberedSourceLines().lines()
                .map(line -> line.replaceFirst("^\\s*\\d+:\\s?", ""))
                .collect(Collectors.joining("\n"));
        return "// [METHOD SLICE] " + slice.filePath() + " | " + slice.className() + "#" + slice.methodName()
                + " (L" + slice.startLine() + "-L" + slice.endLine() + ", ~" + slice.estimatedTokens() + " tokens)\n"
                + rawLines;
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

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s.trim());
        }
        return defaultValue;
    }
}
