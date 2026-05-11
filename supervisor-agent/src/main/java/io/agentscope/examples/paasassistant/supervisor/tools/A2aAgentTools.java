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
import io.agentscope.core.a2a.agent.message.MessageAssembler;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredTraceRegistry;
import io.agentscope.examples.paasassistant.supervisor.stream.ToolNarrator;
import io.agentscope.examples.paasassistant.supervisor.utils.AgentConstants;
import io.agentscope.examples.paasassistant.supervisor.utils.MsgUtils;
import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import reactor.util.retry.Retry;

@Component
public class A2aAgentTools {

    private static final Logger log = LoggerFactory.getLogger(A2aAgentTools.class);

    /**
     * StreamOptions the supervisor requests from each child A2A agent.
     *
     * <p>MUST stay byte-for-byte in sync with each sub-agent's {@code FULL_STREAM_OPTIONS} in
     * {@code diagnosis-sub-agent} and {@code guide-sub-agent}. Drift here causes either silent
     * event loss (sub-agent sends events the supervisor doesn't expect) or missing UI updates
     * (supervisor requests events the sub-agent isn't configured to emit).
     *
     * <p>{@code includeActingChunk(false)}: streaming partial tool input/output is unused by the
     * structured timeline, and fragmented chunks would force per-blockId dedup. The dedup
     * defense in {@code emitToolSteps} is kept as belt-and-suspenders, but with this flag off
     * the framework should already be sending one TOOL_RESULT per tool call.
     */
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

    private final Duration childAgentTimeout;

    private final int childAgentRetryAttempts;

    private final Duration childAgentRetryBackoff;

    public A2aAgentTools(
            @Qualifier("guideAgent") ObjectProvider<A2aAgent> guideAgentProvider,
            @Qualifier("diagnosisAgent") ObjectProvider<A2aAgent> diagnosisAgentProvider,
            StructuredTraceRegistry traceRegistry,
            @Value("${agent.a2a.child-agent-timeout:PT10M}") Duration childAgentTimeout,
            @Value("${agent.a2a.child-agent-retry-attempts:3}") int childAgentRetryAttempts,
            @Value("${agent.a2a.child-agent-retry-backoff:PT0.2S}") Duration childAgentRetryBackoff) {
        this.guideAgentProvider = guideAgentProvider;
        this.diagnosisAgentProvider = diagnosisAgentProvider;
        this.traceRegistry = traceRegistry;
        this.childAgentTimeout = childAgentTimeout;
        this.childAgentRetryAttempts = Math.max(1, childAgentRetryAttempts);
        this.childAgentRetryBackoff = childAgentRetryBackoff;
    }

    @Tool(
            description =
                    "Route read-only Kubernetes explanation requests to the guide agent."
                            + " Pass the full conversational context.")
    public Mono<String> callGuideAgent(
            @ToolParam(name = "context", description = "Complete context") String context) {
        final String originalContext = context;
        return Mono.deferContextual(ctx -> {
            String clusterId = ctx.getOrDefault(AgentConstants.CTX_CLUSTER_ID, "");
            String userId = ctx.getOrDefault(AgentConstants.CTX_USER_ID, "");
            String traceId = ctx.getOrDefault(AgentConstants.CTX_TRACE_ID, "");

            StringBuilder builder = new StringBuilder();
            if (userId != null && !userId.isEmpty()) {
                builder.append("<").append(AgentConstants.TAG_USER_ID).append(">").append(userId).append("</").append(AgentConstants.TAG_USER_ID).append(">");
            }
            if (clusterId != null && !clusterId.isEmpty()) {
                builder.append("<").append(AgentConstants.TAG_CLUSTER_ID).append(">").append(clusterId).append("</").append(AgentConstants.TAG_CLUSTER_ID).append(">");
            }
            if (traceId != null && !traceId.isEmpty()) {
                builder.append("<").append(AgentConstants.TAG_TRACE_ID).append(">").append(traceId).append("</").append(AgentConstants.TAG_TRACE_ID).append(">");
            }
            builder.append(originalContext);
            
            String finalContext = builder.toString();
            Msg msg = Msg.builder().content(TextBlock.builder().text(finalContext).build()).build();
            A2aAgent guideAgent = guideAgentProvider.getObject();
            return callChildAgent(guideAgent, AgentConstants.AGENT_NAME_GUIDE, msg, traceId);
        });
    }

