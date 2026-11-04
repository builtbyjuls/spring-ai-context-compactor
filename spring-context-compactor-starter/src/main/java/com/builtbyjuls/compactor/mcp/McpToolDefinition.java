package com.builtbyjuls.compactor.mcp;

import java.util.Map;
import java.util.Objects;

public record McpToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
    public McpToolDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
    }
}
