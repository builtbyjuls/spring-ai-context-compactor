# Spring AI Context Compactor & MCP Server

A reusable **Spring Boot 4.1 starter, Spring AI 2.0 `CallAdvisor`, and Model Context Protocol (MCP) server** (`spring-context-compactor-starter`) that performs **Spring-semantic AST compaction** on Java source files - reducing pay-as-you-go coding agent API costs by **16.2% to 24.1%** and uncached input tokens by **29.2% to 45.6%** in verified multi-turn A/B benchmarks (`spring-petclinic-rest`) while preserving **100.0%** of Spring wiring, JPA mappings, `@Transactional` rules, and inline proxy-bypass warnings.

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

## 2. Verified Benchmarks & Performance Gains

### 2.1 Autonomous Coding Agent A/B Benchmark (`spring-petclinic-rest`)

Evaluated on canonical Spring Boot REST service changes in `spring-petclinic-rest` (`OwnerRestControllerV1#addVisitToOwner` bugfix, service validation, and integration tests) comparing standard file exploration against `spring-context-compactor` MCP tools across two frontier model families:

| Metric | Claude Sonnet 3.5 Without MCP | Claude Sonnet 3.5 With MCP | Sonnet Delta | Gemini Pro Without MCP | Gemini Pro With MCP v3 | Gemini Delta |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Planner Turns** | 15 | **14** | **-6.7%** | 40 | **29** | **-27.5%** |
| **Duration (Wall-Clock)** | 69.0s | **64.0s** | **-7.2%** | 378.0s | **375.0s** | **-0.8%** |
| **Peak Context Window** | 49,048 tokens | **45,034 tokens** | **-8.2%** | 62,728 tokens | **62,774 tokens** | +0.1% |
| **Net Context Added (`last - first`)** | +32,163 tokens | **+27,907 tokens** | **-13.2%** | +52,473 tokens | **+51,976 tokens** | **-0.9%** |
| **Cumulative Context (`sum(in + cache)`)** | 557,436 tokens | **482,234 tokens** | **-13.5%** | 1,260,524 tokens | **1,017,014 tokens** | **-19.3%** |
| **Uncached Input Tokens (`sum(in)`)** | 68,668 tokens | **48,626 tokens** | **-29.2%** | 256,474 tokens | **139,439 tokens** | **-45.6%** |
| **Cached Input Tokens (`sum(cache)`)** | 488,768 tokens | **433,608 tokens** | **-11.3%** | 1,004,050 tokens | **877,575 tokens** | **-12.6%** |
| **Output Tokens** | 5,861 tokens | **6,221 tokens** | +6.1% | 23,730 tokens | **23,466 tokens** | **-1.1%** |
| **Raw Production File Reads** | 2 full `cat` dumps (16,580 chars) | **0 full reads (0 chars)** | **-100.0%** | 6 `read_file` calls (28,490 chars) | **0 full reads (0 chars)** | **-100.0%** |
| **Tool Output Spills (`output.txt`)** | 0 spills | **0 spills** | 0 spills | 0 spills | **0 spills** | 0 spills |

### 2.2 Pay-As-You-Go API Cost Impact

Calculated using standard frontier model pay-as-you-go pricing (Claude Sonnet: $3.00/1M uncached input, $0.30/1M cached input, $15.00/1M output; Gemini Pro: $1.25/1M uncached input, $0.125/1M cached input, $10.00/1M output):

| Cost Metric | Claude Sonnet 3.5 Without MCP | Claude Sonnet 3.5 With MCP | Sonnet Savings | Gemini Pro Without MCP | Gemini Pro With MCP v3 | Gemini Savings |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Measured Input Cost / Task** | $0.3526 | **$0.2760** | **-21.7%** | $0.4461 | **$0.2840** | **-36.3%** |
| **Measured Total Bill / Task** | $0.4405 | **$0.3693** | **-16.2%** | $0.6834 | **$0.5187** | **-24.1%** |
| **Cost per 1,000 Tasks (Measured)** | $440.53 | **$369.26** | **-$71.27** | $683.40 | **$518.66** | **-$164.74** |
| **Cache-Normalized Input Cost / Task** | $0.2997 | **$0.2663** | **-11.1%** | $0.2281 | **$0.1977** | **-13.3%** |

*Note: Cache-Normalized Input Cost assumes an ideal 100% prefix cache where every token in final context is billed once at full uncached price and all repeat turns are billed at cache-read price.*

### 2.3 Spring AI `CallAdvisor` Single-Turn RAG Impact

In applications where `SpringContextCompactorAdvisor` intercepts `ChatClient` prompts to compact embedded Java code blocks before single-turn LLM calls (e.g., PR review bots or code Q&A without multi-turn conversation caching):
* **Prompt Size Reduction**: Reduces Java source token footprint by **56.8% to 63.1%** (`33,477` chars to `14,475` chars across Petclinic target files).
* **Direct Input Cost Savings**: Drops single-turn Java input cost on Claude Sonnet from **$4.58 to $1.98 per 1,000 requests (-56.8%)**.

### 2.4 Deterministic AST Invariants (`trade-clearing-compactor-demo`)

Verified by `CompactorBenchmarkIntegrationTest` across 21 institutional clearing domain files:

| Metric | Raw Baseline | Compacted Result | Verified Invariant |
| :--- | :--- | :--- | :--- |
| **Spring Contract Retention** | 106 / 106 contracts | 106 / 106 contracts retained | **100.0% retention** of DI fields, `@Transactional` attributes, `@RabbitListener` queues, `@Query` JPQL strings, and JPA mappings |
| **Pre-Prune Proxy Trap Recall** | 3 / 4 traps lost after naive body strip | 4 / 4 traps pinned inline with line numbers | **100.0% trap recall** (`SELF_INVOCATION_BYPASS`, `READ_ONLY_CALLING_WRITE_REQUIRED`, `NON_PUBLIC_TRANSACTIONAL`) |
| **Demo Source Line Reduction** | 21 files / 1,462 lines | 21 skeletons / 417 lines | **-71.5% lines** (estimated -58.8% tokens in static file compaction) |

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
