# Spring AI Context Compactor & MCP Server

A reusable **Spring Boot 4.1 starter, Spring AI 2.0 `CallAdvisor`, and Model Context Protocol (MCP) server** (`spring-context-compactor-starter`) that performs **Spring-semantic AST compaction** on Java source files, cutting LLM context token consumption by **58.8% across repositories** (**80.4% on JPA entities**, **70.8% in Spring AI prompts**) while preserving **100.0%** of Spring wiring, JPA mappings, `@Transactional` rules, and inline proxy-bypass warnings.

* **Tech Stack**: **Java 21** (Virtual Threads), **Spring Boot 4.1.0**, **Spring AI 2.0.1**, **JavaParser 3.28.0**, **Spring AMQP (RabbitMQ 3.13)**, **PostgreSQL 16** (Testcontainers), **Hibernate ORM 7.x**.
* **Scope**: Provides both a **Spring AI `CallAdvisor`** (`SpringContextCompactorAdvisor`) that automatically compresses Java code blocks in LLM prompts and deterministic **Model Context Protocol (MCP)** tools (`compact_spring_file`, `compact_spring_slice`, `read_spring_method_body`, `analyze_compaction_budget`) over stdio and HTTP for AI coding agents.

---

## 1. The Problem & Architectural Motivation

Enterprise **Spring Boot** source files waste thousands of context tokens when fed to LLMs in RAG pipelines or read by AI coding agents (`read_file` / MCP):

1. **Java Boilerplate Overload**: Standard JPA `@Entity` classes, `@Service` orchestrators, and `@RestController` endpoints are padded with getters, setters, `equals`/`hashCode`, `toString`, builders, import walls, and private helper method bodies that consume **65% to 80%** of the context window without adding architectural signal.
2. **Generic Skeletonizers Erase Spring Semantics & Hide Proxy Traps**: Naive Tree-sitter or regex minifiers strip method bodies blindly - erasing `@Query` JPQL strings and `@Bean` wiring, and hiding intra-bean `@Transactional` self-invocation traps (`this.recordInternalComplianceStamp(...)`) buried inside method bodies before the agent can see them.
3. **Full-File Reads for Surgical Edits**: Coding agents routinely read an entire 300-line `@Entity` and 180-line `@Service` just to inspect injected collaborator types and edit a single method.

**`spring-context-compactor-starter`** solves all three problems with deterministic, sub-15ms JavaParser AST compaction:

