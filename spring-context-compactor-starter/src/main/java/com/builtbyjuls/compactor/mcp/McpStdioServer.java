package com.builtbyjuls.compactor.mcp;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class McpStdioServer {

    private final McpCompactorDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public McpStdioServer(McpCompactorDispatcher dispatcher, ObjectMapper objectMapper) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public void serve(InputStream inputStream, OutputStream outputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            McpJsonRpcRequest request;
            try {
                request = objectMapper.readValue(trimmed, McpJsonRpcRequest.class);
            } catch (Exception e) {
                McpJsonRpcResponse parseError = McpJsonRpcResponse.error(null, -32700, "Parse error: " + e.getMessage());
                writer.write(objectMapper.writeValueAsString(parseError));
                writer.newLine();
                writer.flush();
                continue;
            }

            McpJsonRpcResponse response = dispatcher.dispatch(request);
            if (response != null) {
                writer.write(objectMapper.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String sourceRootOverride = null;
        for (String arg : args) {
            if (arg != null && arg.startsWith("--source-root=")) {
                sourceRootOverride = arg.substring("--source-root=".length()).trim();
            }
        }

        CompactorProperties properties = new CompactorProperties();
        if (sourceRootOverride != null && !sourceRootOverride.isBlank()) {
            properties.setSourceRoot(sourceRootOverride);
        }

        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        SpringAstCompactor astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);
        SpringSliceExpander sliceExpander = new SpringSliceExpander(astCompactor, trapScanner, properties);
        CompactionBudgetService budgetService = new CompactionBudgetService(astCompactor);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        McpCompactorDispatcher dispatcher = new McpCompactorDispatcher(
                astCompactor,
                sliceExpander,
                budgetService,
                properties,
                objectMapper
        );

        McpStdioServer server = new McpStdioServer(dispatcher, objectMapper);
        server.serve(System.in, System.out);
    }
}
