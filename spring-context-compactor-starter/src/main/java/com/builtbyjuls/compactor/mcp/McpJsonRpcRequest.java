package com.builtbyjuls.compactor.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpJsonRpcRequest(
        String jsonrpc,
        Object id,
        String method,
        Map<String, Object> params
) {
    public McpJsonRpcRequest {
        jsonrpc = (jsonrpc != null && !jsonrpc.isBlank()) ? jsonrpc : "2.0";
        params = params != null ? Collections.unmodifiableMap(new LinkedHashMap<>(params)) : Map.of();
    }
}