    @Tool(
            description =
                    "Route Kubernetes diagnosis, incident and controlled change requests to the diagnosis agent."
                            + " Pass the full conversational context.")
    public Mono<String> callDiagnosisAgent(
            @ToolParam(name = "context", description = "Complete context") String context) {
        final String originalContext = context;
        return Mono.deferContextual(ctx -> {
            String clusterId = ctx.getOrDefault(AgentConstants.CTX_CLUSTER_ID, "");
            String userId = ctx.getOrDefault(AgentConstants.CTX_USER_ID, "");
            String traceId = ctx.getOrDefault(AgentConstants.CTX_TRACE_ID, "");

            StringBuilder builder = new StringBuilder();
            if (userId != null && !userId.isEmpty()) {
                builder.append("<").append(AgentConstants.TAG_USER_ID).append(">").append(userId).append("</").append(AgentConstants.TAG_USER_ID).append(">");
            }
            if (clusterId != null && !clusterId.isEmpty()) {
                builder.append("<").append(AgentConstants.TAG_CLUSTER_ID).append(">").append(clusterId).append("</").append(AgentConstants.TAG_CLUSTER_ID).append(">");
            }
            if (traceId != null && !traceId.isEmpty()) {
                builder.append("<").append(AgentConstants.TAG_TRACE_ID).append(">").append(traceId).append("</").append(AgentConstants.TAG_TRACE_ID).append(">");
            }
            builder.append(originalContext);

            String finalContext = builder.toString();
            Msg msg = Msg.builder().content(TextBlock.builder().text(finalContext).build()).build();
            A2aAgent diagnosisAgent = diagnosisAgentProvider.getObject();
            return callChildAgent(diagnosisAgent, AgentConstants.AGENT_NAME_DIAGNOSIS, msg, traceId);
        });
    }

    private Mono<String> callChildAgent(
            A2aAgent agent, String childAgentName, Msg msg, String traceId) {

        Mono<String> childCall =
                Mono.deferContextual(
                                ctxView -> {
                                    StructuredSseEmitter emitter =
                                            resolveEmitter(traceId, ctxView);
                                    StringBuilder finalAnswer = new StringBuilder();
                                    ChildStreamState state = new ChildStreamState();
                                    return agent.stream(msg, CHILD_AGENT_STREAM_OPTIONS)
                                             .doOnNext(
                                                     event ->
                                                             handleChildEvent(
                                                                     childAgentName,
                                                                     event,
                                                                     emitter,
                                                                     finalAnswer,
                                                                     state))
                                             .then(Mono.fromSupplier(() -> {
                                                 String complete = finalAnswer.toString().trim();
                                                 emitFinalAnswerOnce(
                                                         childAgentName, complete, emitter, state);
                                                 return complete;
                                             }))
                                             .doFinally(sig -> state.getAssembler().clear());
                                })
                        .timeout(childAgentTimeout);

        return applyTransientRetry(childCall, childAgentName, traceId)
                .onErrorResume(
                        throwable ->
                                Mono.deferContextual(
                                        ctxView ->
                                                handleChildAgentFailure(
                                                        throwable,
                                                        agent,
                                                        childAgentName,
                                                        traceId,
                                                        resolveEmitter(traceId, ctxView))));
    }

    private Mono<String> applyTransientRetry(
            Mono<String> childCall, String childAgentName, String traceId) {
        if (childAgentRetryAttempts <= 1) {
            return childCall;
        }
        return childCall.retryWhen(
                Retry.fixedDelay(childAgentRetryAttempts - 1, childAgentRetryBackoff)
                        .filter(A2aAgentTools::isTransientA2aTransportFailure)
                        .doBeforeRetry(
                                signal ->
                                        log.warn(
                                                "Transient A2A streaming error from {}. Retrying stream call. failedAttempt={}/{} nextAttempt={} traceId={}",
                                                childAgentName,
                                                signal.totalRetriesInARow() + 1,
                                                childAgentRetryAttempts,
                                                signal.totalRetriesInARow() + 2,
                                                traceId,
                                                signal.failure())));
    }

    private Mono<String> handleChildAgentFailure(
            Throwable throwable,
            A2aAgent agent,
            String childAgentName,
            String traceId,
            StructuredSseEmitter emitter) {
        if (isTimeoutFailure(throwable)) {
            log.error(
                    "A2A streaming call to {} timed out after {}. traceId={}",
                    childAgentName,
                    childAgentTimeout,
                    traceId,
                    throwable);
            interruptChildAgent(agent, childAgentName, traceId);
            emitChildAgentError(emitter, childAgentName, AgentConstants.CHILD_AGENT_TIMEOUT_MESSAGE);
            return Mono.just(AgentConstants.CHILD_AGENT_TIMEOUT_MESSAGE);
        }

        if (isInterruptedFailure(throwable)) {
            log.warn(
                    "A2A streaming call to {} was interrupted. traceId={}",
                    childAgentName,
                    traceId,
                    throwable);
            emitChildAgentError(emitter, childAgentName, AgentConstants.CHILD_AGENT_UNAVAILABLE_MESSAGE);
            return Mono.just(AgentConstants.CHILD_AGENT_UNAVAILABLE_MESSAGE);
        }

        log.error("A2A streaming call to {} failed. traceId={}", childAgentName, traceId, throwable);
        emitChildAgentError(emitter, childAgentName, AgentConstants.CHILD_AGENT_UNAVAILABLE_MESSAGE);
        return Mono.just(AgentConstants.CHILD_AGENT_UNAVAILABLE_MESSAGE);
    }

