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

package io.agentscope.examples.paasassistant.supervisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.examples.paasassistant.supervisor.agent.SupervisorAgent;
import io.agentscope.examples.paasassistant.supervisor.controller.dto.StructuredChatRequest;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredStreamHook;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredTraceRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RequestMapping("/api/assistant/")
@RestController
public class SupervisorAgentController {

    private static final Logger logger = LoggerFactory.getLogger(SupervisorAgentController.class);

    private final SupervisorAgent supervisorAgent;

    private final ObjectMapper objectMapper;

    private final StructuredTraceRegistry traceRegistry;

    public SupervisorAgentController(
            SupervisorAgent supervisorAgent,
            ObjectMapper objectMapper,
            StructuredTraceRegistry traceRegistry) {
        this.supervisorAgent = supervisorAgent;
        this.objectMapper = objectMapper;
        this.traceRegistry = traceRegistry;
    }

    @PostMapping(
            path = "/chat/structured",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> structuredChat(@RequestBody StructuredChatRequest request) {
        String chatId = safe(request.chat_id());
        String userId = safe(request.user_id());
        String userQuery = safe(request.user_query());

        logger.info("Received structured user query: {}", userQuery);

        if (chatId.isBlank() || userId.isBlank() || userQuery.isBlank()) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error")
                            .data(
                                    "{\"message\":\"chat_id, user_id and user_query are required\",\"stage\":\"validation\"}")
                            .build());
        }

        try {
            Sinks.Many<ServerSentEvent<String>> sink =
                    Sinks.many().unicast().onBackpressureBuffer();
            StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
            String traceId = UUID.randomUUID().toString();
            traceRegistry.register(traceId, emitter);

            Msg msg =
                    Msg.builder()
                            .role(MsgRole.USER)
                            .content(
                                    TextBlock.builder()
                                            .text(
                                                    formatStructuredUserInput(
                                                            userQuery, userId, traceId))
                                            .build())
                            .build();

            processStructuredStream(
                    supervisorAgent
                            .stream(
                                    msg,
                                    chatId,
                                    userId,
                                    new StructuredStreamHook("supervisor_agent", emitter))
                            .contextWrite(ctx -> ctx.put(StructuredSseEmitter.CONTEXT_KEY, emitter)),
                    sink,
                    emitter);

            return sink.asFlux()
                    .doOnCancel(() -> logger.info("Client disconnected from structured stream"))
                    .doOnError(e -> logger.error("Error occurred during structured streaming", e))
                    .doFinally(signalType -> traceRegistry.unregister(traceId));
        } catch (Exception e) {
            logger.error("Failed to process structured user query: {}", userQuery, e);
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error")
                            .data(
                                    "{\"message\":\"System processing error, please try again later.\",\"stage\":\"request\"}")
                            .build());
        }
    }

    public void processStructuredStream(
            Flux<Event> generator,
            Sinks.Many<ServerSentEvent<String>> sink,
            StructuredSseEmitter emitter) {
        generator
                .filter(event -> !event.isLast())
                .map(
                        event ->
                                event.getMessage().getContent().stream()
                                        .filter(block -> block instanceof TextBlock)
                                        .map(block -> ((TextBlock) block).getText())
                                        .toList())
                .flatMap(Flux::fromIterable)
                .filter(content -> content != null && !content.isBlank())
                .doOnNext(emitter::emitAnswerDelta)
                .doOnError(
                        e -> {
                            logger.error(
                                    "Unexpected error in structured stream processing: {}",
                                    e.getMessage(),
                                    e);
                            emitter.emitError(
                                    "System processing error, please try again later.",
                                    "stream");
                        })
                .doOnComplete(
                        () -> {
                            logger.info("Structured stream processing completed successfully");
                            emitter.emitDone();
                            sink.tryEmitComplete();
                        })
                .subscribe(
                        null,
                        e -> {
                            logger.error(
                                    "Structured stream processing failed: {}", e.getMessage(), e);
                            emitter.emitError(
                                    "System processing error, please try again later.",
                                    "stream");
                            sink.tryEmitComplete();
                        });
    }

    private String formatStructuredUserInput(String userQuery, String userId, String traceId) {
        StringBuilder builder = new StringBuilder();
        builder.append("本轮请求是唯一需要路由和处理的用户请求；历史记录只能作为参考，不能替换本轮问题。\n\n");
        builder.append("本轮用户问题:\n").append(userQuery).append('\n');
        builder.append("<traceId>").append(traceId).append("</traceId>\n");
        builder.append("<userId>").append(userId).append("</userId>");
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
