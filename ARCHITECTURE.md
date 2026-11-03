# Architecture & Technical Design

**Target Stack**: **Java 21**, **Spring Boot 4.1.x**, **Spring AI 2.0.x**, **JavaParser 3.28.x**, **Spring AMQP (RabbitMQ 3.13)**, **PostgreSQL 16** (Testcontainers), **JUnit 5**, **Hibernate ORM 7.x**.

---

## 1. Overview & Design Goals

### 1.1 Problem

AI coding agents (Claude Code, Cursor, Antigravity, Codex) and Spring AI RAG/chat pipelines consume excessive context tokens when reading enterprise **Spring Boot** source files:

* **Java Boilerplate Bloat**: Standard JPA entities, Spring services, and REST controllers carry hundreds of lines of getters, setters, `equals`/`hashCode`, builder patterns, import walls, and private helper method bodies that inflate prompt token usage by 3x to 6x without adding architectural signal.
* **Spring-Blind Generic AST Skeletonizers**: Generic Tree-sitter skeletonizers strip method bodies indiscriminately or drop critical annotations, losing `@Transactional(propagation = Propagation.REQUIRES_NEW)` rules, `@Query` JPQL strings, `@Bean` wiring definitions, JPA `@JoinColumn` mappings, and intra-bean `@Transactional` self-invocation traps.
* **Full-File Reads for Single-Method Edits**: Coding agents routinely read an entire 350-line `@Service` or `@Entity` file just to inspect dependency types and edit a single 20-line method.

### 1.2 How It Works

This project provides a reusable Spring Boot starter, Spring AI `CallAdvisor`, and Model Context Protocol (MCP) server (**`spring-context-compactor-starter`**) and demonstrates it on an institutional FX and derivatives trade clearing application (**`trade-clearing-compactor-demo`**).

1. **Classify & Prune by Spring Stereotype**: `SpringAstCompactor` parses `.java` files with `JavaParser` (`3.28.x`), detects the Spring stereotype (`CONTROLLER`, `SERVICE`, `ENTITY`, `REPOSITORY`, `CONFIGURATION`, `MESSAGING_LISTENER`), strips non-contract boilerplate and method bodies, and preserves 100% of Spring wiring, JPA columns/relations, validation rules, and exact source line ranges (`/* L42-L88: body omitted (47 lines) */`).
2. **Annotate Static Spring Proxy Traps Inline**: During AST compaction of `@Service` and `@Component` classes, `SpringAstCompactor` scans method bodies before stripping them and emits inline warnings whenever a method invokes another `@Transactional`, `@Async`, or `@Cacheable` method on `this` (`/* [TRAP: SELF_INVOCATION_BYPASS] calls this.recordInternalComplianceCheck() at L93 */`).
3. **Expand Surgical Slices & Method Bodies**: `SpringSliceExpander` compacts an entire package or feature slice in one call and extracts the exact uncompacted lines of a single requested `className#methodName` so agents never read full files unnecessarily.
4. **Expose via Spring AI `CallAdvisor` & MCP JSON-RPC 2.0**:
   * `SpringContextCompactorAdvisor` implements Spring AI's `CallAdvisor` to automatically compact raw Java classes and fenced ` ```java ` code blocks inside outgoing `ChatClientRequest` prompts.
   * `McpCompactorDispatcher` exposes deterministic MCP tools (`compact_spring_file`, `compact_spring_slice`, `read_spring_method_body`, `analyze_compaction_budget`) over HTTP (`POST /internal/spring-compactor/mcp`) and standard I/O (`McpStdioServer`).

### 1.3 System Boundaries

* **Zero Boot Required for Source Compaction**: File and slice compaction operate directly on Java ASTs in under 5ms per file without requiring a running database or initialized `ApplicationContext`.
* **100% Contract Preservation**: Public/protected method signatures, constructor-injected fields, JPA `@Column`/`@JoinColumn` metadata, Bean Validation constraints, `@Query` strings, and `@Bean` return types are never stripped.

