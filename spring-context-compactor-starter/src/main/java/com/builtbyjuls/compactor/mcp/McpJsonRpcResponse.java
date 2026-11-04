package com.builtbyjuls.compactor.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

public record McpJsonRpcResponse(
        String jsonrpc,
        Object id,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Object result,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        McpError error
) {
    public static McpJsonRpcResponse success(Object id, Object result) {
        return new McpJsonRpcResponse("2.0", id, result, null);
    }

    public static McpJsonRpcResponse error(Object id, int code, String message) {
        return new McpJsonRpcResponse("2.0", id, null, new McpError(code, message, null));
    }

    public static McpJsonRpcResponse error(Object id, int code, String message, Object data) {
        return new McpJsonRpcResponse("2.0", id, null, new McpError(code, message, data));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpError(
            int code,
            String message,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Object data
    ) {
        public McpError(int code, String message) {
            this(code, message, null);
        }
    }
}
