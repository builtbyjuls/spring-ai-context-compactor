package com.builtbyjuls.compactor.compactor;

import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.CompactedMethodSummaryDto;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.builtbyjuls.compactor.model.SpringStereotype;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringAstCompactor {

    private static final Set<String> SPRING_LIFECYCLE_OR_MESSAGING_ANNOTATIONS = Set.of(
            "Transactional",
            "RabbitListener",
            "KafkaListener",
            "JmsListener",
            "Scheduled",
            "EventListener",
            "PostConstruct"
    );

    private final StereotypeClassifier classifier;
    private final SpringProxyTrapScanner trapScanner;
    private final CompactorProperties properties;
    private final JavaParser javaParser;

    public SpringAstCompactor(StereotypeClassifier classifier, SpringProxyTrapScanner trapScanner, CompactorProperties properties) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
        this.trapScanner = Objects.requireNonNull(trapScanner, "trapScanner must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.javaParser = new JavaParser(config);
    }

    public CompactedFileResultDto compactFile(Path sourceRoot, Path filePath) {
        Objects.requireNonNull(sourceRoot, "sourceRoot must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        try {
            String javaSource = Files.readString(filePath, StandardCharsets.UTF_8);
            Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
            Path normalizedFile = filePath.toAbsolutePath().normalize();
            String relPath = normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
            return compactSource(relPath, javaSource);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + filePath, e);
        }
    }

    public CompactedFileResultDto compactSource(String filePath, String javaSource) {
        if (filePath == null) {
            filePath = "";
        }
        if (javaSource == null) {
            javaSource = "";
        }

        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaSource);
        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            return emptyResult(filePath, javaSource);
        }

        CompilationUnit cu = parseResult.getResult().get();
        String packageName = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");

        TypeDeclaration<?> primaryType = cu.getPrimaryType().orElseGet(() ->
                cu.getTypes().isEmpty() ? null : cu.getTypes().get(0)
        );

        if (primaryType == null) {
            return emptyResult(filePath, javaSource);
        }

        String className = primaryType.getNameAsString();
        SpringStereotype stereotype = classifier.classify(primaryType);

        List<MethodTrapNoteDto> allTraps = List.of();
        if (properties.isDetectProxyTraps() && primaryType instanceof ClassOrInterfaceDeclaration classOrInterface) {
            allTraps = trapScanner.scan(classOrInterface);
        }

        List<String> injectedDependencies = extractInjectedDependencies(primaryType, stereotype);
        List<String> importedTypes = extractImportedTypes(cu);

        List<CompactedMethodSummaryDto> methodSummaries = new ArrayList<>();
        String classBodySkeleton = renderClassBody(cu, primaryType, stereotype, allTraps, methodSummaries);

        int origLines = (int) javaSource.lines().count();
        if (origLines == 0 && !javaSource.isEmpty()) {
            origLines = 1;
        }
        int origTokens = (int) Math.ceil(javaSource.length() / 4.0);

        String finalSkeleton = assembleFinalSkeleton(
                filePath,
                stereotype,
                injectedDependencies,
                importedTypes,
                origLines,
                origTokens,
                classBodySkeleton
        );

        int compactedLines = (int) finalSkeleton.lines().count();
        int compactedTokens = (int) Math.ceil(finalSkeleton.length() / 4.0);
        double tokenReductionPercent = origTokens > 0
                ? Math.max(0.0, ((double) (origTokens - compactedTokens) / origTokens) * 100.0)
                : 0.0;

        // Re-render once if token count or reduction formatted string changes line 2
        finalSkeleton = renderBanner(
                filePath,
                stereotype,
                injectedDependencies,
                importedTypes,
                origLines,
                origTokens,
                compactedLines,
                compactedTokens,
                tokenReductionPercent
        ) + classBodySkeleton;

        compactedLines = (int) finalSkeleton.lines().count();
        compactedTokens = (int) Math.ceil(finalSkeleton.length() / 4.0);
        tokenReductionPercent = origTokens > 0
                ? Math.max(0.0, ((double) (origTokens - compactedTokens) / origTokens) * 100.0)
                : 0.0;

        return new CompactedFileResultDto(
                filePath,
                packageName,
                className,
                stereotype,
                injectedDependencies,
                origLines,
                compactedLines,
                origTokens,
                compactedTokens,
                tokenReductionPercent,
                finalSkeleton,
                methodSummaries,
                allTraps
        );
    }

    private List<String> extractInjectedDependencies(TypeDeclaration<?> typeDeclaration, SpringStereotype stereotype) {
        if (stereotype == SpringStereotype.ENTITY) {
            return List.of();
        }
        List<String> dependencies = new ArrayList<>();
        for (FieldDeclaration field : typeDeclaration.getFields()) {
            if (shouldRetainField(field, stereotype)) {
                String typeName = field.getElementType().asString();
                if (!dependencies.contains(typeName)) {
                    dependencies.add(typeName);
                }
            }
        }
        return Collections.unmodifiableList(dependencies);
    }

    private boolean shouldRetainField(FieldDeclaration field, SpringStereotype stereotype) {
        if (field.isStatic()) {
            return false;
        }
        if (stereotype == SpringStereotype.ENTITY) {
            return true;
        }
        if (field.isPrivate() && field.isFinal()) {
            return true;
        }
        return hasAnnotation(field, "Autowired", "Value");
    }

    private String renderClassBody(
            CompilationUnit cu,
            TypeDeclaration<?> primaryType,
            SpringStereotype stereotype,
            List<MethodTrapNoteDto> allTraps,
            List<CompactedMethodSummaryDto> methodSummaries
    ) {
        StringBuilder sb = new StringBuilder();

        cu.getPackageDeclaration().ifPresent(pkg ->
                sb.append("package ").append(pkg.getNameAsString()).append(";\n\n")
        );

        for (AnnotationExpr annotation : primaryType.getAnnotations()) {
            sb.append(annotation.toString()).append("\n");
        }

        sb.append(renderTypeHeader(primaryType)).append(" {\n");

        for (FieldDeclaration field : primaryType.getFields()) {
            if (shouldRetainField(field, stereotype)) {
                sb.append("\n").append(renderField(field));
            }
        }

        for (BodyDeclaration<?> member : primaryType.getMembers()) {
            if (member instanceof ConstructorDeclaration constructor) {
                if (!isBoilerplateConstructor(constructor, stereotype)) {
                    sb.append("\n").append(renderConstructor(constructor));
                }
            } else if (member instanceof MethodDeclaration method) {
                if (!isBoilerplateMethod(method, stereotype)) {
                    List<MethodTrapNoteDto> methodTraps = findTrapsForMethod(method, allTraps);

                    int startLine = method.getBegin().map(p -> p.line).orElse(0);
                    int endLine = method.getEnd().map(p -> p.line).orElse(0);
                    int omittedLines = method.getBody().map(b -> {
                        int bStart = b.getBegin().map(p -> p.line).orElse(startLine);
                        int bEnd = b.getEnd().map(p -> p.line).orElse(endLine);
                        return Math.max(1, bEnd - bStart + 1);
                    }).orElse(0);

                    List<String> retainedAnnotations = method.getAnnotations().stream()
                            .filter(a -> !"Override".equals(extractSimpleName(a.getNameAsString())))
                            .map(Node::toString)
                            .toList();

                    String signature = buildMethodSignature(method);

                    methodSummaries.add(new CompactedMethodSummaryDto(
                            method.getNameAsString(),
                            signature,
                            startLine,
                            endLine,
                            omittedLines,
                            retainedAnnotations,
                            methodTraps
                    ));

                    sb.append("\n").append(renderMethod(method, signature, startLine, endLine, omittedLines, methodTraps, stereotype));
                }
            } else if (member instanceof TypeDeclaration<?> innerType) {
                if (!isBoilerplateInnerClass(innerType)) {
                    sb.append("\n").append(renderInnerType(innerType));
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String renderTypeHeader(TypeDeclaration<?> type) {
        StringBuilder sb = new StringBuilder();
        String modifiers = type.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .collect(Collectors.joining(" "));

        if (!modifiers.isEmpty()) {
            sb.append(modifiers).append(" ");
        }

        if (type instanceof ClassOrInterfaceDeclaration classOrInterface) {
            sb.append(classOrInterface.isInterface() ? "interface " : "class ");
            sb.append(classOrInterface.getNameAsString());

            if (!classOrInterface.getTypeParameters().isEmpty()) {
                sb.append("<").append(
                        classOrInterface.getTypeParameters().stream()
                                .map(Node::toString)
                                .collect(Collectors.joining(", "))
                ).append(">");
            }

            if (!classOrInterface.getExtendedTypes().isEmpty()) {
                sb.append(" extends ").append(
                        classOrInterface.getExtendedTypes().stream()
                                .map(Node::toString)
                                .collect(Collectors.joining(", "))
                );
            }

            if (!classOrInterface.getImplementedTypes().isEmpty()) {
                sb.append(" implements ").append(
                        classOrInterface.getImplementedTypes().stream()
                                .map(Node::toString)
                                .collect(Collectors.joining(", "))
                );
            }
        } else if (type instanceof RecordDeclaration recordDecl) {
            sb.append("record ").append(recordDecl.getNameAsString()).append("(");
            sb.append(recordDecl.getParameters().stream().map(Node::toString).collect(Collectors.joining(", ")));
            sb.append(")");
            if (!recordDecl.getImplementedTypes().isEmpty()) {
                sb.append(" implements ").append(
                        recordDecl.getImplementedTypes().stream().map(Node::toString).collect(Collectors.joining(", "))
                );
            }
        } else if (type instanceof EnumDeclaration enumDecl) {
            sb.append("enum ").append(enumDecl.getNameAsString());
        } else {
            sb.append("class ").append(type.getNameAsString());
        }

        return sb.toString();
    }

    private String renderInnerType(TypeDeclaration<?> innerType) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr annotation : innerType.getAnnotations()) {
            sb.append("    ").append(annotation.toString()).append("\n");
        }
        sb.append("    ").append(renderTypeHeader(innerType)).append(" {");
        if (innerType instanceof EnumDeclaration enumDecl) {
            String entries = enumDecl.getEntries().stream()
                    .map(e -> e.getNameAsString())
                    .collect(Collectors.joining(", "));
            sb.append(" ").append(entries).append(" }\n");
        } else {
            sb.append(" /* inner type preserved */ }\n");
        }
        return sb.toString();
    }

    private String renderField(FieldDeclaration field) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr annotation : field.getAnnotations()) {
            sb.append("    ").append(annotation.toString()).append("\n");
        }
        sb.append("    ");
        String modifiers = field.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .collect(Collectors.joining(" "));

        if (!modifiers.isEmpty()) {
            sb.append(modifiers).append(" ");
        }

        sb.append(field.getElementType().asString()).append(" ");
        sb.append(field.getVariables().stream()
                .map(VariableDeclarator::getNameAsString)
                .collect(Collectors.joining(", ")));
        sb.append(";\n");
        return sb.toString();
    }

    private String renderConstructor(ConstructorDeclaration constructor) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr annotation : constructor.getAnnotations()) {
            sb.append("    ").append(annotation.toString()).append("\n");
        }
        sb.append("    ");
        String modifiers = constructor.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .collect(Collectors.joining(" "));

        if (!modifiers.isEmpty()) {
            sb.append(modifiers).append(" ");
        }

        sb.append(constructor.getNameAsString()).append("(");
        sb.append(constructor.getParameters().stream()
                .map(Node::toString)
                .collect(Collectors.joining(", ")));
        sb.append(")");

        int startLine = constructor.getBegin().map(p -> p.line).orElse(0);
        int endLine = constructor.getEnd().map(p -> p.line).orElse(0);
        int omittedLines = Math.max(1, endLine - startLine + 1);

        if (properties.isIncludeLineNumbers()) {
            sb.append(" /* L").append(startLine).append("-L").append(endLine)
                    .append(": body omitted (").append(omittedLines).append(" lines) */ ;\n");
        } else {
            sb.append(" /* body omitted (").append(omittedLines).append(" lines) */ ;\n");
        }

        return sb.toString();
    }

    public static String buildMethodSignature(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();
        String modifiers = method.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .collect(Collectors.joining(" "));

        if (!modifiers.isEmpty()) {
            sb.append(modifiers).append(" ");
        }

        if (!method.getTypeParameters().isEmpty()) {
            sb.append("<").append(
                    method.getTypeParameters().stream()
                            .map(Node::toString)
                            .collect(Collectors.joining(", "))
            ).append("> ");
        }

        sb.append(method.getType().asString()).append(" ");
        sb.append(method.getNameAsString()).append("(");
        sb.append(method.getParameters().stream()
                .map(Node::toString)
                .collect(Collectors.joining(", ")));
        sb.append(")");

        if (!method.getThrownExceptions().isEmpty()) {
            sb.append(" throws ").append(
                    method.getThrownExceptions().stream()
                            .map(Node::toString)
                            .collect(Collectors.joining(", "))
            );
        }

        return sb.toString();
    }

    private String renderMethod(
            MethodDeclaration method,
            String signature,
            int startLine,
            int endLine,
            int omittedLines,
            List<MethodTrapNoteDto> methodTraps,
            SpringStereotype stereotype
    ) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr annotation : method.getAnnotations()) {
            if ("Override".equals(extractSimpleName(annotation.getNameAsString()))) {
                continue;
            }
            sb.append("    ").append(annotation.toString()).append("\n");
        }
        sb.append("    ").append(signature);

        if (method.getBody().isEmpty()) {
            sb.append(";\n");
        } else {
            // Check for single-line return statement in CONFIGURATION classes
            if (stereotype == SpringStereotype.CONFIGURATION && hasAnnotation(method, "Bean")) {
                BlockStmt body = method.getBody().get();
                if (body.getStatements().size() == 1 && body.getStatements().get(0).isReturnStmt()) {
                    String stmtStr = body.getStatements().get(0).toString().trim();
                    sb.append(" { ").append(stmtStr).append(" }\n");
                    return sb.toString();
                }
            }

            if (properties.isIncludeLineNumbers()) {
                sb.append(" /* L").append(startLine).append("-L").append(endLine)
                        .append(": body omitted (").append(omittedLines).append(" lines)");
            } else {
                sb.append(" /* body omitted (").append(omittedLines).append(" lines)");
            }

            if (!methodTraps.isEmpty()) {
                for (MethodTrapNoteDto trap : methodTraps) {
                    sb.append(" | [TRAP: ").append(trap.trapType().name()).append("] ").append(trap.summary());
                }
            }

            sb.append(" */ ;\n");
        }

        return sb.toString();
    }

    public static List<MethodTrapNoteDto> findTrapsForMethod(MethodDeclaration method, List<MethodTrapNoteDto> allTraps) {
        String methodName = method.getNameAsString();
        int startLine = method.getBegin().map(p -> p.line).orElse(0);
        int endLine = method.getEnd().map(p -> p.line).orElse(0);

        return allTraps.stream()
                .filter(t -> {
                    if (t.callerMethod().equals(methodName)) {
                        if (startLine > 0 && endLine >= startLine && t.callSiteLine() > 0) {
                            return t.callSiteLine() >= startLine && t.callSiteLine() <= endLine;
                        }
                        return true;
                    }
                    if (t.targetMethod().equals(methodName) && t.trapType() == ProxyTrapType.NON_PUBLIC_TRANSACTIONAL) {
                        if (startLine > 0 && endLine >= startLine && t.callSiteLine() > 0) {
                            return t.callSiteLine() >= startLine && t.callSiteLine() <= endLine;
                        }
                        return true;
                    }
                    return false;
                })
                .distinct()
                .toList();
    }

    private List<String> extractImportedTypes(CompilationUnit cu) {
        List<String> types = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            if (isJavaLangImport(imp)) {
                continue;
            }
            String formatted;
            if (imp.isAsterisk()) {
                formatted = name + ".*";
            } else {
                formatted = extractSimpleName(name);
            }
            if (!formatted.isBlank() && !types.contains(formatted)) {
                types.add(formatted);
            }
        }
        return Collections.unmodifiableList(types);
    }

    private boolean isJavaLangImport(ImportDeclaration imp) {
        String name = imp.getNameAsString();
        if (imp.isAsterisk()) {
            return "java.lang".equals(name);
        }
        return name.startsWith("java.lang.") && !name.substring("java.lang.".length()).contains(".");
    }

    private boolean isBoilerplateConstructor(ConstructorDeclaration constructor, SpringStereotype stereotype) {
        if (stereotype == SpringStereotype.ENTITY) {
            return true;
        }
        if (constructor.getBody().isEmpty()) {
            return true;
        }
        BlockStmt body = constructor.getBody();
        for (Statement stmt : body.getStatements()) {
            if (stmt.isExplicitConstructorInvocationStmt()) {
                continue;
            }
            if (stmt.isExpressionStmt()) {
                Expression expr = stmt.asExpressionStmt().getExpression();
                if (expr.isAssignExpr()) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    private boolean isBoilerplateMethod(MethodDeclaration method, SpringStereotype stereotype) {
        String name = method.getNameAsString();
        if (stereotype == SpringStereotype.ENTITY) {
            if ((name.startsWith("get") || name.startsWith("is")) && method.getParameters().isEmpty()) {
                return true;
            }
            if (name.startsWith("set") && method.getParameters().size() == 1) {
                return true;
            }
            if ("equals".equals(name) && method.getParameters().size() == 1) {
                return true;
            }
            if ("hashCode".equals(name) && method.getParameters().isEmpty()) {
                return true;
            }
            if ("toString".equals(name) && method.getParameters().isEmpty()) {
                return true;
            }
        }

        if (method.isPrivate() && stereotype == SpringStereotype.ENTITY) {
            return !hasAnyAnnotation(method, SPRING_LIFECYCLE_OR_MESSAGING_ANNOTATIONS);
        }

        return false;
    }

    private boolean isBoilerplateInnerClass(TypeDeclaration<?> innerType) {
        String name = innerType.getNameAsString();
        if ("Builder".equals(name) || name.endsWith("Builder")) {
            return true;
        }
        if (innerType instanceof ClassOrInterfaceDeclaration classOrInterface) {
            return classOrInterface.getMethods().stream()
                    .anyMatch(m -> "build".equals(m.getNameAsString()));
        }
        return false;
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String... simpleNames) {
        Set<String> set = Set.of(simpleNames);
        return hasAnyAnnotation(node, set);
    }

    private boolean hasAnyAnnotation(NodeWithAnnotations<?> node, Set<String> simpleNames) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = extractSimpleName(annotation.getNameAsString());
            if (simpleNames.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private String extractSimpleName(String qualifiedName) {
        int dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex >= 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
    }

    private String assembleFinalSkeleton(
            String filePath,
            SpringStereotype stereotype,
            List<String> injectedDependencies,
            List<String> importedTypes,
            int origLines,
            int origTokens,
            String classBodySkeleton
    ) {
        int estimatedCompactedLines = (int) classBodySkeleton.lines().count()
                + 2
                + (injectedDependencies.isEmpty() ? 0 : 1)
                + (importedTypes.isEmpty() ? 0 : 1);
        int estimatedCompactedTokens = (int) Math.ceil((classBodySkeleton.length() + 150) / 4.0);
        double estimatedReduction = origTokens > 0
                ? Math.max(0.0, ((double) (origTokens - estimatedCompactedTokens) / origTokens) * 100.0)
                : 0.0;

        String banner = renderBanner(
                filePath,
                stereotype,
                injectedDependencies,
                importedTypes,
                origLines,
                origTokens,
                estimatedCompactedLines,
                estimatedCompactedTokens,
                estimatedReduction
        );

        return banner + classBodySkeleton;
    }

    private String renderBanner(
            String filePath,
            SpringStereotype stereotype,
            List<String> injectedDependencies,
            List<String> importedTypes,
            int origLines,
            int origTokens,
            int compactedLines,
            int compactedTokens,
            double tokenReductionPercent
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("// [SPRING COMPACTOR] File: ").append(filePath).append("\n");
        sb.append(String.format(
                Locale.ROOT,
                "// Stereotype: %s | Original: %d lines (~%d tokens) -> Compacted: %d lines (~%d tokens) | Saved: %.1f%%\n",
                stereotype.name(),
                origLines,
                origTokens,
                compactedLines,
                compactedTokens,
                tokenReductionPercent
        ));
        if (!injectedDependencies.isEmpty()) {
            sb.append("// Injected Beans: [")
                    .append(String.join(", ", injectedDependencies))
                    .append("]\n");
        }
        if (!importedTypes.isEmpty()) {
            sb.append("// Imported Types: [")
                    .append(String.join(", ", importedTypes))
                    .append("]\n");
        }
        return sb.toString();
    }

    private CompactedFileResultDto emptyResult(String filePath, String javaSource) {
        int origLines = (int) javaSource.lines().count();
        int origTokens = (int) Math.ceil(javaSource.length() / 4.0);
        return new CompactedFileResultDto(
                filePath,
                "",
                "",
                SpringStereotype.COMPONENT,
                List.of(),
                origLines,
                origLines,
                origTokens,
                origTokens,
                0.0,
                javaSource,
                List.of(),
                List.of()
        );
    }
}
