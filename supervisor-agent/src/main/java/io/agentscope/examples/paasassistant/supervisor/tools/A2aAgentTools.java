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

package io.agentscope.examples.paasassistant.supervisor.tools;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredTraceRegistry;
import io.agentscope.examples.paasassistant.supervisor.stream.ToolNarrator;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class A2aAgentTools {

    private static final Logger log = LoggerFactory.getLogger(A2aAgentTools.class);

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("<traceId>(.+?)</traceId>");

    private static final int CHILD_AGENT_MAX_ATTEMPTS = 3;

    private static final long CHILD_AGENT_RETRY_BACKOFF_MILLIS = 200L;

    private static final String CHILD_AGENT_UNAVAILABLE_MESSAGE =
            "子 Agent 暂时不可用，A2A 流式响应在读取过程中中断。请稍后重试；如果持续出现，请检查子 Agent 服务状态和网络连接。";

    private static final StreamOptions CHILD_AGENT_STREAM_OPTIONS =
            StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.AGENT_RESULT)
                    .incremental(true)
                    .includeReasoningChunk(true)
                    .includeReasoningResult(false)
                    .includeActingChunk(false)
                    .includeSummaryChunk(false)
                    .includeSummaryResult(false)
                    .build();

    private final ObjectProvider<A2aAgent> guideAgentProvider;

    private final ObjectProvider<A2aAgent> diagnosisAgentProvider;

    private final StructuredTraceRegistry traceRegistry;

    public A2aAgentTools(
            @Qualifier("guideAgent") ObjectProvider<A2aAgent> guideAgentProvider,
            @Qualifier("diagnosisAgent") ObjectProvider<A2aAgent> diagnosisAgentProvider,
            StructuredTraceRegistry traceRegistry) {
        this.guideAgentProvider = guideAgentProvider;
        this.diagnosisAgentProvider = diagnosisAgentProvider;
        this.traceRegistry = traceRegistry;
    }

    @Tool(
            description =
                    "Route read-only Kubernetes explanation requests to the guide agent."
                            + " Pass the full conversational context.")
    public String callGuideAgent(
            @ToolParam(name = "context", description = "Complete context") String context,
            @ToolParam(name = "userId", description = "User's UserId") String userId) {
        context = "<userId>" + userId + "</userId>" + context;
        Msg msg = Msg.builder().content(TextBlock.builder().text(context).build()).build();
        A2aAgent guideAgent = guideAgentProvider.getObject();
        return callChildAgent(guideAgent, "guide_agent", msg, context);
    }

    @Tool(
            description =
                    "Route Kubernetes diagnosis, incident and controlled change requests to the diagnosis agent."
                            + " Pass the full conversational context.")
    public String callDiagnosisAgent(
            @ToolParam(name = "context", description = "Complete context") String context,
            @ToolParam(name = "userId", description = "User's UserId") String userId) {
        context = "<userId>" + userId + "</userId>" + context;
        Msg msg = Msg.builder().content(TextBlock.builder().text(context).build()).build();
        A2aAgent diagnosisAgent = diagnosisAgentProvider.getObject();
        return callChildAgent(diagnosisAgent, "diagnosis_agent", msg, context);
    }

    private String callChildAgent(
            A2aAgent agent, String childAgentName, Msg msg, String contextWithTags) {
        StructuredSseEmitter emitter = resolveEmitter(contextWithTags);

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= CHILD_AGENT_MAX_ATTEMPTS; attempt++) {
            StringBuilder finalAnswer = new StringBuilder();
            ChildStreamState state = new ChildStreamState();
            try {
                agent.stream(msg, CHILD_AGENT_STREAM_OPTIONS)
                        .doOnNext(
                                event ->
                                        handleChildEvent(
                                                childAgentName,
                                                event,
                                                emitter,
                                                finalAnswer,
                                                state))
                        .collectList()
                        .block();
                return finalAnswer.toString().trim();
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (!shouldRetryChildAgentCall(ex, attempt)) {
                    break;
                }
                log.warn(
                        "Transient A2A streaming error from {}. Retrying stream call. attempt={}/{}",
                        childAgentName,
                        attempt,
                        CHILD_AGENT_MAX_ATTEMPTS,
                        ex);
                sleepBeforeRetry();
            }
        }

        log.error("A2A streaming call to {} failed.", childAgentName, lastFailure);
        if (emitter != null) {
            emitter.emitError(CHILD_AGENT_UNAVAILABLE_MESSAGE, childAgentName);
        }
        return CHILD_AGENT_UNAVAILABLE_MESSAGE;
    }

    static boolean shouldRetryChildAgentCall(RuntimeException ex, int attempt) {
        return attempt < CHILD_AGENT_MAX_ATTEMPTS && isTransientA2aTransportFailure(ex);
    }

    static boolean isTransientA2aTransportFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }
            if (current instanceof IOException && hasChunkedTransferMessage(current)) {
                return true;
            }
            if (hasChunkedTransferMessage(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasChunkedTransferMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("chunked transfer encoding")
                || message.contains("READING_LENGTH")
                || message.contains("EOF reached while reading");
    }

    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(CHILD_AGENT_RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleChildEvent(
            String childAgentName,
            Event event,
            StructuredSseEmitter emitter,
            StringBuilder finalAnswer,
            ChildStreamState state) {
        if (event == null || event.getMessage() == null) {
            return;
        }

        if (event.getType() == EventType.REASONING) {
            boolean emittedToolStep = emitToolResultBlocks(childAgentName, event.getMessage(), emitter);
            if (emittedToolStep) {
                state.markStepEmitted();
            }
            String thinking = event.getMessage().getContentBlocks(ThinkingBlock.class).stream()
                    .map(ThinkingBlock::getThinking)
                    .reduce("", String::concat);
            thinking = ToolNarrator.extractThinkingChunk(thinking);
            if (!thinking.isEmpty()) {
                if (emitter != null) {
                    emitter.emitReasoningDelta(childAgentName, thinking);
                }
                state.markStepEmitted();
                return;
            }

            if (!emittedToolStep) {
                String text = event.getMessage().getContentBlocks(TextBlock.class).stream()
                        .map(TextBlock::getText)
                        .reduce("", String::concat);
                text = ToolNarrator.extractThinkingChunk(text);
                if (!text.isEmpty()) {
                    if (emitter != null) {
                        emitter.emitReasoningDelta(childAgentName, text);
                    }
                    state.markStepEmitted();
                }
            }
            return;
        }

        if (event.getType() == EventType.TOOL_RESULT) {
            if (emitToolResultBlocks(childAgentName, event.getMessage(), emitter)) {
                state.markStepEmitted();
            }
            return;
        }

        if (event.getType() == EventType.AGENT_RESULT) {
            event.getMessage().getContentBlocks(TextBlock.class).forEach(block -> finalAnswer.append(block.getText()));
            if (!state.hasStepEmitted()) {
                String summary = ToolNarrator.extractThinkingText(finalAnswer.toString());
                if (!summary.isEmpty()) {
                    if (emitter != null) {
                        emitter.emitReasoningDelta(childAgentName, summary);
                    }
                    state.markStepEmitted();
                }
            }
        }
    }

    private boolean emitToolResultBlocks(
            String childAgentName, Msg message, StructuredSseEmitter emitter) {
        if (message == null || emitter == null) {
            return false;
        }

        boolean emitted = false;
        for (ToolResultBlock block : message.getContentBlocks(ToolResultBlock.class)) {
            String toolName =
                    (block.getName() == null || block.getName().isBlank())
                            ? "unknown_tool"
                            : block.getName();
            String outputSummary = block.getOutput().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(TextBlock.class::cast)
                    .map(TextBlock::getText)
                    .reduce("", String::concat);
            emitter.emitToolResult(
                    childAgentName,
                    toolName,
                    "success",
                    ToolNarrator.summarizeToolResult(
                            childAgentName, toolName, outputSummary, ""),
                    "");
            emitted = true;
        }

        // Support for synthetic ToolResult transported as TextBlock to bypass A2A framework bugs
        for (TextBlock textBlock : message.getContentBlocks(TextBlock.class)) {
            String text = textBlock.getText();
            if (text != null && text.startsWith("[SYNTHETIC_TOOL_RESULT]")) {
                String[] parts = text.split("\n", 2);
                String toolName = parts[0].substring("[SYNTHETIC_TOOL_RESULT]".length()).trim();
                String outputSummary = parts.length > 1 ? parts[1] : "";
                if (toolName.isBlank()) {
                    toolName = "unknown_tool";
                }
                emitter.emitToolResult(
                        childAgentName,
                        toolName,
                        "success",
                        ToolNarrator.summarizeToolResult(childAgentName, toolName, outputSummary, ""),
                        "");
                emitted = true;
            }
        }
        return emitted;
    }

    private StructuredSseEmitter resolveEmitter(String contextWithTags) {
        String traceId = extractTraceId(contextWithTags);
        StructuredSseEmitter emitter = traceRegistry.get(traceId);
        if (emitter != null) {
            return emitter;
        }
        return traceRegistry.getLatest();
    }

    private String extractTraceId(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        Matcher matcher = TRACE_ID_PATTERN.matcher(context);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private static final class ChildStreamState {

        private boolean stepEmitted;

        private void markStepEmitted() {
            this.stepEmitted = true;
        }

        private boolean hasStepEmitted() {
            return stepEmitted;
        }
    }
}
