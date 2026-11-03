package com.builtbyjuls.compactor.compactor;

import com.builtbyjuls.compactor.model.SpringStereotype;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.Set;

public class StereotypeClassifier {

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "RestController", "Controller"
    );

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of(
            "Entity", "MappedSuperclass", "Embeddable"
    );

    private static final Set<String> REPOSITORY_INTERFACES = Set.of(
            "JpaRepository", "CrudRepository", "PagingAndSortingRepository"
    );

    private static final Set<String> CONFIGURATION_ANNOTATIONS = Set.of(
            "Configuration", "ConfigurationProperties"
    );

    private static final Set<String> MESSAGING_METHOD_ANNOTATIONS = Set.of(
            "RabbitListener", "KafkaListener", "JmsListener"
    );

    public SpringStereotype classify(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration == null) {
            return SpringStereotype.COMPONENT;
        }

        if (hasAnyAnnotation(typeDeclaration, CONTROLLER_ANNOTATIONS)) {
            return SpringStereotype.CONTROLLER;
        }

        if (hasAnyAnnotation(typeDeclaration, ENTITY_ANNOTATIONS)) {
            return SpringStereotype.ENTITY;
        }

        if (hasAnnotation(typeDeclaration, "Repository") || extendsRepositoryInterface(typeDeclaration)) {
            return SpringStereotype.REPOSITORY;
        }

        if (hasAnyAnnotation(typeDeclaration, CONFIGURATION_ANNOTATIONS)) {
            return SpringStereotype.CONFIGURATION;
        }

        if (hasMessagingListenerMethods(typeDeclaration)) {
            return SpringStereotype.MESSAGING_LISTENER;
        }

        if (hasAnnotation(typeDeclaration, "Service")) {
            return SpringStereotype.SERVICE;
        }

        return SpringStereotype.COMPONENT;
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

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            if (extractSimpleName(annotation.getNameAsString()).equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean extendsRepositoryInterface(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            if (matchesAnyInterface(classOrInterface.getExtendedTypes())) {
                return true;
            }
            return matchesAnyInterface(classOrInterface.getImplementedTypes());
        }
        return false;
    }

    private boolean matchesAnyInterface(NodeList<ClassOrInterfaceType> types) {
        for (ClassOrInterfaceType type : types) {
            String simpleName = extractSimpleName(type.getNameAsString());
            if (REPOSITORY_INTERFACES.contains(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMessagingListenerMethods(TypeDeclaration<?> typeDeclaration) {
        for (MethodDeclaration method : typeDeclaration.getMethods()) {
            if (hasAnyAnnotation(method, MESSAGING_METHOD_ANNOTATIONS)) {
                return true;
            }
        }
        return false;
    }

    private String extractSimpleName(String qualifiedName) {
        int dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex >= 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
    }
}
