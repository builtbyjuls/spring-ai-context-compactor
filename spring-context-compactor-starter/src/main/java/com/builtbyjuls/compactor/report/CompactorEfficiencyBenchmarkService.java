package com.builtbyjuls.compactor.report;

import com.builtbyjuls.compactor.advisor.SpringContextCompactorAdvisor;
import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.mcp.McpCompactorDispatcher;
import com.builtbyjuls.compactor.mcp.McpJsonRpcRequest;
import com.builtbyjuls.compactor.model.AdvisorCompactionStatsDto;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.CompactedMethodSummaryDto;
import com.builtbyjuls.compactor.model.MethodBodySliceDto;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.builtbyjuls.compactor.model.SpringStereotype;
import com.builtbyjuls.compactor.service.CompactionBudgetService;
import com.builtbyjuls.compactor.service.SpringSliceExpander;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CompactorEfficiencyBenchmarkService {

    private static final Set<String> ENTITY_CONTRACT_ANNOTATIONS = Set.of(
            "Id", "Version", "Column", "Enumerated", "JoinColumn", "ManyToOne", "OneToMany"
    );

    private static final Set<String> METHOD_CONTRACT_ANNOTATIONS = Set.of(
            "PostMapping", "GetMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping",
            "Transactional", "RabbitListener", "KafkaListener", "JmsListener", "Query", "Bean"
    );

    private final SpringAstCompactor astCompactor;
    private final SpringSliceExpander sliceExpander;
    private final CompactionBudgetService budgetService;
    private final SpringContextCompactorAdvisor advisor;
    private final McpCompactorDispatcher mcpDispatcher;
    private final JavaParser javaParser;

    public CompactorEfficiencyBenchmarkService(
            SpringAstCompactor astCompactor,
            SpringSliceExpander sliceExpander,
            CompactionBudgetService budgetService,
            SpringContextCompactorAdvisor advisor,
            McpCompactorDispatcher mcpDispatcher
    ) {
        this.astCompactor = Objects.requireNonNull(astCompactor, "astCompactor must not be null");
        this.sliceExpander = Objects.requireNonNull(sliceExpander, "sliceExpander must not be null");
        this.budgetService = Objects.requireNonNull(budgetService, "budgetService must not be null");
        this.advisor = Objects.requireNonNull(advisor, "advisor must not be null");
        this.mcpDispatcher = Objects.requireNonNull(mcpDispatcher, "mcpDispatcher must not be null");
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    public CompactorBenchmarkReportDto runBenchmark(Path sourceRoot) {
        String sourceRootStr = sourceRoot != null ? sourceRoot.toString().replace('\\', '/') : "";
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            return new CompactorBenchmarkReportDto(
                    sourceRootStr,
                    0, 0, 0, 0L, 0L, 0, 0, 0.0,
                    0, 0, 0.0, 0.0,
                    0, 0, 100.0,
                    0, 0, 0, 100.0,
                    0, 0, 0.0, 0,
                    List.of()
            );
        }

        ProjectCompactionBudgetDto budget = budgetService.analyzeProjectBudget(sourceRoot);
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        List<Path> javaFiles = walkJavaFiles(normalizedRoot);

        List<ScannedFileEntry> entries = new ArrayList<>(javaFiles.size());
        long rawSourceTotalBytes = 0L;
        long compactedTotalBytes = 0L;

        for (Path file : javaFiles) {
            try {
                String rawSource = Files.readString(file, StandardCharsets.UTF_8);
                rawSourceTotalBytes += rawSource.getBytes(StandardCharsets.UTF_8).length;
                CompactedFileResultDto compacted = astCompactor.compactFile(sourceRoot, file);
                compactedTotalBytes += compacted.compactedSkeleton().getBytes(StandardCharsets.UTF_8).length;
                entries.add(new ScannedFileEntry(file, rawSource, compacted));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read benchmark file: " + file, e);
            }
        }

        int scannedJavaFiles = budget.totalFiles();
        int rawSourceTotalLines = budget.originalTotalLines();
        int compactedTotalLines = budget.compactedTotalLines();
        int rawSourceEstimatedTokens = budget.originalEstimatedTokens();
        int compactedEstimatedTokens = budget.compactedEstimatedTokens();
        double repositoryTokenReductionPercent = budget.overallTokenReductionPercent();

        BenchmarkMetricRowDto metric1 = new BenchmarkMetricRowDto(
                "repositoryTokenConsumption",
                "Repository Context Compaction (Full Source Scan vs AST Skeleton)",
                String.format(Locale.ROOT, "%d files / %d lines / ~%,d tokens",
                        scannedJavaFiles, rawSourceTotalLines, rawSourceEstimatedTokens),
                String.format(Locale.ROOT, "%d skeletons / %d lines / ~%,d tokens",
                        scannedJavaFiles, compactedTotalLines, compactedEstimatedTokens),
                String.format(Locale.ROOT, "-%.1f%% tokens (-%,d tokens)",
                        repositoryTokenReductionPercent, budget.tokensSaved()),
                "Compacts all Spring stereotypes (@RestController, @Service, @Repository, @Entity, @Configuration) into compilable-style Java skeletons"
        );

        // Metric 2: Surgical Method Edit Workflow
        ScannedFileEntry serviceEntry = findLargestByStereotype(entries, SpringStereotype.SERVICE, 0);
        ScannedFileEntry entityEntry = findLargestByStereotype(entries, SpringStereotype.ENTITY, 1);

        int rawSurgicalEditTokens = 0;
        int compactedSurgicalEditTokens = 0;
        double surgicalEditTokenReductionPercent = 0.0;
        double mcpSurgicalWorkflowLatencyMs = 0.0;

        if (serviceEntry != null && entityEntry != null) {
            rawSurgicalEditTokens = serviceEntry.compacted().originalEstimatedTokens()
                    + entityEntry.compacted().originalEstimatedTokens();

            String serviceClassName = serviceEntry.compacted().className();
            String targetMethodName = serviceEntry.compacted().methods().isEmpty()
                    ? "toString"
                    : serviceEntry.compacted().methods().get(0).methodName();

            McpJsonRpcRequest compactReq = new McpJsonRpcRequest(
                    "2.0",
                    1,
                    "tools/call",
                    Map.of(
                            "name", "compact_spring_file",
                            "arguments", Map.of("query", serviceClassName, "sourceRoot", sourceRoot.toString())
                    )
            );
            McpJsonRpcRequest expandReq = new McpJsonRpcRequest(
                    "2.0",
                    2,
                    "tools/call",
                    Map.of(
                            "name", "read_spring_method_body",
                            "arguments", Map.of(
                                    "classQuery", serviceClassName,
                                    "methodName", targetMethodName,
                                    "sourceRoot", sourceRoot.toString()
                            )
                    )
            );

            for (int i = 0; i < 3; i++) {
                mcpDispatcher.dispatch(compactReq);
                mcpDispatcher.dispatch(expandReq);
            }

            long startNanos = System.nanoTime();
            for (int i = 0; i < 10; i++) {
                mcpDispatcher.dispatch(compactReq);
                mcpDispatcher.dispatch(expandReq);
            }
            long elapsedNanos = System.nanoTime() - startNanos;
            mcpSurgicalWorkflowLatencyMs = Math.round(((elapsedNanos / 10.0) / 1_000_000.0) * 100.0) / 100.0;

            List<MethodBodySliceDto> slices = sliceExpander.expandMethodBody(sourceRoot, serviceClassName, targetMethodName);
            int expandedMethodTokens = slices.isEmpty() ? 0 : slices.get(0).estimatedTokens();

            compactedSurgicalEditTokens = serviceEntry.compacted().compactedEstimatedTokens() + expandedMethodTokens;
            surgicalEditTokenReductionPercent = rawSurgicalEditTokens > 0
                    ? Math.max(0.0, ((double) (rawSurgicalEditTokens - compactedSurgicalEditTokens) / rawSurgicalEditTokens) * 100.0)
                    : 0.0;
        }

        BenchmarkMetricRowDto metric2 = new BenchmarkMetricRowDto(
                "surgicalEditWorkflow",
                "Surgical Method Edit Workflow (Read Full Files vs Skeleton + Expand Method)",
                String.format(Locale.ROOT, "2 full files (~%,d tokens)", rawSurgicalEditTokens),
                String.format(Locale.ROOT, "1 skeleton + 1 method (~%,d tokens in %.2f ms)",
                        compactedSurgicalEditTokens, mcpSurgicalWorkflowLatencyMs),
                String.format(Locale.ROOT, "-%.1f%% tokens (%.2f ms MCP latency)",
                        surgicalEditTokenReductionPercent, mcpSurgicalWorkflowLatencyMs),
                "Agent inspects class skeleton with Lstart-Lend markers and unfolds only the target method body on demand"
        );

        // Metric 3: Spring Semantic Contract & Wiring Retention
        ContractRetentionCount contractCount = evaluateSpringContractRetention(entries);
        int totalSpringContractsEvaluated = contractCount.totalEvaluated();
        int retainedSpringContracts = contractCount.retained();
        double springContractRetentionPercent = totalSpringContractsEvaluated > 0
                ? (retainedSpringContracts * 100.0) / totalSpringContractsEvaluated
                : 100.0;

        BenchmarkMetricRowDto metric3 = new BenchmarkMetricRowDto(
                "springContractRetention",
                "Spring Semantic Contract & Wiring Retention",
                String.format(Locale.ROOT, "%d / %d Spring wiring & JPA/AMQP contracts",
                        totalSpringContractsEvaluated, totalSpringContractsEvaluated),
                String.format(Locale.ROOT, "%d / %d contracts retained in skeletons",
                        retainedSpringContracts, totalSpringContractsEvaluated),
                String.format(Locale.ROOT, "%.1f%% contract retention", springContractRetentionPercent),
                "Retains DI fields, @Transactional attributes, @RabbitListener queues, @Query JPQL strings, and @Entity @Column mappings"
        );

        // Metric 4: Pre-Prune @Transactional Proxy Trap Recall
        int totalSeededProxyTraps = budget.detectedTraps().size();
        int inlineRetainedProxyTraps = 0;
        int naiveSkeletonDetectedTraps = 0;

        for (ScannedFileEntry entry : entries) {
            String skeleton = entry.compacted().compactedSkeleton();
            for (MethodTrapNoteDto trap : entry.compacted().trapNotes()) {
                if (skeleton.contains("[TRAP: " + trap.trapType().name() + "]")
                        && skeleton.contains(trap.summary())) {
                    inlineRetainedProxyTraps++;
                }
                if (trap.trapType() == ProxyTrapType.NON_PUBLIC_TRANSACTIONAL) {
                    naiveSkeletonDetectedTraps++;
                }
            }
        }

        double inlineProxyTrapRecallPercent = totalSeededProxyTraps > 0
                ? (inlineRetainedProxyTraps * 100.0) / totalSeededProxyTraps
                : 100.0;

        BenchmarkMetricRowDto metric4 = new BenchmarkMetricRowDto(
                "inlineProxyTrapDetection",
                "Pre-Prune @Transactional Proxy Trap Recall",
                String.format(Locale.ROOT, "%d / %d internal call-site traps lost after naive body strip",
                        Math.max(0, totalSeededProxyTraps - naiveSkeletonDetectedTraps), totalSeededProxyTraps),
                String.format(Locale.ROOT, "%d / %d traps pinned inline with L-numbers",
                        inlineRetainedProxyTraps, totalSeededProxyTraps),
                String.format(Locale.ROOT, "%.1f%% trap recall (pre-prune AST scan)", inlineProxyTrapRecallPercent),
                "Scans AST before body stripping to pin SELF_INVOCATION_BYPASS, READ_ONLY_CALLING_WRITE_REQUIRED, and NON_PUBLIC_TRANSACTIONAL inline"
        );

        // Metric 5: Spring AI CallAdvisor Prompt Compression
        int advisorPromptOriginalTokens = 0;
        int advisorPromptCompactedTokens = 0;
        double advisorPromptTokenReductionPercent = 0.0;
        int projectedSavedTokensPer1000Prompts = 0;

        if (serviceEntry != null && entityEntry != null) {
            String promptText = "Review the transaction boundaries, JPA column mappings, and margin calculation workflow in the following Spring components:\n\n"
                    + "```java\n" + entityEntry.rawSource() + "\n```\n\n"
                    + "```java\n" + serviceEntry.rawSource() + "\n```\n\n"
                    + "Identify any @Transactional self-invocation or read-only proxy bypass risks and verify margin call persistence.";

            advisor.compactPromptText(promptText);
            AdvisorCompactionStatsDto advisorStats = advisor.inspectCompactionStats(promptText);
            advisorPromptOriginalTokens = advisorStats.originalTokens();
            advisorPromptCompactedTokens = advisorStats.compactedTokens();
            advisorPromptTokenReductionPercent = advisorStats.tokenReductionPercent();
            projectedSavedTokensPer1000Prompts = advisorStats.tokensSaved() * 1000;
        }

        BenchmarkMetricRowDto metric5 = new BenchmarkMetricRowDto(
                "springAiAdvisorSavings",
                "Spring AI CallAdvisor Prompt Compression",
                String.format(Locale.ROOT, "~%,d tokens / request (%,d tokens / 1k calls)",
                        advisorPromptOriginalTokens, advisorPromptOriginalTokens * 1000),
                String.format(Locale.ROOT, "~%,d tokens / request (%,d tokens / 1k calls)",
                        advisorPromptCompactedTokens, advisorPromptCompactedTokens * 1000),
                String.format(Locale.ROOT, "-%.1f%% prompt tokens (-%,d tokens / 1k calls)",
                        advisorPromptTokenReductionPercent, projectedSavedTokensPer1000Prompts),
                "Zero-touch Spring AI 2.0 CallAdvisor compacts fenced Java blocks in UserMessage and SystemMessage before ChatModel call"
        );

        List<BenchmarkMetricRowDto> metrics = List.of(metric1, metric2, metric3, metric4, metric5);

        return new CompactorBenchmarkReportDto(
                sourceRootStr,
                scannedJavaFiles,
                rawSourceTotalLines,
                compactedTotalLines,
                rawSourceTotalBytes,
                compactedTotalBytes,
                rawSourceEstimatedTokens,
                compactedEstimatedTokens,
                repositoryTokenReductionPercent,
                rawSurgicalEditTokens,
                compactedSurgicalEditTokens,
                surgicalEditTokenReductionPercent,
                mcpSurgicalWorkflowLatencyMs,
                totalSpringContractsEvaluated,
                retainedSpringContracts,
                springContractRetentionPercent,
                totalSeededProxyTraps,
                inlineRetainedProxyTraps,
                naiveSkeletonDetectedTraps,
                inlineProxyTrapRecallPercent,
                advisorPromptOriginalTokens,
                advisorPromptCompactedTokens,
                advisorPromptTokenReductionPercent,
                projectedSavedTokensPer1000Prompts,
                metrics
        );
    }

    public Map<MethodTrapNoteDto, String> resolveTrapClassNames(Path sourceRoot) {
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            return Map.of();
        }
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        List<Path> javaFiles = walkJavaFiles(normalizedRoot);
        Map<MethodTrapNoteDto, String> trapToClass = new LinkedHashMap<>();
        for (Path file : javaFiles) {
            CompactedFileResultDto compacted = astCompactor.compactFile(sourceRoot, file);
            for (MethodTrapNoteDto trap : compacted.trapNotes()) {
                trapToClass.put(trap, compacted.className());
            }
        }
        return trapToClass;
    }

    private ScannedFileEntry findLargestByStereotype(
            List<ScannedFileEntry> entries,
            SpringStereotype stereotype,
            int fallbackIndex
    ) {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.stream()
                .filter(e -> e.compacted().stereotype() == stereotype)
                .max(Comparator.comparingInt(e -> e.compacted().originalEstimatedTokens()))
                .orElseGet(() -> {
                    List<ScannedFileEntry> sorted = entries.stream()
                            .sorted(Comparator.comparingInt((ScannedFileEntry e) -> e.compacted().originalEstimatedTokens()).reversed())
                            .toList();
                    int idx = Math.min(fallbackIndex, sorted.size() - 1);
                    return sorted.get(idx);
                });
    }

    private ContractRetentionCount evaluateSpringContractRetention(List<ScannedFileEntry> entries) {
        int totalEvaluated = 0;
        int retained = 0;

        for (ScannedFileEntry entry : entries) {
            CompactedFileResultDto compacted = entry.compacted();
            String skeleton = compacted.compactedSkeleton();

            // 1. Injected dependencies
            for (String dep : compacted.injectedDependencies()) {
                totalEvaluated++;
                if (skeleton.contains(dep)) {
                    retained++;
                }
            }

            // 2. Retained methods and their Spring contract annotations
            for (CompactedMethodSummaryDto method : compacted.methods()) {
                totalEvaluated++;
                if (skeleton.contains(method.methodName() + "(")) {
                    retained++;
                }
                for (String ann : method.retainedAnnotations()) {
                    String simpleName = extractAnnotationSimpleName(ann);
                    if (METHOD_CONTRACT_ANNOTATIONS.contains(simpleName)) {
                        totalEvaluated++;
                        if (skeleton.contains(ann)) {
                            retained++;
                        }
                    }
                }
            }

            // 3. Entity JPA field mapping contracts
            if (compacted.stereotype() == SpringStereotype.ENTITY) {
                ParseResult<CompilationUnit> parseResult = javaParser.parse(entry.rawSource());
                if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                    CompilationUnit cu = parseResult.getResult().get();
                    TypeDeclaration<?> primaryType = cu.getPrimaryType().orElseGet(() ->
                            cu.getTypes().isEmpty() ? null : cu.getTypes().get(0)
                    );
                    if (primaryType != null) {
                        for (FieldDeclaration field : primaryType.getFields()) {
                            if (field.isStatic()) {
                                continue;
                            }
                            for (AnnotationExpr ann : field.getAnnotations()) {
                                String simpleName = extractSimpleName(ann.getNameAsString());
                                if (ENTITY_CONTRACT_ANNOTATIONS.contains(simpleName)) {
                                    totalEvaluated++;
                                    if (skeleton.contains(ann.toString())) {
                                        retained++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return new ContractRetentionCount(totalEvaluated, retained);
    }

    private String extractAnnotationSimpleName(String annotationStr) {
        String trimmed = annotationStr.startsWith("@") ? annotationStr.substring(1) : annotationStr;
        int parenIdx = trimmed.indexOf('(');
        String namePart = parenIdx >= 0 ? trimmed.substring(0, parenIdx).trim() : trimmed.trim();
        return extractSimpleName(namePart);
    }

    private String extractSimpleName(String qualifiedName) {
        int dotIdx = qualifiedName.lastIndexOf('.');
        return dotIdx >= 0 ? qualifiedName.substring(dotIdx + 1) : qualifiedName;
    }

    private List<Path> walkJavaFiles(Path normalizedRoot) {
        try (var stream = Files.walk(normalizedRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                    .filter(p -> !p.getFileName().toString().equals("module-info.java"))
                    .sorted((p1, p2) -> {
                        String r1 = normalizedRoot.relativize(p1).toString().replace('\\', '/');
                        String r2 = normalizedRoot.relativize(p2).toString().replace('\\', '/');
                        return r1.compareTo(r2);
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk sourceRoot: " + normalizedRoot, e);
        }
    }

    private record ScannedFileEntry(Path file, String rawSource, CompactedFileResultDto compacted) {}

    private record ContractRetentionCount(int totalEvaluated, int retained) {}
}
