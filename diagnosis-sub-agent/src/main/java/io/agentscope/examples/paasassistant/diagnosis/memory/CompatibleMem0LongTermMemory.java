package io.agentscope.examples.paasassistant.diagnosis.memory;

import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.mem0.Mem0AddRequest;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0Client;
import io.agentscope.core.memory.mem0.Mem0Message;
import io.agentscope.core.memory.mem0.Mem0SearchRequest;
import io.agentscope.core.memory.mem0.Mem0SearchResponse;
import io.agentscope.core.memory.mem0.Mem0SearchResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

public class CompatibleMem0LongTermMemory implements LongTermMemory {

    private static final int MAX_MEMORY_TEXT_LENGTH = 8000;

    private static final Pattern METADATA_TAG_PATTERN =
            Pattern.compile("<(?:userId|traceId)>.*?</(?:userId|traceId)>", Pattern.DOTALL);

    private final Mem0Client client;

    private final Mem0ApiType apiType;

    private final String agentId;

    private final String userId;

    private final String runId;

    private final Map<String, Object> metadata;

    private final boolean infer;

    public CompatibleMem0LongTermMemory(
            String agentId,
            String userId,
            String runId,
            Map<String, Object> metadata,
            String apiBaseUrl,
            String apiKey,
            Mem0ApiType apiType,
            Duration timeout,
            boolean infer) {
        this.client = new Mem0Client(apiBaseUrl, apiKey, apiType, timeout);
        this.apiType = apiType;
        this.agentId = agentId;
        this.userId = userId;
        this.runId = runId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.infer = infer;
    }

    @Override
    public Mono<Void> record(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return Mono.empty();
        }

        List<Mem0Message> mem0Messages =
                messages.stream()
                        .filter(Objects::nonNull)
                        .filter(this::isSupportedRole)
                        .map(this::convertToMem0Message)
                        .flatMap(java.util.Optional::stream)
                        .toList();

        if (mem0Messages.isEmpty()) {
            return Mono.empty();
        }

        Map<String, Object> enrichedMetadata = new java.util.HashMap<>(metadata);
        enrichedMetadata.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        Mem0AddRequest request =
                Mem0AddRequest.builder()
                        .messages(mem0Messages)
                        .agentId(agentId)
                        .userId(userId)
                        .runId(runId)
                        .metadata(enrichedMetadata)
                        .infer(infer)
                        .build();

        return client.add(request).then();
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        if (msg == null) {
            return Mono.just("");
        }

        String query = sanitize(msg.getTextContent());
        if (query.isEmpty()) {
            return Mono.just("");
        }

        return client.search(buildSearchRequest(query))
                .map(CompatibleMem0LongTermMemory::joinResults)
                .onErrorReturn("");
    }

    private boolean isSupportedRole(Msg msg) {
        return msg.getRole() == MsgRole.USER || msg.getRole() == MsgRole.ASSISTANT;
    }

    private java.util.Optional<Mem0Message> convertToMem0Message(Msg msg) {
        String content = sanitize(msg.getTextContent());
        if (content.isEmpty() || content.contains("<compressed_history>")) {
            return java.util.Optional.empty();
        }

        String role = msg.getRole() == MsgRole.USER ? "user" : "assistant";
        return java.util.Optional.of(
                Mem0Message.builder().role(role).content(content).build());
    }

    private Mem0SearchRequest buildSearchRequest(String query) {
        Mem0SearchRequest.Builder builder =
                Mem0SearchRequest.builder().query(query).topK(Integer.valueOf(5));

        if (apiType == Mem0ApiType.SELF_HOSTED) {
            Map<String, Object> filters = new LinkedHashMap<>();
            if (userId != null && !userId.isBlank()) {
                filters.put("user_id", userId);
            }
            if (agentId != null && !agentId.isBlank()) {
                filters.put("agent_id", agentId);
            }
            if (runId != null && !runId.isBlank()) {
                filters.put("run_id", runId);
            }
            if (!metadata.isEmpty()) {
                filters.putAll(metadata);
            }
            builder.filters(filters);
            return builder.build();
        }

        builder.userId(userId).agentId(agentId).runId(runId);
        if (!metadata.isEmpty()) {
            builder.getFilters().putAll(metadata);
        }
        return builder.build();
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String sanitized =
                METADATA_TAG_PATTERN.matcher(text)
                        .replaceAll("")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .trim();
        if (sanitized.length() <= MAX_MEMORY_TEXT_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_MEMORY_TEXT_LENGTH).trim();
    }

    private static String joinResults(Mem0SearchResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return "";
        }
        return response.getResults().stream()
                .map(Mem0SearchResult::getMemory)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining("\n"));
    }
}
