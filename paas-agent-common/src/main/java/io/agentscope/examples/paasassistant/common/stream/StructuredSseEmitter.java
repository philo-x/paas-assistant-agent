/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.examples.paasassistant.common.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.examples.paasassistant.common.config.AgentConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

public class StructuredSseEmitter {

    private static final Logger logger = LoggerFactory.getLogger(StructuredSseEmitter.class);

    /**
     * Reactor Context key that carries the per-request emitter through the supervisor's reactive
     * pipeline into tool invocations. Each HTTP request has its own subscription context, so
     * lookup by this key is automatically isolated across concurrent users.
     */
    public static final String CONTEXT_KEY = AgentConstants.CTX_SSE_EMITTER;

    private final Sinks.Many<ServerSentEvent<String>> sink;

    private final ObjectMapper objectMapper;

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, String> danglingSurrogates = new java.util.concurrent.ConcurrentHashMap<>();

    public StructuredSseEmitter(
            Sinks.Many<ServerSentEvent<String>> sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    private String fixSurrogates(String key, String text) {
        if (text == null || text.isEmpty()) return text;
        String dangling = danglingSurrogates.remove(key);
        if (dangling != null) {
            text = dangling + text;
        }
        if (text.length() > 0 && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
            danglingSurrogates.put(key, text.substring(text.length() - 1));
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    public void emitReasoningDelta(String agent, String text) {
        text = fixSurrogates("reasoning_" + agent, text);
        if (text.isEmpty()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("text", text);
        emit("reasoning_delta", payload);
    }

    public void emitToolStart(String agent, String tool, String inputSummary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("tool", tool);
        payload.put("title", ToolNarrator.titleForTool(tool));
        payload.put("delegation", ToolNarrator.isDelegationTool(tool));
        payload.put("inputSummary", inputSummary);
        payload.put("summary", ToolNarrator.summarizeToolStart(agent, tool, inputSummary));
        emit("tool_start", payload);
    }

    public void emitToolResult(
            String agent, String tool, String status, String summary, String inputSummary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("tool", tool);
        payload.put("title", ToolNarrator.titleForTool(tool));
        payload.put("delegation", ToolNarrator.isDelegationTool(tool));
        payload.put("status", status);
        payload.put("summary", summary);
        payload.put("inputSummary", inputSummary);
        emit("tool_result", payload);
    }

    public void emitAnswerDelta(String text) {
        text = fixSurrogates("answer", text);
        if (text.isEmpty()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        emit("answer_delta", payload);
    }

    public void emitDone() {
        emit("done", Map.of("status", "completed"));
    }

    public void emitError(String message, String stage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("stage", stage);
        emit("error", payload);
    }

    private void emit(String eventName, Map<String, Object> payload) {
        try {
            Map<String, Object> enrichedPayload = new LinkedHashMap<>(payload);
            enrichedPayload.put("sequence", sequence.getAndIncrement());
            String json = objectMapper.writeValueAsString(enrichedPayload);
            sink.tryEmitNext(ServerSentEvent.<String>builder().event(eventName).data(json).build());
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize SSE payload for event {}", eventName, e);
            sink.tryEmitNext(
                    ServerSentEvent.<String>builder()
                            .event("error")
                            .data("{\"message\":\"Failed to serialize SSE payload\",\"stage\":\"serialization\"}")
                            .build());
        }
    }
}