### 1.4 Accuracy & Performance Targets

* **65% to 85% Token Reduction on Enterprise Classes**: Strips boilerplate accessors, `equals`/`hashCode`, builders, and method bodies while retaining exact line numbers (`Lstart-Lend`) for surgical editing.
* **Deterministic Inline Trap Preservation**: Even when a method body is omitted from the compacted skeleton, any intra-class self-invocation of a `@Transactional` method inside that body is surfaced as an inline comment with the exact call-site line number.

---

## 2. Module Structure

```text
spring-ai-context-compactor/
|-- pom.xml                                 # Parent BOM (Java 21, Spring Boot 4.1.x, Spring AI 2.0.x, JavaParser 3.28.x)
|-- spring-context-compactor-starter/       # Reusable Spring Context Compactor, CallAdvisor & MCP Starter
|   |-- src/main/java/.../annotation/       # @EnableSpringContextCompactor
|   |-- src/main/java/.../config/           # SpringContextCompactorAutoConfiguration, CompactorProperties
|   |-- src/main/java/.../model/            # Immutable DTO records for CompactedFile, MethodSlice, TrapNote, BudgetReport
|   |-- src/main/java/.../compactor/        # SpringAstCompactor, StereotypeClassifier, SpringProxyTrapScanner
|   |-- src/main/java/.../service/          # SpringSliceExpander, CompactionBudgetService
|   |-- src/main/java/.../advisor/          # SpringContextCompactorAdvisor (Spring AI CallAdvisor)
|   \-- src/main/java/.../mcp/              # McpCompactorDispatcher, CompactorMcpHttpController, McpStdioServer
\-- trade-clearing-compactor-demo/          # Institutional FX & Derivatives Trade Clearing Demo App
    |-- src/main/java/.../margin/           # Conditional Margin Engines (SPAN vs ISDA SIMM)
    |-- src/main/java/.../ledger/           # Verbose JPA Entities, Repositories & Multi-Propagation Audit Services
    |-- src/main/java/.../trade/            # Trade Clearing Controller, Service & Self-Invocation Trap Scenario
    |-- src/main/java/.../messaging/        # RabbitMQ Exchanges, Queues, DLQ & @RabbitListener Consumers
    \-- src/test/java/.../                  # Testcontainers Integration Tests (PostgreSQL 16 + RabbitMQ 3.13)
```

---

## 3. Core Engine (`spring-context-compactor-starter`)

### 3.1 Spring-Semantic AST Compactor (`SpringAstCompactor`)

* **Stereotype-Driven Pruning Rules**:
  * `ENTITY` (`@Entity`, `@Table`, `@MappedSuperclass`, `@Embeddable`): Retains fields, `@Id`, `@GeneratedValue`, `@Column`, `@ManyToOne`, `@OneToMany`, `@JoinColumn`, `@Version`, and validation annotations. Strips getters, setters, `equals`, `hashCode`, `toString`, and builder classes.
  * `SERVICE` / `COMPONENT` (`@Service`, `@Component`): Retains class annotations, constructor-injected `final` fields, `@Value` fields, and public/protected method signatures with `@Transactional` metadata. Replaces method bodies with `/* Lstart-Lend: body omitted (N lines) */` while surfacing inline `SELF_INVOCATION_BYPASS` and `READ_ONLY_CALLING_WRITE_REQUIRED` trap notes extracted before pruning.
  * `CONTROLLER` (`@RestController`, `@Controller`): Retains `@RequestMapping`, `@GetMapping`/`@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@Valid`, `@PreAuthorize`, and response types; replaces handler bodies with line-range markers.
  * `REPOSITORY` (`@Repository`, `JpaRepository`): Retains full interface declarations, `@Query` JPQL/native SQL strings, `@Modifying`, and `@EntityGraph` definitions.
  * `CONFIGURATION` (`@Configuration`, `@ConfigurationProperties`): Retains `@Bean` method signatures, `@ConditionalOnProperty`, `@Profile`, `@Primary`, and RabbitMQ `Exchange`/`Queue`/`Binding` definitions.