1. **Stereotype-Aware AST Pruning (`SpringAstCompactor`)**: Applies custom compaction rules per Spring stereotype (`@Entity`, `@Service`, `@RestController`, `@Repository`, `@Configuration`, `@RabbitListener`), stripping boilerplate and method bodies while keeping every contract and exact 1-based source line range (`/* L52-L120: body omitted (68 lines) */`).
2. **Pre-Prune Inline Proxy Trap Pinning (`SpringProxyTrapScanner`)**: Scans method bodies *before* stripping them so intra-bean `@Transactional` self-invocation (`SELF_INVOCATION_BYPASS`), read-only write leaks (`READ_ONLY_CALLING_WRITE_REQUIRED`), and non-public transactional methods (`NON_PUBLIC_TRANSACTIONAL`) are pinned inline on the compacted method signature with exact line numbers.
3. **Surgical Slice & Method Unfolding (`SpringSliceExpander`)**: Compacts an entire package slice within a token budget and unfolds only the target `className#methodName` body on demand with 1-based line numbers.
4. **Zero-Touch Spring AI `CallAdvisor` (`SpringContextCompactorAdvisor`)**: Plugs directly into Spring AI 2.0 `ChatClient` pipelines to automatically compact fenced ` ```java ` blocks in `UserMessage` and `SystemMessage` prompts before LLM invocation.

---

## 2. Portfolio Evidence & Performance Gains Matrix

Measured by `CompactorBenchmarkIntegrationTest` against the live 21-file institutional FX and derivatives clearing house application (`trade-clearing-compactor-demo/src/main/java`):

| Metric | Raw File / Naive Baseline | Spring Compactor Result | Measured Gain | Architectural Evidence |
| --- | --- | --- | --- | --- |
| **Repository Context Compaction (Full Source Scan vs AST Skeleton)** | 21 files / 1,462 lines / ~11,730 tokens | 21 skeletons / 417 lines / ~4,828 tokens | **-58.8% tokens (-6,902 tokens)** | Compacts all Spring stereotypes (`@RestController`, `@Service`, `@Repository`, `@Entity`, `@Configuration`) into compilable-style Java skeletons |
| **Surgical Method Edit Workflow (Read Full Files vs Skeleton + Expand Method)** | 2 full files (~4,113 tokens) | 1 skeleton + 1 method (~1,639 tokens in 12.70 ms) | **-60.2% tokens (12.70 ms MCP latency)** | Agent inspects class skeleton with `Lstart-Lend` markers and unfolds only the target method body on demand |
| **Spring Semantic Contract & Wiring Retention** | 106 / 106 Spring wiring & JPA/AMQP contracts | 106 / 106 contracts retained in skeletons | **100.0% contract retention** | Retains DI fields, `@Transactional` attributes, `@RabbitListener` queues, `@Query` JPQL strings, and `@Entity` `@Column` mappings |
| **Pre-Prune `@Transactional` Proxy Trap Recall** | 3 / 4 internal call-site traps lost after naive body strip | 4 / 4 traps pinned inline with L-numbers | **100.0% trap recall (pre-prune AST scan)** | Scans AST before body stripping to pin `SELF_INVOCATION_BYPASS`, `READ_ONLY_CALLING_WRITE_REQUIRED`, and `NON_PUBLIC_TRANSACTIONAL` inline |
| **Spring AI `CallAdvisor` Prompt Compression** | ~4,179 tokens / request (4,179,000 tokens / 1k calls) | ~1,220 tokens / request (1,220,000 tokens / 1k calls) | **-70.8% prompt tokens (-2,959,000 tokens / 1k calls)** | Zero-touch Spring AI 2.0 `CallAdvisor` compacts fenced Java blocks in `UserMessage` and `SystemMessage` before `ChatModel` call |

### Per-Stereotype Compaction Breakdown

| Stereotype | Files | Original Lines | Compacted Lines | Original Tokens | Compacted Tokens | Token Reduction % |
| --- | --- | --- | --- | --- | --- | --- |
| `CONTROLLER` | 1 | 47 | 26 | ~535 | ~328 | **38.7%** |
| `SERVICE` | 2 | 203 | 65 | ~2,246 | ~1,030 | **54.1%** |
| `ENTITY` | 4 | 863 | 137 | ~5,707 | ~1,120 | **80.4%** |
| `REPOSITORY` | 4 | 60 | 46 | ~633 | ~626 | **1.1%** |
| `CONFIGURATION` | 1 | 83 | 37 | ~810 | ~484 | **40.2%** |
| `MESSAGING_LISTENER` | 1 | 20 | 15 | ~186 | ~178 | **4.3%** |
| `COMPONENT` | 8 | 186 | 91 | ~1,613 | ~1,062 | **34.2%** |

### Detected `@Transactional` Proxy Traps

| Class | Method | Line | Trap Type | Description |
| --- | --- | --- | --- | --- |
| `ClearingAuditService` | `recordComplianceStamp` | `L25` | `SELF_INVOCATION_BYPASS` | Method `'recordComplianceStamp'` calls `@Transactional` method `'recordAuditEvent'` via intra-class invocation bypassing Spring AOP proxy |
| `TradeClearingService` | `inspectMemberPortfolioWithSnapshotTouch` | `L136` | `READ_ONLY_CALLING_WRITE_REQUIRED` | Method `'inspectMemberPortfolioWithSnapshotTouch'` (`@Transactional(readOnly = true)`) calls `'recordInternalComplianceStamp'` (write `@Transactional`) via `this` reference; read-only transaction context persists and write operations may fail or violate isolation |
| `TradeClearingService` | `inspectMemberPortfolioWithSnapshotTouch` | `L136` | `SELF_INVOCATION_BYPASS` | Method `'inspectMemberPortfolioWithSnapshotTouch'` calls `@Transactional` method `'recordInternalComplianceStamp'` via intra-class invocation bypassing Spring AOP proxy |
| `TradeClearingService` | `archiveExpiredTradeNotice` | `L140` | `NON_PUBLIC_TRANSACTIONAL` | Method `'archiveExpiredTradeNotice'` is annotated with `@Transactional` but is not public (Spring AOP proxy cannot intercept non-public methods) |

---

## 3. Architecture Flow

```mermaid
flowchart TD
    Agent["AI Coding Agent / Spring AI ChatClient"] -- "MCP JSON-RPC 2.0 / CallAdvisor" --> Entry{"McpCompactorDispatcher / SpringContextCompactorAdvisor"}

    Entry -- "compact_spring_file / compact_spring_slice" --> Classifier["StereotypeClassifier (JavaParser AST)"]
    Classifier --> TrapScan["SpringProxyTrapScanner (Pre-Prune Trap Extraction)"]
    TrapScan --> Pruner["SpringAstCompactor (Layer-Specific AST Pruning)"]

    Entry -- "read_spring_method_body" --> Expander["SpringSliceExpander (Exact Line-Range Unfolding)"]
    Entry -- "analyze_compaction_budget" --> Budget["CompactionBudgetService & CompactorReportExporter"]

    Pruner --> Output["Compacted Java Skeleton + Line Anchors + Inline Trap Notes"]
    Expander --> Output
    Budget --> Output
