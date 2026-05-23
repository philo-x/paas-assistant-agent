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

package io.agentscope.examples.paasassistant.diagnosis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
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
@RequestMapping("/api/diagnosis")
public class DiagnosisAgentController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosisAgentController.class);

    private final AgentRunner agentRunner;
    private final ObjectMapper objectMapper;

    public DiagnosisAgentController(AgentRunner agentRunner) {
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
                promptBuilder.append("<userId>").append(userId).append("</userId>");
            }
            if (request.cluster_id() != null && !request.cluster_id().isEmpty()) {
                promptBuilder.append("<clusterId>").append(request.cluster_id()).append("</clusterId>");
            }
            promptBuilder.append("<traceId>").append(traceId).append("</traceId>");
            promptBuilder.append(userQuery);

            Msg msg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(promptBuilder.toString()).build())
                    .build();

            AgentRequestOptions options = new AgentRequestOptions();
            options.setTaskId(traceId);

            EventStreamTranslator translator = new EventStreamTranslator("diagnosis_agent", emitter);

            Disposable disposable = agentRunner.stream(Collections.singletonList(msg), options)
                    .doOnNext(translator::handleEvent)
                    .doOnError(e -> {
                        logger.error("Error occurred during structured streaming", e);
                        emitter.emitError("System processing error, please try again later.", "stream");
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
                            Flux.interval(Duration.ofSeconds(15))
                                    .onBackpressureDrop()
                                    .map(i -> ServerSentEvent.<String>builder()
                                            .comment("keep-alive")
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
                    .header("X-Accel-Buffering", "no")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .body(flux);
        } catch (Exception e) {
            logger.error("Failed to process structured user query: {}", userQuery, e);
            return ResponseEntity.ok()
                    .header("X-Accel-Buffering", "no")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .body(Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data("{\"message\":\"System processing error, please try again later.\",\"stage\":\"request\"}")
                                    .build()));
        }
    }
}
