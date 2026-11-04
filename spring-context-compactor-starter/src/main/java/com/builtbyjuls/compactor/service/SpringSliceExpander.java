package com.builtbyjuls.compactor.service;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.compactor.SpringProxyTrapScanner;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.MethodBodySliceDto;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.SliceCompactionReportDto;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpringSliceExpander {

    private static final Map<String, Pattern> GLOB_CACHE = new ConcurrentHashMap<>();

    private final SpringAstCompactor astCompactor;
    private final SpringProxyTrapScanner trapScanner;
    private final CompactorProperties properties;
    private final JavaParser javaParser;

    public SpringSliceExpander(
            SpringAstCompactor astCompactor,
            SpringProxyTrapScanner trapScanner,
            CompactorProperties properties
    ) {
        this.astCompactor = Objects.requireNonNull(astCompactor, "astCompactor must not be null");
        this.trapScanner = Objects.requireNonNull(trapScanner, "trapScanner must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.javaParser = new JavaParser(config);
    }

    public SliceCompactionReportDto compactSlice(Path sourceRoot, String sliceQuery) {
        return compactSlice(sourceRoot, sliceQuery, 0);
    }

    public SliceCompactionReportDto compactSlice(Path sourceRoot, String sliceQuery, int maxTokenBudget) {
        String query = sliceQuery != null ? sliceQuery : "";
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            return new SliceCompactionReportDto(
                    query,
                    0, 0, 0, 0, 0, 0.0, false, List.of(), ""
            );
        }

        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        List<Path> matchedFiles = findSliceFiles(normalizedRoot, query);
        Path activeRoot = normalizedRoot;

        if (matchedFiles.isEmpty() && !query.isBlank()) {
            Optional<Path> siblingTestRoot = resolveSiblingTestRoot(normalizedRoot);
            if (siblingTestRoot.isPresent()) {
                List<Path> siblingFiles = findSliceFiles(siblingTestRoot.get(), query);
                if (!siblingFiles.isEmpty()) {
                    matchedFiles = siblingFiles;
                    activeRoot = siblingTestRoot.get();
                }
            }
        }

        List<CompactedFileResultDto> files = new ArrayList<>();
        boolean tokenBudgetExceeded = false;
        int currentCompactedTokens = 0;

        for (Path file : matchedFiles) {
            CompactedFileResultDto compacted = astCompactor.compactFile(activeRoot, file);
            int fileTokens = compacted.compactedEstimatedTokens();

            if (maxTokenBudget > 0) {
                if (files.isEmpty()) {
                    files.add(compacted);
                    currentCompactedTokens += fileTokens;
                    if (currentCompactedTokens > maxTokenBudget) {
                        tokenBudgetExceeded = true;
                    }
                } else if (currentCompactedTokens + fileTokens <= maxTokenBudget) {
                    files.add(compacted);
                    currentCompactedTokens += fileTokens;
                } else {
                    tokenBudgetExceeded = true;
                    break;
                }
            } else {
                files.add(compacted);
                currentCompactedTokens += fileTokens;
            }
        }

        int totalFilesCompacted = files.size();
        int originalTotalLines = files.stream().mapToInt(CompactedFileResultDto::originalLines).sum();
        int compactedTotalLines = files.stream().mapToInt(CompactedFileResultDto::compactedLines).sum();
        int originalEstimatedTokens = files.stream().mapToInt(CompactedFileResultDto::originalEstimatedTokens).sum();
        int compactedEstimatedTokens = files.stream().mapToInt(CompactedFileResultDto::compactedEstimatedTokens).sum();
        double overallTokenReductionPercent = originalEstimatedTokens > 0
                ? Math.max(0.0, ((double) (originalEstimatedTokens - compactedEstimatedTokens) / originalEstimatedTokens) * 100.0)
                : 0.0;

        String combinedSkeleton = files.stream()
                .map(CompactedFileResultDto::compactedSkeleton)
                .collect(Collectors.joining("\n"));

        return new SliceCompactionReportDto(
                query,
                totalFilesCompacted,
                originalTotalLines,
                compactedTotalLines,
                originalEstimatedTokens,
                compactedEstimatedTokens,
                overallTokenReductionPercent,
                tokenBudgetExceeded,
                files,
                combinedSkeleton
        );
    }

    public List<MethodBodySliceDto> expandMethodBodies(Path sourceRoot, String fileOrClassQuery, Collection<String> methodNames) {
        if (methodNames == null || methodNames.isEmpty()) {
            return List.of();
        }
        String joined = methodNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return List.of();
        }
        return expandMethodBody(sourceRoot, fileOrClassQuery, joined);
    }

    public List<MethodBodySliceDto> expandMethodBodiesWithCallees(
            Path sourceRoot,
            String fileOrClassQuery,
            Collection<String> seedMethodNames
    ) {
        if (sourceRoot == null || !Files.exists(sourceRoot)
                || fileOrClassQuery == null || fileOrClassQuery.isBlank()
                || seedMethodNames == null || seedMethodNames.isEmpty()) {
            return List.of();
        }

        Set<String> seedPatterns = seedMethodNames.stream()
                .filter(Objects::nonNull)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (seedPatterns.isEmpty()) {
            return List.of();
        }

        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        List<Path> matchingFiles = locateMatchingFiles(normalizedRoot, fileOrClassQuery.trim());
        if (matchingFiles.isEmpty()) {
            return List.of();
        }

        List<MethodBodySliceDto> results = new ArrayList<>();

        for (Path file : matchingFiles) {
            List<String> originalLines;
            String javaSource;
            try {
                originalLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                javaSource = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read file: " + file, e);
            }

            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaSource);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                continue;
            }

            CompilationUnit cu = parseResult.getResult().get();
            TypeDeclaration<?> primaryType = cu.getPrimaryType().orElseGet(() ->
                    cu.getTypes().isEmpty() ? null : cu.getTypes().get(0)
            );
            if (primaryType == null) {
                continue;
            }

            String className = primaryType.getNameAsString();
            List<MethodTrapNoteDto> allTraps = List.of();
            if (properties.isDetectProxyTraps() && primaryType instanceof ClassOrInterfaceDeclaration cid) {
                allTraps = trapScanner.scan(cid);
            }

            Set<MethodDeclaration> visited = new LinkedHashSet<>();
            Queue<MethodDeclaration> queue = new ArrayDeque<>();

            for (MethodDeclaration method : primaryType.getMethods()) {
                if (matchesAnyMethodPattern(method.getNameAsString(), seedPatterns)) {
                    if (visited.add(method)) {
                        queue.add(method);
                    }
                }
            }

            while (!queue.isEmpty()) {
                MethodDeclaration current = queue.poll();
                if (current.getBody().isEmpty()) {
                    continue;
                }
                List<MethodCallExpr> calls = current.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                    boolean isIntraClass = call.getScope().isEmpty()
                            || call.getScope().get().isThisExpr();
                    if (!isIntraClass) {
                        continue;
                    }
                    String calleeName = call.getNameAsString();
                    for (MethodDeclaration m : primaryType.getMethods()) {
                        if (m.getNameAsString().equals(calleeName) && visited.add(m)) {
                            queue.add(m);
                        }
                    }
                }
            }

            Path effectiveRoot = file.startsWith(normalizedRoot)
                    ? normalizedRoot
                    : resolveSiblingTestRoot(normalizedRoot).filter(file::startsWith).orElse(normalizedRoot);

            for (MethodDeclaration method : primaryType.getMethods()) {
                if (visited.contains(method)) {
                    results.add(buildMethodSlice(method, originalLines, className, file, effectiveRoot, allTraps));
                }
            }
        }

        return Collections.unmodifiableList(results);
    }

    public List<MethodBodySliceDto> expandMethodBody(Path sourceRoot, String fileOrClassQuery, String methodName) {
        if (sourceRoot == null || !Files.exists(sourceRoot)
                || fileOrClassQuery == null || fileOrClassQuery.isBlank()
                || methodName == null || methodName.isBlank()) {
            return List.of();
        }

        Set<String> targetMethods = Arrays.stream(methodName.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (targetMethods.isEmpty()) {
            return List.of();
        }

        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        List<Path> matchingFiles = locateMatchingFiles(normalizedRoot, fileOrClassQuery.trim());
        if (matchingFiles.isEmpty()) {
            return List.of();
        }

        List<MethodBodySliceDto> results = new ArrayList<>();

        for (Path file : matchingFiles) {
            List<String> originalLines;
            String javaSource;
            try {
                originalLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                javaSource = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read file: " + file, e);
            }

            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaSource);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                continue;
            }

            CompilationUnit cu = parseResult.getResult().get();
            TypeDeclaration<?> primaryType = cu.getPrimaryType().orElseGet(() ->
                    cu.getTypes().isEmpty() ? null : cu.getTypes().get(0)
            );
            if (primaryType == null) {
                continue;
            }

            String className = primaryType.getNameAsString();
            List<MethodTrapNoteDto> allTraps = List.of();
            if (properties.isDetectProxyTraps() && primaryType instanceof ClassOrInterfaceDeclaration cid) {
                allTraps = trapScanner.scan(cid);
            }

            Path effectiveRoot = file.startsWith(normalizedRoot)
                    ? normalizedRoot
                    : resolveSiblingTestRoot(normalizedRoot).filter(file::startsWith).orElse(normalizedRoot);

            for (MethodDeclaration method : primaryType.getMethods()) {
                if (matchesAnyMethodPattern(method.getNameAsString(), targetMethods)) {
                    results.add(buildMethodSlice(method, originalLines, className, file, effectiveRoot, allTraps));
                }
            }
        }

        return Collections.unmodifiableList(results);
    }

    private MethodBodySliceDto buildMethodSlice(
            MethodDeclaration method,
            List<String> originalLines,
            String className,
            Path file,
            Path effectiveRoot,
            List<MethodTrapNoteDto> allTraps
    ) {
        int startLine = method.getBegin().map(p -> p.line).orElse(1);
        for (AnnotationExpr ann : method.getAnnotations()) {
            if (ann.getBegin().isPresent()) {
                startLine = Math.min(startLine, ann.getBegin().get().line);
            }
        }
        int endLine = method.getEnd().map(p -> p.line).orElse(startLine);

        if (!originalLines.isEmpty()) {
            startLine = Math.max(1, Math.min(startLine, originalLines.size()));
            endLine = Math.max(startLine, Math.min(endLine, originalLines.size()));
        }

        List<String> formatted = new ArrayList<>();
        for (int lineNum = startLine; lineNum <= endLine && lineNum <= originalLines.size(); lineNum++) {
            formatted.add(String.format(Locale.ROOT, "%4d: %s", lineNum, originalLines.get(lineNum - 1)));
        }
        String numberedSourceLines = String.join("\n", formatted);
        int lineCount = Math.max(1, endLine - startLine + 1);
        int estimatedTokens = (int) Math.ceil(numberedSourceLines.length() / 4.0);

        String signature = SpringAstCompactor.buildMethodSignature(method);
        List<MethodTrapNoteDto> methodTraps = SpringAstCompactor.findTrapsForMethod(method, allTraps);
        String relPath = effectiveRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');

        return new MethodBodySliceDto(
                relPath,
                className,
                method.getNameAsString(),
                signature,
                startLine,
                endLine,
                lineCount,
                estimatedTokens,
                numberedSourceLines,
                methodTraps
        );
    }

    private boolean matchesAnyMethodPattern(String actualMethodName, Set<String> patterns) {
        if (patterns == null || patterns.isEmpty() || actualMethodName == null) {
            return false;
        }
        if (patterns.contains("*") || patterns.contains(actualMethodName)) {
            return true;
        }
        for (String pattern : patterns) {
            if (pattern.contains("*") && globMatches(pattern, actualMethodName)) {
                return true;
            }
        }
        return false;
    }

    private boolean globMatches(String glob, String text) {
        Pattern pattern = GLOB_CACHE.computeIfAbsent(glob, g -> {
            StringBuilder regex = new StringBuilder("^");
            String[] parts = g.split("\\*", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    regex.append(".*");
                }
                if (!parts[i].isEmpty()) {
                    regex.append(Pattern.quote(parts[i]));
                }
            }
            regex.append("$");
            return Pattern.compile(regex.toString());
        });
        return pattern.matcher(text).matches();
    }

    private List<Path> findSliceFiles(Path normalizedRoot, String sliceQuery) {
        List<Path> allJavaFiles = walkJavaFiles(normalizedRoot);
        String trimmed = sliceQuery.trim();
        if (trimmed.isEmpty()) {
            return allJavaFiles;
        }

        String querySlash = trimmed.replace('.', '/').toLowerCase(Locale.ROOT);
        List<Path> matched = new ArrayList<>();

        for (Path file : allJavaFiles) {
            String relPath = normalizedRoot.relativize(file).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            String simpleNameWithoutExt = fileName.endsWith(".java")
                    ? fileName.substring(0, fileName.length() - 5)
                    : fileName;

            if (relPath.contains(querySlash) || simpleNameWithoutExt.contains(querySlash)) {
                matched.add(file);
            }
        }

        return matched;
    }

    private List<Path> locateMatchingFiles(Path normalizedRoot, String query) {
        List<Path> allJavaFiles = walkJavaFiles(normalizedRoot);
        String querySlash = query.replace('\\', '/');
        String querySlashLower = querySlash.toLowerCase(Locale.ROOT);
        String querySlashNoExt = querySlashLower.endsWith(".java")
                ? querySlashLower.substring(0, querySlashLower.length() - 5)
                : querySlashLower;
        String queryDot = query.replace('/', '.');
        String queryDotLower = queryDot.toLowerCase(Locale.ROOT);
        String queryDotNoExt = queryDotLower.endsWith(".java")
                ? queryDotLower.substring(0, queryDotLower.length() - 5)
                : queryDotLower;

        List<Path> exactMatches = new ArrayList<>();
        for (Path file : allJavaFiles) {
            String relPath = normalizedRoot.relativize(file).toString().replace('\\', '/');
            String relPathLower = relPath.toLowerCase(Locale.ROOT);
            String relPathNoExt = relPathLower.endsWith(".java")
                    ? relPathLower.substring(0, relPathLower.length() - 5)
                    : relPathLower;
            String relPathDot = relPathNoExt.replace('/', '.');

            String fileName = file.getFileName().toString();
            String fileNameLower = fileName.toLowerCase(Locale.ROOT);
            String simpleNameNoExt = fileNameLower.endsWith(".java")
                    ? fileNameLower.substring(0, fileNameLower.length() - 5)
                    : fileNameLower;

            if (relPathLower.equals(querySlashLower)
                    || relPathNoExt.equals(querySlashNoExt)
                    || relPathDot.equals(queryDotNoExt)
                    || fileNameLower.equals(querySlashLower)
                    || simpleNameNoExt.equals(querySlashNoExt)) {
                exactMatches.add(file);
            }
        }

        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        List<Path> fallbackMatches = new ArrayList<>();
        for (Path file : allJavaFiles) {
            String relPath = normalizedRoot.relativize(file).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);

            if (relPath.endsWith(querySlashLower)
                    || relPath.endsWith(querySlashLower + ".java")
                    || relPath.contains(querySlashLower)
                    || fileName.contains(querySlashLower)) {
                fallbackMatches.add(file);
            }
        }

        if (!fallbackMatches.isEmpty()) {
            return fallbackMatches;
        }

        Optional<Path> siblingTestRoot = resolveSiblingTestRoot(normalizedRoot);
        if (siblingTestRoot.isPresent()) {
            return locateMatchingFiles(siblingTestRoot.get(), query);
        }

        return List.of();
    }

    private List<Path> walkJavaFiles(Path normalizedRoot) {
        try (var stream = Files.walk(normalizedRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String relPath = normalizedRoot.relativize(p).toString().replace('\\', '/');
                        return !relPath.startsWith("target/") && !relPath.contains("/target/")
                                && !relPath.startsWith(".git/") && !relPath.contains("/.git/");
                    })
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

    private Optional<Path> resolveSiblingTestRoot(Path normalizedRoot) {
        if (normalizedRoot != null && normalizedRoot.endsWith(Path.of("src", "main", "java"))) {
            Path main = normalizedRoot.getParent();
            if (main != null) {
                Path src = main.getParent();
                if (src != null) {
                    Path testJava = src.resolve(Path.of("test", "java"));
                    if (Files.isDirectory(testJava)) {
                        return Optional.of(testJava.normalize());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