```

---

## 4. Before vs After Code Comparison

### Before (`TradeClearingService.java` - 176 lines, ~1,986 tokens)

```java
@Service
public class TradeClearingService {
    private final ClearingMemberRepository clearingMemberRepository;
    private final ClearedTradeRepository clearedTradeRepository;
    private final MarginCallRecordRepository marginCallRecordRepository;
    private final MarginCalculator marginCalculator;
    private final ClearingAuditService clearingAuditService;
    private final ClearingAuditLogRepository clearingAuditLogRepository;
    private final MarginCallPublisher marginCallPublisher;

    // ... 70-line constructor and novateAndClearTrade implementation ...

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public List<ClearedTrade> inspectMemberPortfolioWithSnapshotTouch(String memberCode) {
        List<ClearedTrade> trades = clearedTradeRepository.findByMemberCodeOrderByClearedAtDesc(memberCode);
        this.recordInternalComplianceStamp(memberCode, "PORTFOLIO_INSPECTION");
        return trades;
    }

    @Transactional
    protected void archiveExpiredTradeNotice(String tradeId) {
        ClearingAuditLog log = ClearingAuditLog.builder()
                .tradeId(tradeId)
                .eventType("EXPIRED_TRADE_ARCHIVED")
                .detail("Archived notice for trade " + tradeId)
                .recordedAt(Instant.now())
                .build();
        clearingAuditLogRepository.save(log);
    }
    // ... private helpers ...
}
```

### After (`SpringAstCompactor` Skeleton - 44 lines, ~868 tokens)

```java
// [SPRING COMPACTOR] File: com/builtbyjuls/clearing/trade/TradeClearingService.java
// Stereotype: SERVICE | Original: 176 lines (~1986 tokens) -> Compacted: 44 lines (~868 tokens) | Saved: 56.3%
// Injected Beans: [ClearingMemberRepository, ClearedTradeRepository, MarginCallRecordRepository, MarginCalculator, ClearingAuditService, ClearingAuditLogRepository, MarginCallPublisher]
package com.builtbyjuls.clearing.trade;

@Service
public class TradeClearingService {

    private final ClearingMemberRepository clearingMemberRepository;
    private final ClearedTradeRepository clearedTradeRepository;
    private final MarginCallRecordRepository marginCallRecordRepository;
    private final MarginCalculator marginCalculator;
    private final ClearingAuditService clearingAuditService;
    private final ClearingAuditLogRepository clearingAuditLogRepository;
    private final MarginCallPublisher marginCallPublisher;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ClearedTradePositionDto novateAndClearTrade(TradeSubmissionMessage submission) /* L52-L120: body omitted (68 lines) */ ;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInternalComplianceStamp(String tradeId, String engineName) /* L122-L131: body omitted (9 lines) */ ;

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public List<ClearedTrade> inspectMemberPortfolioWithSnapshotTouch(String memberCode) /* L133-L138: body omitted (5 lines) | [TRAP: READ_ONLY_CALLING_WRITE_REQUIRED] Method 'inspectMemberPortfolioWithSnapshotTouch' (@Transactional(readOnly = true)) calls 'recordInternalComplianceStamp' (write @Transactional) via this reference; read-only transaction context persists and write operations may fail or violate isolation | [TRAP: SELF_INVOCATION_BYPASS] Method 'inspectMemberPortfolioWithSnapshotTouch' calls @Transactional method 'recordInternalComplianceStamp' via intra-class invocation bypassing Spring AOP proxy */ ;

