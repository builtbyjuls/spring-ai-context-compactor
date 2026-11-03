package com.builtbyjuls.compactor.compactor;

import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ThisExpr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SpringProxyTrapScanner {

    private record TransactionalMetadata(boolean isTransactional, boolean isExplicit, boolean isReadOnly) {
        static final TransactionalMetadata NONE = new TransactionalMetadata(false, false, false);
    }

    public List<MethodTrapNoteDto> scan(ClassOrInterfaceDeclaration classOrInterface) {
        if (classOrInterface == null) {
            return List.of();
        }

        List<MethodTrapNoteDto> traps = new ArrayList<>();
        TransactionalMetadata classTx = extractTransactionalMetadata(classOrInterface.getAnnotations());

        Map<String, TransactionalMetadata> methodTxMap = new HashMap<>();

        for (MethodDeclaration method : classOrInterface.getMethods()) {
            TransactionalMetadata methodTx = extractTransactionalMetadata(method.getAnnotations());
            if (methodTx.isTransactional()) {
                methodTxMap.put(method.getNameAsString(), methodTx);
                if (!method.isPublic()) {
                    int declLine = method.getBegin().map(p -> p.line).orElse(0);
                    traps.add(new MethodTrapNoteDto(
                            method.getNameAsString(),
                            method.getNameAsString(),
                            declLine,
                            ProxyTrapType.NON_PUBLIC_TRANSACTIONAL,
                            "Method '" + method.getNameAsString() + "' is annotated with @Transactional but is not public (Spring AOP proxy cannot intercept non-public methods)"
                    ));
                }
            } else if (classTx.isTransactional() && method.isPublic()) {
                methodTxMap.put(method.getNameAsString(), new TransactionalMetadata(true, false, classTx.isReadOnly()));
            }
        }

        for (MethodDeclaration method : classOrInterface.getMethods()) {
            String callerName = method.getNameAsString();
            int callerStartLine = method.getBegin().map(p -> p.line).orElse(0);
            TransactionalMetadata callerTx = methodTxMap.getOrDefault(callerName, TransactionalMetadata.NONE);

            method.getBody().ifPresent(body -> {
                List<MethodCallExpr> calls = body.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                    if (isSelfInvocation(call)) {
                        String targetName = call.getNameAsString();
                        TransactionalMetadata targetTx = methodTxMap.get(targetName);

                        if (targetTx != null && targetTx.isTransactional()) {
                            int callSiteLine = call.getBegin().map(p -> p.line).orElse(callerStartLine);

                            if (callerTx.isTransactional() && callerTx.isReadOnly() && !targetTx.isReadOnly()) {
                                traps.add(new MethodTrapNoteDto(
                                        callerName,
                                        targetName,
                                        callSiteLine,
                                        ProxyTrapType.READ_ONLY_CALLING_WRITE_REQUIRED,
                                        "Method '" + callerName + "' (@Transactional(readOnly = true)) calls '" + targetName + "' (write @Transactional) via this reference; read-only transaction context persists and write operations may fail or violate isolation"
                                ));
                            }

                            traps.add(new MethodTrapNoteDto(
                                    callerName,
                                    targetName,
                                    callSiteLine,
                                    ProxyTrapType.SELF_INVOCATION_BYPASS,
                                    "Method '" + callerName + "' calls @Transactional method '" + targetName + "' via intra-class invocation bypassing Spring AOP proxy"
                            ));
                        }
                    }
                }
            });
        }

        traps.sort(Comparator
                .comparingInt(MethodTrapNoteDto::callSiteLine)
                .thenComparing(t -> t.trapType().name())
                .thenComparing(MethodTrapNoteDto::targetMethod)
        );

        return List.copyOf(traps);
    }

    private boolean isSelfInvocation(MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return true;
        }
        return call.getScope().get() instanceof ThisExpr;
    }

    private TransactionalMetadata extractTransactionalMetadata(List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            String simpleName = extractSimpleName(annotation.getNameAsString());
            if ("Transactional".equals(simpleName)) {
                boolean readOnly = false;
                if (annotation instanceof NormalAnnotationExpr normal) {
                    for (MemberValuePair pair : normal.getPairs()) {
                        if ("readOnly".equals(pair.getNameAsString())) {
                            if (pair.getValue() instanceof BooleanLiteralExpr boolExpr) {
                                readOnly = boolExpr.getValue();
                            }
                        }
                    }
                }
                return new TransactionalMetadata(true, true, readOnly);
            }
        }
        return TransactionalMetadata.NONE;
    }

    private String extractSimpleName(String qualifiedName) {
        int dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex >= 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
    }
}
