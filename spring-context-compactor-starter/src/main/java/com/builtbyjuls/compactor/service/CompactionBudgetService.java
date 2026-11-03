package com.builtbyjuls.compactor.service;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProjectCompactionBudgetDto;
import com.builtbyjuls.compactor.model.SpringStereotype;
import com.builtbyjuls.compactor.model.StereotypeBudgetRowDto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CompactionBudgetService {

    private final SpringAstCompactor astCompactor;

    public CompactionBudgetService(SpringAstCompactor astCompactor) {
        this.astCompactor = Objects.requireNonNull(astCompactor, "astCompactor must not be null");
    }

    public ProjectCompactionBudgetDto analyzeProjectBudget(Path sourceRoot) {
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            return new ProjectCompactionBudgetDto(
                    sourceRoot != null ? sourceRoot.toString().replace('\\', '/') : "",
                    0, 0, 0, 0, 0, 0, 0.0, List.of(), List.of()
            );
        }

        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        List<Path> javaFiles = walkJavaFiles(normalizedRoot);

        List<CompactedFileResultDto> compactedFiles = new ArrayList<>();
        for (Path file : javaFiles) {
            compactedFiles.add(astCompactor.compactFile(sourceRoot, file));
        }

        List<StereotypeBudgetRowDto> stereotypeBreakdown = new ArrayList<>();
        for (SpringStereotype stereotype : SpringStereotype.values()) {
            List<CompactedFileResultDto> forStereotype = compactedFiles.stream()
                    .filter(f -> f.stereotype() == stereotype)
                    .toList();
            if (!forStereotype.isEmpty()) {
                int fileCount = forStereotype.size();
                int origLines = forStereotype.stream().mapToInt(CompactedFileResultDto::originalLines).sum();
                int compLines = forStereotype.stream().mapToInt(CompactedFileResultDto::compactedLines).sum();
                int origTokens = forStereotype.stream().mapToInt(CompactedFileResultDto::originalEstimatedTokens).sum();
                int compTokens = forStereotype.stream().mapToInt(CompactedFileResultDto::compactedEstimatedTokens).sum();
                double tokenReductionPercent = origTokens > 0
                        ? Math.max(0.0, ((double) (origTokens - compTokens) / origTokens) * 100.0)
                        : 0.0;
                stereotypeBreakdown.add(new StereotypeBudgetRowDto(
                        stereotype,
                        fileCount,
                        origLines,
                        compLines,
                        origTokens,
                        compTokens,
                        tokenReductionPercent
                ));
            }
        }

        List<MethodTrapNoteDto> detectedTraps = new ArrayList<>();
        for (CompactedFileResultDto fileResult : compactedFiles) {
            detectedTraps.addAll(fileResult.trapNotes());
        }

        int totalFiles = compactedFiles.size();
        int originalTotalLines = compactedFiles.stream().mapToInt(CompactedFileResultDto::originalLines).sum();
        int compactedTotalLines = compactedFiles.stream().mapToInt(CompactedFileResultDto::compactedLines).sum();
        int originalEstimatedTokens = compactedFiles.stream().mapToInt(CompactedFileResultDto::originalEstimatedTokens).sum();
        int compactedEstimatedTokens = compactedFiles.stream().mapToInt(CompactedFileResultDto::compactedEstimatedTokens).sum();
        int tokensSaved = Math.max(0, originalEstimatedTokens - compactedEstimatedTokens);
        double overallTokenReductionPercent = originalEstimatedTokens > 0
                ? Math.max(0.0, ((double) (originalEstimatedTokens - compactedEstimatedTokens) / originalEstimatedTokens) * 100.0)
                : 0.0;

        return new ProjectCompactionBudgetDto(
                sourceRoot.toString().replace('\\', '/'),
                totalFiles,
                originalTotalLines,
                compactedTotalLines,
                originalEstimatedTokens,
                compactedEstimatedTokens,
                tokensSaved,
                overallTokenReductionPercent,
                stereotypeBreakdown,
                detectedTraps
        );
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
}
