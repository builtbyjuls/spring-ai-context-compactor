package com.builtbyjuls.compactor.mcp;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.compactor.StereotypeClassifier;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpStdioServerTest {

    private McpStdioServer server;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        CompactorProperties properties = new CompactorProperties();
        properties.setSourceRoot(tempDir.toString());

        StereotypeClassifier classifier = new StereotypeClassifier();
        SpringProxyTrapScanner trapScanner = new SpringProxyTrapScanner();
        SpringAstCompactor astCompactor = new SpringAstCompactor(classifier, trapScanner, properties);
        SpringSliceExpander sliceExpander = new SpringSliceExpander(astCompactor, trapScanner, properties);
        CompactionBudgetService budgetService = new CompactionBudgetService(astCompactor);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        McpCompactorDispatcher dispatcher = new McpCompactorDispatcher(
                astCompactor,
                sliceExpander,
                budgetService,
                properties,
                objectMapper
        );
        server = new McpStdioServer(dispatcher, objectMapper);
    }

    @Test
    @DisplayName("Streams requests and responses over stdio, skipping notifications")
    void streamsRequestsAndResponses() throws IOException {
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"ping","params":{}}
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.serve(in, out);

        String output = out.toString(StandardCharsets.UTF_8);
        List<String> lines = output.lines().filter(l -> !l.isBlank()).toList();

        // Exactly 2 response lines (ping and tools/list; notification produces no output)
        assertThat(lines).hasSize(2);

        JsonNode resp1 = objectMapper.readTree(lines.get(0));
        assertThat(resp1.get("id").asInt()).isEqualTo(1);
        assertThat(resp1.has("result")).isTrue();

        JsonNode resp2 = objectMapper.readTree(lines.get(1));
        assertThat(resp2.get("id").asInt()).isEqualTo(2);
        assertThat(resp2.get("result").get("tools").size()).isEqualTo(4);
    }

    @Test
    @DisplayName("Returns -32700 Parse error on malformed JSON line")
    void handlesMalformedJsonLine() throws IOException {
        String input = "{ malformed json \n";

        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.serve(in, out);

        String output = out.toString(StandardCharsets.UTF_8);
        List<String> lines = output.lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(1);

        JsonNode resp = objectMapper.readTree(lines.get(0));
        assertThat(resp.has("error")).isTrue();
        assertThat(resp.get("error").get("code").asInt()).isEqualTo(-32700);
        assertThat(resp.get("error").get("message").asText()).contains("Parse error");
        assertThat(resp.has("id")).isTrue();
        assertThat(resp.get("id").isNull()).isTrue();
        assertThat(resp.has("result")).isFalse();
    }

    @Test
    @DisplayName("Skips empty and whitespace lines")
    void skipsEmptyLines() throws IOException {
        String input = "\n   \n\n{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"ping\",\"params\":{}}\n\n";

        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.serve(in, out);

        String output = out.toString(StandardCharsets.UTF_8);
        List<String> lines = output.lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(1);

        JsonNode resp = objectMapper.readTree(lines.get(0));
        assertThat(resp.get("id").asInt()).isEqualTo(99);
    }
}
