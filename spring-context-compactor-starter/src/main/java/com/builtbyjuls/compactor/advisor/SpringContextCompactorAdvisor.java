package com.builtbyjuls.compactor.advisor;

import com.builtbyjuls.compactor.compactor.SpringAstCompactor;
import com.builtbyjuls.compactor.config.CompactorProperties;
import com.builtbyjuls.compactor.model.AdvisorCompactionStatsDto;
import com.builtbyjuls.compactor.model.CompactedFileResultDto;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpringContextCompactorAdvisor implements CallAdvisor {

    public static final String STATS_CONTEXT_KEY = "spring.compactor.stats";
    private static final Pattern FENCED_JAVA_PATTERN = Pattern.compile("(?s)```java\\s*\\R(.*?)```");

    private final SpringAstCompactor astCompactor;
    private final CompactorProperties properties;
    private final List<AdvisorCompactionStatsDto> recordedStats = new CopyOnWriteArrayList<>();

    public SpringContextCompactorAdvisor(SpringAstCompactor astCompactor, CompactorProperties properties) {
        this.astCompactor = Objects.requireNonNull(astCompactor, "astCompactor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String getName() {
        return "SpringContextCompactorAdvisor";
    }

    @Override
    public int getOrder() {
        return properties.getAdvisorOrder();
    }

    public List<AdvisorCompactionStatsDto> getRecordedStats() {
        return List.copyOf(recordedStats);
    }

    public AdvisorCompactionStatsDto getCumulativeStats() {
        int totalDetected = 0;
        int totalCompacted = 0;
        int totalOriginal = 0;
        int totalCompactedTokens = 0;
        int totalSaved = 0;

        for (AdvisorCompactionStatsDto stat : recordedStats) {
            totalDetected += stat.codeBlocksDetected();
            totalCompacted += stat.codeBlocksCompacted();
            totalOriginal += stat.originalTokens();
            totalCompactedTokens += stat.compactedTokens();
            totalSaved += stat.tokensSaved();
        }

        double reduction = totalOriginal > 0 ? ((double) totalSaved / totalOriginal) * 100.0 : 0.0;
        return new AdvisorCompactionStatsDto(totalDetected, totalCompacted, totalOriginal, totalCompactedTokens, totalSaved, reduction);
    }

    public void clearStats() {
        recordedStats.clear();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Objects.requireNonNull(chatClientRequest, "chatClientRequest must not be null");
        Objects.requireNonNull(callAdvisorChain, "callAdvisorChain must not be null");

        if (chatClientRequest.prompt() == null || chatClientRequest.prompt().getInstructions() == null) {
            AdvisorCompactionStatsDto emptyStats = new AdvisorCompactionStatsDto(0, 0, 0, 0, 0, 0.0);
            recordedStats.add(emptyStats);
            ChatClientRequest updatedRequest = chatClientRequest.mutate().context(STATS_CONTEXT_KEY, emptyStats).build();
            ChatClientResponse response = callAdvisorChain.nextCall(updatedRequest);
            return response != null ? response.mutate().context(STATS_CONTEXT_KEY, emptyStats).build() : null;
        }

        List<Message> originalMessages = chatClientRequest.prompt().getInstructions();
        List<Message> updatedMessages = new ArrayList<>(originalMessages.size());

        int totalDetected = 0;
        int totalCompacted = 0;
        int totalOriginalChars = 0;
        int totalCompactedChars = 0;

        for (Message message : originalMessages) {
            if (message instanceof UserMessage userMsg) {
                String originalText = userMsg.getText() != null ? userMsg.getText() : "";
                totalOriginalChars += originalText.length();
                CompactionTransformationResult transformed = transformMessageText(originalText);
                totalDetected += transformed.detected();
                totalCompacted += transformed.compacted();
                totalCompactedChars += transformed.text().length();
                if (!transformed.text().equals(originalText)) {
                    updatedMessages.add(userMsg.mutate().text(transformed.text()).build());
                } else {
                    updatedMessages.add(userMsg);
                }
            } else if (message instanceof SystemMessage sysMsg) {
                String originalText = sysMsg.getText() != null ? sysMsg.getText() : "";
                totalOriginalChars += originalText.length();
                CompactionTransformationResult transformed = transformMessageText(originalText);
                totalDetected += transformed.detected();
                totalCompacted += transformed.compacted();
                totalCompactedChars += transformed.text().length();
                if (!transformed.text().equals(originalText)) {
                    updatedMessages.add(sysMsg.mutate().text(transformed.text()).build());
                } else {
                    updatedMessages.add(sysMsg);
                }
            } else {
                if (message.getText() != null) {
                    totalOriginalChars += message.getText().length();
                    totalCompactedChars += message.getText().length();
                }
                updatedMessages.add(message);
            }
        }

        int originalTokens = (int) Math.ceil(totalOriginalChars / 4.0);
        int compactedTokens = (int) Math.ceil(totalCompactedChars / 4.0);
        int tokensSaved = Math.max(0, originalTokens - compactedTokens);
        double tokenReductionPercent = originalTokens > 0
                ? ((double) tokensSaved / originalTokens) * 100.0
                : 0.0;

        AdvisorCompactionStatsDto stats = new AdvisorCompactionStatsDto(
                totalDetected,
                totalCompacted,
                originalTokens,
                compactedTokens,
                tokensSaved,
                tokenReductionPercent
        );
        recordedStats.add(stats);

        Prompt updatedPrompt = chatClientRequest.prompt().mutate()
                .messages(updatedMessages)
                .build();

        ChatClientRequest updatedRequest = chatClientRequest.mutate()
                .prompt(updatedPrompt)
                .context(STATS_CONTEXT_KEY, stats)
                .build();

        ChatClientResponse response = callAdvisorChain.nextCall(updatedRequest);
        if (response != null) {
            return response.mutate().context(STATS_CONTEXT_KEY, stats).build();
        }
        return null;
    }

    private CompactionTransformationResult transformMessageText(String text) {
        if (text == null || text.isBlank()) {
            return new CompactionTransformationResult(text != null ? text : "", 0, 0);
        }

        Matcher matcher = FENCED_JAVA_PATTERN.matcher(text);
        int detected = 0;
        int compacted = 0;
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            detected++;
            String codeBlockContent = matcher.group(1);
            CompactedFileResultDto result = astCompactor.compactSource("prompt-snippet.java", codeBlockContent);
            if (!result.className().isEmpty() && result.compactedEstimatedTokens() < result.originalEstimatedTokens()) {
                compacted++;
                String formattedSkeleton = result.compactedSkeleton().stripTrailing();
                String replacement = "```java\n" + formattedSkeleton + "\n```";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        if (detected > 0) {
            return new CompactionTransformationResult(sb.toString(), detected, compacted);
        }

        String trimmed = text.stripLeading();
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ") || trimmed.startsWith("@")) {
            detected++;
            CompactedFileResultDto result = astCompactor.compactSource("prompt-snippet.java", text);
            if (!result.className().isEmpty() && result.compactedEstimatedTokens() < result.originalEstimatedTokens()) {
                compacted++;
                return new CompactionTransformationResult(result.compactedSkeleton(), detected, compacted);
            }
        }

        return new CompactionTransformationResult(text, detected, compacted);
    }

    private record CompactionTransformationResult(String text, int detected, int compacted) {}
}