    private static void emitChildAgentError(
            StructuredSseEmitter emitter, String childAgentName, String message) {
        if (emitter != null) {
            emitter.emitError(message, childAgentName);
        }
    }

    static boolean shouldRetryChildAgentCall(RuntimeException ex, int attempt) {
        return attempt < 3 && isTransientA2aTransportFailure(ex);
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

    static boolean isTimeoutFailure(Throwable throwable) {
        return findCause(throwable, TimeoutException.class) != null;
    }

    static boolean isInterruptedFailure(Throwable throwable) {
        return findCause(throwable, InterruptedException.class) != null;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = Exceptions.unwrap(throwable);
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
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

    private void interruptChildAgent(A2aAgent agent, String childAgentName, String traceId) {
        try {
            agent.interrupt();
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to interrupt A2A task for {} after timeout. traceId={}",
                    childAgentName,
                    traceId,
                    ex);
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

        EventType type = event.getType();

        // Process reasoning (model thinking, text, and tool calls)
        if (type == EventType.REASONING) {
            boolean emittedToolStep = emitToolSteps(childAgentName, event.getMessage(), emitter, state);
            if (emittedToolStep) {
                state.markStepEmitted();
            }

            // Also emit thinking content if present (common in REASONING events)
            for (ThinkingBlock block : event.getMessage().getContentBlocks(ThinkingBlock.class)) {
                String thinking = ToolNarrator.extractThinkingChunk(block.getThinking());
                if (!thinking.isEmpty()) {
                    if (emitter != null) {
                        emitter.emitReasoningDelta(childAgentName, thinking);
                    }
                    state.markStepEmitted();
                }
            }

            // Fallback to text blocks if no thinking block was found or in addition to it
            for (TextBlock block : event.getMessage().getContentBlocks(TextBlock.class)) {
                String text = ToolNarrator.extractThinkingChunk(block.getText());
                if (!text.isEmpty()) {
                    if (emitter != null) {
                        emitter.emitReasoningDelta(childAgentName, text);
                    }
                    state.markStepEmitted();
                }
            }
            return;
        }

        // Process tool execution results
        if (type == EventType.TOOL_RESULT) {
            boolean emittedToolStep = emitToolSteps(childAgentName, event.getMessage(), emitter, state);
            if (emittedToolStep) {
                state.markStepEmitted();
            }
            return;
        }

        // Process final agent answer.
        // We DO NOT emit here, because AGENT_RESULT can fire multiple times under
        // incremental(true): emitting on the first event would lock `answerEmitted` and
        // ship only the first fragment to the user. Accumulate the text now and let the
        // stream-end hook in callChildAgent emit the fully-assembled answer exactly once.
        if (type == EventType.AGENT_RESULT) {
            event.getMessage()
                    .getContentBlocks(TextBlock.class)
                    .forEach(block -> finalAnswer.append(block.getText()));
            return;
        }
    }

    /**
     * Emits the child agent's complete final answer to the structured SSE stream exactly once,
     * after the upstream Flux has finished accumulating it. Earlier in this file the AGENT_RESULT
     * branch only appends to {@code finalAnswer}; this method is the sole point that pushes the
     * assembled text out to the user, so we never ship a truncated first-fragment to the UI.
     */
    private void emitFinalAnswerOnce(
            String childAgentName,
            String completeAnswer,
            StructuredSseEmitter emitter,
            ChildStreamState state) {
        if (emitter == null || state.hasAnswerEmitted() || completeAnswer == null
                || completeAnswer.isEmpty()) {
            return;
        }
        String summary = ToolNarrator.extractThinkingText(completeAnswer);
        if (summary.isEmpty()) {
            return;
        }
        emitter.emitReasoningDelta(childAgentName, summary);
        state.markAnswerEmitted();
    }

    private boolean emitToolSteps(
            String childAgentName, Msg message, StructuredSseEmitter emitter, ChildStreamState state) {
        if (message == null || emitter == null) {
            return false;
        }

        boolean emitted = false;

        // Process tool calls (starts).
        // The framework streams ToolUseBlocks in fragments (assembled by MessageAssembler);
        // we only want to fire `tool_start` once per logical tool call.
        for (ToolUseBlock block : message.getContentBlocks(ToolUseBlock.class)) {
            ContentBlock assembled = state.getAssembler().assemble(block.getId(), block);
            if (!(assembled instanceof ToolUseBlock finalBlock)) {
                continue;
            }
            if (!state.tryMarkToolStartEmitted(finalBlock.getId())) {
                continue;
            }

            String toolName = finalBlock.getName();
            Object inputObj = finalBlock.getInput();
            String inputSummary = "";
            if (inputObj != null) {
                try {
                    inputSummary = JsonUtils.getJsonCodec().toJson(inputObj);
                } catch (Exception e) {
                    inputSummary = inputObj.toString();
                }
            } else if (finalBlock.getContent() != null) {
                inputSummary = finalBlock.getContent();
            }
            emitter.emitToolStart(childAgentName, toolName, inputSummary);
            emitted = true;
        }

        // Process tool results (completions). Same dedup pattern as ToolUseBlock above.
        for (ToolResultBlock block : message.getContentBlocks(ToolResultBlock.class)) {
            ContentBlock assembled = state.getAssembler().assemble(block.getId(), block);
            if (!(assembled instanceof ToolResultBlock finalBlock)) {
                continue;
            }
            if (!state.tryMarkToolResultEmitted(finalBlock.getId())) {
                continue;
            }

            String toolName =
                    (finalBlock.getName() == null || finalBlock.getName().isBlank())
                            ? "unknown_tool"
                            : finalBlock.getName();

            String title = ToolNarrator.titleForTool(toolName);
            String summary = "已完成" + title + "。";

            emitter.emitToolResult(
                    childAgentName,
                    toolName,
                    "success",
                    summary,
                    "");
            emitted = true;
        }

        return emitted;
    }

    /**
     * Resolves the per-request structured SSE emitter without ever falling back across users.
     *
     * <p>Lookup order:
     *
     * <ol>
     *   <li>Reactor Context — populated by {@code SupervisorAgentController} for every structured
     *       request. This is automatically isolated per HTTP subscription.
     *   <li>Exact traceId match in the registry — used when Reactor Context did not propagate
     *       through the framework's tool-dispatch boundary but the LLM kept the {@code <traceId>}
     *       tag intact.
     *   <li>{@code null} — child-agent events will be dropped from the SSE stream rather than
     *       leaked to another user's connection.
     * </ol>
     */
    StructuredSseEmitter resolveEmitter(String traceId, ContextView ctxView) {
        if (ctxView != null && ctxView.hasKey(StructuredSseEmitter.CONTEXT_KEY)) {
            StructuredSseEmitter fromContext = ctxView.get(StructuredSseEmitter.CONTEXT_KEY);
            if (fromContext != null) {
                return fromContext;
            }
        }

        StructuredSseEmitter fromRegistry = traceRegistry.get(traceId);
        if (fromRegistry != null) {
            return fromRegistry;
        }

        log.warn(
                "No structured SSE emitter for traceId={}; child-agent events will be dropped to avoid cross-user leakage.",
                traceId);
        return null;
    }



    private static final class ChildStreamState {
        private boolean stepEmitted;
        private boolean answerEmitted;
        private final MessageAssembler assembler = new MessageAssembler();
        private final java.util.Set<String> emittedToolStartIds = new java.util.HashSet<>();
        private final java.util.Set<String> emittedToolResultIds = new java.util.HashSet<>();

        public MessageAssembler getAssembler() {
            return assembler;
        }

        /**
         * Returns true the first time a ToolUseBlock with this id is observed; subsequent
         * fragments of the same logical block return false so the SSE timeline only fires
         * one tool_start per tool call.
         */
        private boolean tryMarkToolStartEmitted(String blockId) {
            if (blockId == null || blockId.isBlank()) {
                return true;
            }
            return emittedToolStartIds.add(blockId);
        }

        /** Same idea as {@link #tryMarkToolStartEmitted} but for ToolResultBlock. */
        private boolean tryMarkToolResultEmitted(String blockId) {
            if (blockId == null || blockId.isBlank()) {
                return true;
            }
            return emittedToolResultIds.add(blockId);
        }

        private void markStepEmitted() {
            this.stepEmitted = true;
        }

        private boolean hasStepEmitted() {
            return stepEmitted;
        }

        private void markAnswerEmitted() {
            this.answerEmitted = true;
        }

        private boolean hasAnswerEmitted() {
            return answerEmitted;
        }
    }
}