### 3.2 Surgical Slice & Method Body Expander (`SpringSliceExpander`)

* **Multi-File Slice Compaction**: Scans a directory or package slice and returns a unified, token-budgeted skeleton bundle with dependency headers.
* **Exact Method Unfolding**: Given a file path and `methodName` (or line range), returns the exact uncompacted Java source lines with 1-based line numbers so an agent can apply surgical edits immediately.

### 3.3 Spring AI `CallAdvisor` (`SpringContextCompactorAdvisor`)

* **Prompt Code-Block Compression**: Intercepts outgoing `ChatClientRequest` user and system prompts, detects embedded Java source files or fenced ` ```java ` blocks containing Spring stereotypes, replaces them with their compacted skeletons, and records token savings in advisor context metadata.

### 3.4 MCP JSON-RPC 2.0 Server (`McpCompactorDispatcher`)

* **Protocol Compliance**: Handles JSON-RPC 2.0 `initialize`, `tools/list`, and `tools/call` over HTTP (`POST /internal/spring-compactor/mcp`) and stdio (`McpStdioServer`) with four tools: `compact_spring_file`, `compact_spring_slice`, `read_spring_method_body`, and `analyze_compaction_budget`.

---

## 4. Reference Domain (`trade-clearing-compactor-demo`)

### 4.1 Domain Model & Enterprise Verbosity

Institutional FX and derivatives clearing houses validate counterparty margin, novate bilateral trades into central clearing, and publish margin call events across verbose enterprise classes:

1. **Conditional Margin Engine (`com.builtbyjuls.clearing.margin`)**:
   * `MarginCalculator` interface with `StandardSpanMarginCalculator` (`span`, default) and `PortfolioSimmMarginCalculator` (`simm`).
2. **Verbose Clearing Entities & Ledger (`com.builtbyjuls.clearing.ledger`, `com.builtbyjuls.clearing.trade`)**:
   * Full enterprise JPA entities (`ClearingMember`, `ClearedTrade`, `MarginCallRecord`, `ClearingAuditLog`) with explicit getters/setters, builders, `equals`/`hashCode`, and audit columns, orchestrated by `TradeClearingController`, `TradeClearingService`, and `ClearingAuditService`.
3. **RabbitMQ Clearing Topology (`com.builtbyjuls.clearing.messaging`)**:
   * Topic exchange `clearing.trade.topic`, direct exchange `clearing.margin.direct`, dead-letter exchange `clearing.dlx`, and `TradeExecutionRabbitListener`.

---

## 5. Key Engineering Trade-offs

| Decision Area | Chosen Approach | Rejected Alternative | Why We Chose It & Trade-off |
| --- | --- | --- | --- |
| **AST Parsing Engine** | **JavaParser (`com.github.javaparser:javaparser-core`)** | Regex / text line stripping or live Spring context boot | **Why**: Parses uncompiled `.java` files in milliseconds with exact line/column ranges and full Java 21 record/annotation AST fidelity without booting a database.<br><br>**Trade-off**: Analyzes syntactic AST and local type declarations rather than external third-party bytecode jars. |
| **Method Body Handling** | **Skeleton + Exact Line Range (`Lstart-Lend`) + On-Demand `read_spring_method_body`** | Dropping line numbers or returning abstract JSON metadata | **Why**: Coding agents need valid Java signatures and exact line numbers to edit files directly without a second full-file `read_file` call.<br><br>**Trade-off**: Requires a second targeted call (`read_spring_method_body`) only for the specific method being modified. |
| **Dual Delivery Surface** | **Spring AI `CallAdvisor` + Built-in MCP JSON-RPC 2.0 Server** | Standalone CLI-only script | **Why**: Works both inside Spring AI 2.0 LLM applications (automatic prompt compression) and as an external MCP server for coding agents.<br><br>**Trade-off**: Maintains two lightweight adapters over the same `SpringAstCompactor` core. |
