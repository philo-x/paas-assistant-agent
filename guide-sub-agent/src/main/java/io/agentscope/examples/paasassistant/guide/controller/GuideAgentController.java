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

package io.agentscope.examples.paasassistant.guide.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.examples.paasassistant.common.config.AgentConstants;
import io.agentscope.examples.paasassistant.common.controller.dto.StructuredChatRequest;
import io.agentscope.examples.paasassistant.common.stream.EventStreamTranslator;
import io.agentscope.examples.paasassistant.common.stream.StructuredSseEmitter;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
@RequestMapping("/api/guide")
public class GuideAgentController {

    private static final Logger logger = LoggerFactory.getLogger(GuideAgentController.class);

    private final AgentRunner agentRunner;
    private final ObjectMapper objectMapper;

    public GuideAgentController(AgentRunner agentRunner) {
        this.agentRunner = agentRunner;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(value = "/chat/structured", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> structuredChat(@RequestBody StructuredChatRequest request) {
        String userQuery = request.user_query();
        String userId = request.user_id();

        try {
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
            StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
            String traceId = request.chat_id() != null && !request.chat_id().isEmpty() 
                    ? request.chat_id() : UUID.randomUUID().toString();

            StringBuilder promptBuilder = new StringBuilder();
            if (userId != null && !userId.isEmpty()) {
                promptBuilder.append(AgentConstants.TAG_USER_ID_START).append(userId).append(AgentConstants.TAG_USER_ID_END);
            }
            if (request.cluster_id() != null && !request.cluster_id().isEmpty()) {
                promptBuilder.append(AgentConstants.TAG_CLUSTER_ID_START).append(request.cluster_id()).append(AgentConstants.TAG_CLUSTER_ID_END);
            }
            promptBuilder.append(AgentConstants.TAG_TRACE_ID_START).append(traceId).append(AgentConstants.TAG_TRACE_ID_END);
            promptBuilder.append(userQuery);

            Msg msg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(promptBuilder.toString()).build())
                    .build();

            AgentRequestOptions options = new AgentRequestOptions();
            options.setTaskId(traceId);

            EventStreamTranslator translator = new EventStreamTranslator(AgentConstants.AGENT_NAME_GUIDE, emitter);

            Disposable disposable = agentRunner.stream(Collections.singletonList(msg), options)
                    .doOnNext(translator::handleEvent)
                    .doOnError(e -> {
                        logger.error("Error occurred during structured streaming", e);
                        emitter.emitError(AgentConstants.SYSTEM_ERROR_MESSAGE, AgentConstants.SSE_EVENT_STAGE_STREAM);
                        sink.tryEmitComplete();
                    })
                    .doOnComplete(() -> {
                        translator.emitFinalAnswer();
                        emitter.emitDone();
                        sink.tryEmitComplete();
                    })
                    .subscribe();

            Flux<ServerSentEvent<String>> flux = sink.asFlux()
                    .publish(shared -> Flux.merge(
                            shared,
                            Flux.interval(Duration.ofSeconds(AgentConstants.KEEP_ALIVE_INTERVAL_SECONDS))
                                    .onBackpressureDrop()
                                    .map(i -> ServerSentEvent.<String>builder()
                                            .comment(AgentConstants.KEEP_ALIVE_COMMENT)
                                            .build())
                                    .takeUntilOther(shared.ignoreElements())
                    ))
                    .doOnCancel(() -> {
                        logger.warn("Client disconnected from structured stream (traceId={}), cancelling agent task.", traceId);
                        disposable.dispose();
                        agentRunner.stop(traceId);
                    })
                    .doFinally(signalType -> {
                        disposable.dispose();
                        agentRunner.stop(traceId);
                    });

            return ResponseEntity.ok()
                    .header(AgentConstants.HTTP_HEADER_X_ACCEL_BUFFERING, AgentConstants.HTTP_HEADER_VALUE_NO)
                    .header(AgentConstants.HTTP_HEADER_CACHE_CONTROL, AgentConstants.HTTP_HEADER_VALUE_NO_CACHE)
                    .header(AgentConstants.HTTP_HEADER_CONNECTION, AgentConstants.HTTP_HEADER_VALUE_KEEP_ALIVE)
                    .body(flux);
        } catch (Exception e) {
            logger.error("Failed to process structured user query: {}", userQuery, e);
            return ResponseEntity.ok()
                    .header(AgentConstants.HTTP_HEADER_X_ACCEL_BUFFERING, AgentConstants.HTTP_HEADER_VALUE_NO)
                    .header(AgentConstants.HTTP_HEADER_CACHE_CONTROL, AgentConstants.HTTP_HEADER_VALUE_NO_CACHE)
                    .header(AgentConstants.HTTP_HEADER_CONNECTION, AgentConstants.HTTP_HEADER_VALUE_KEEP_ALIVE)
                    .body(Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event(AgentConstants.SSE_EVENT_ERROR)
                                    .data(String.format(AgentConstants.SYSTEM_ERROR_JSON_FORMAT, AgentConstants.SYSTEM_ERROR_MESSAGE, AgentConstants.SSE_EVENT_STAGE_REQUEST))
                                    .build()));
        }
    }
}
