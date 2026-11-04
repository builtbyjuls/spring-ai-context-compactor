package com.builtbyjuls.compactor.mcp;

import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/internal/spring-compactor")
public class CompactorMcpHttpController {

    private final CompactionBudgetService budgetService;
    private final McpCompactorDispatcher dispatcher;

    public CompactorMcpHttpController(CompactionBudgetService budgetService, McpCompactorDispatcher dispatcher) {
        this.budgetService = Objects.requireNonNull(budgetService, "budgetService must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    }

    @GetMapping("/budget")
    public ResponseEntity<ProjectCompactionBudgetDto> getBudget() {
        return ResponseEntity.ok(budgetService.analyzeProjectBudget(dispatcher.resolveSourceRoot(null)));
    }

    @PostMapping("/mcp")
    public ResponseEntity<McpJsonRpcResponse> handleMcp(@RequestBody McpJsonRpcRequest request) {
        McpJsonRpcResponse response = dispatcher.dispatch(request);
        if (response == null) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(response);
    }
}