    @Transactional
    protected void archiveExpiredTradeNotice(String tradeId) /* L140-L149: body omitted (9 lines) | [TRAP: NON_PUBLIC_TRANSACTIONAL] Method 'archiveExpiredTradeNotice' is annotated with @Transactional but is not public (Spring AOP proxy cannot intercept non-public methods) */ ;
}
```

---

## 5. MCP Server & Spring AI Integration Guide

### 5.1 Stdio MCP Configuration (Claude Desktop / Cursor / Antigravity)

Add `McpStdioServer` to your agent's MCP configuration file (`mcp_config.json` / `claude_desktop_config.json`). The stdio server parses `.java` files directly with zero database or broker dependencies:

```json
{
  "mcpServers": {
    "spring-context-compactor": {
      "command": "java",
      "args": [
        "-cp",
        "spring-context-compactor-starter/target/spring-context-compactor-starter-0.1.0-SNAPSHOT.jar",
        "com.builtbyjuls.compactor.mcp.McpStdioServer",
        "--source-root=trade-clearing-compactor-demo/src/main/java"
      ]
    }
  }
}
```

### 5.2 HTTP JSON-RPC 2.0 MCP Endpoint

When running a Spring Boot application with `spring-context-compactor-starter` on the classpath:

```bash
# 1. Inspect project-wide compaction budget & proxy traps
curl -s http://localhost:8080/internal/spring-compactor/budget | jq .

# 2. Compact a single class via MCP JSON-RPC 2.0
curl -s -X POST http://localhost:8080/internal/spring-compactor/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "compact_spring_file",
      "arguments": { "query": "TradeClearingService" }
    }
  }' | jq .

# 3. Unfold a single method body with 1-based line numbers
curl -s -X POST http://localhost:8080/internal/spring-compactor/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "read_spring_method_body",
      "arguments": {
        "classQuery": "TradeClearingService",
        "methodName": "novateAndClearTrade"
      }
    }
  }' | jq .
```

### 5.3 Spring AI 2.0 `CallAdvisor` Integration

Register `SpringContextCompactorAdvisor` on your Spring AI `ChatClient` to automatically compact embedded ` ```java ` blocks in outgoing prompts:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(springContextCompactorAdvisor)
        .build();

ChatClientResponse response = chatClient.prompt()
        .user("Review transaction boundaries in:\n```java\n" + rawServiceSource + "\n```")
        .call()
        .chatClientResponse();

AdvisorCompactionStatsDto stats = (AdvisorCompactionStatsDto)
        response.context().get(SpringContextCompactorAdvisor.STATS_CONTEXT_KEY);
```

---

## 6. Repository Structure

* **`spring-context-compactor-starter/`**: Reusable **Spring Boot starter, Spring AI `CallAdvisor`, MCP server, and CI/CD benchmark report exporter** (`@EnableSpringContextCompactor`, `SpringAstCompactor`, `SpringProxyTrapScanner`, `SpringSliceExpander`, `SpringContextCompactorAdvisor`, `McpCompactorDispatcher`, `CompactorEfficiencyBenchmarkService`, `CompactorReportExporter`).
* **`trade-clearing-compactor-demo/`**: Institutional **FX and derivatives trade clearing application** demonstrating Spring-semantic compaction across verbose JPA entities, multi-propagation `@Transactional` services, REST controllers, and RabbitMQ topology.
* **[ARCHITECTURE.md](ARCHITECTURE.md)**: Full **architectural specification**, stereotype pruning matrix, and engineering trade-offs.

---

## 7. Quick Start & Verification Commands

### Prerequisites

* **Java 21+** and **Maven 3.9+**
* **Docker** (required for **Testcontainers PostgreSQL 16 & RabbitMQ 3.13**)

### Run the Test Suite & Generate CI/CD Benchmark Reports

```bash
# Run all 86 unit and Testcontainers integration tests
./mvnw -B clean test
```

Generated report artifacts are written to `trade-clearing-compactor-demo/target/compactor-reports/`:
* `compactor-summary.md` - Markdown summary with the 5-Metric Portfolio Evidence Matrix and Mermaid flow diagram
* `benchmark-report.json` - Machine-readable benchmark metrics (`CompactorBenchmarkReportDto`)
* `compaction-budget.json` - Per-stereotype token reduction and proxy trap inventory (`ProjectCompactionBudgetDto`)
* `junit-compactor.xml` - JUnit XML report for CI/CD test runners

### Run Full Stack via Docker Compose

```bash
docker compose up --build
```
