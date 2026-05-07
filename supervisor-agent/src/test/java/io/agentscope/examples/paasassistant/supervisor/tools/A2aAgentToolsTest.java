package io.agentscope.examples.paasassistant.supervisor.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredTraceRegistry;
import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

class A2aAgentToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsToolResultExactlyOnceEvenWhenFrameworkChunksItAcrossMultipleEvents() {
        // The framework can fragment a single ToolResultBlock across multiple TOOL_RESULT
        // events under incremental(true) + includeActingChunk(true). The MessageAssembler
        // accumulates the fragments, but emitToolSteps used to fire emitToolResult on
        // every assembled snapshot, producing duplicate frontend rows. Verify dedup by id.
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        registry.register("trace-dedup-result", emitter);

        Event chunk1 =
                new Event(
                        EventType.TOOL_RESULT,
                        Msg.builder()
                                .content(
                                        new ToolResultBlock(
                                                "tool-1",
                                                "resource-list",
                                                List.of(
                                                        TextBlock.builder()
                                                                .text("found 3")
                                                                .build())))
                                .build(),
                        false);
        Event chunk2 =
                new Event(
                        EventType.TOOL_RESULT,
                        Msg.builder()
                                .content(
                                        new ToolResultBlock(
                                                "tool-1",
                                                "resource-list",
                                                List.of(
                                                        TextBlock.builder()
                                                                .text(" pods")
                                                                .build())))
                                .build(),
                        false);

        A2aAgent guideAgent = org.mockito.Mockito.mock(A2aAgent.class);
        when(guideAgent.stream(any(Msg.class), any(StreamOptions.class)))
                .thenReturn(Flux.just(chunk1, chunk2));
        A2aAgentTools tools = createToolsWith(guideAgent, registry);

        tools.callGuideAgent("<traceId>trace-dedup-result</traceId>请检查", "user-1")
                .block(Duration.ofSeconds(2));

        long toolResultEvents =
                sink.asFlux()
                        .takeUntilOther(reactor.core.publisher.Mono.delay(Duration.ofMillis(100)))
                        .filter(e -> "tool_result".equals(e.event()))
                        .collectList()
                        .block(Duration.ofSeconds(1))
                        .size();

        assertThat(toolResultEvents).isEqualTo(1);
    }

    @Test
    void emitsToolStartExactlyOnceEvenWhenFrameworkChunksItAcrossMultipleEvents() {
        // Same fragmentation story for ToolUseBlock arriving across REASONING events.
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        registry.register("trace-dedup-start", emitter);

        Event chunk1 =
                new Event(
                        EventType.REASONING,
                        Msg.builder()
                                .content(
                                        new ToolUseBlock(
                                                "tool-1",
                                                "resource-get",
                                                Map.of("namespace", "default")))
                                .build(),
                        false);
        Event chunk2 =
                new Event(
                        EventType.REASONING,
                        Msg.builder()
                                .content(
                                        new ToolUseBlock(
                                                "tool-1",
                                                "resource-get",
                                                Map.of("namespace", "default", "kind", "Pod")))
                                .build(),
                        false);

        A2aAgent guideAgent = org.mockito.Mockito.mock(A2aAgent.class);
        when(guideAgent.stream(any(Msg.class), any(StreamOptions.class)))
                .thenReturn(Flux.just(chunk1, chunk2));
        A2aAgentTools tools = createToolsWith(guideAgent, registry);

        tools.callGuideAgent("<traceId>trace-dedup-start</traceId>请检查", "user-1")
                .block(Duration.ofSeconds(2));

        long toolStartEvents =
                sink.asFlux()
                        .takeUntilOther(reactor.core.publisher.Mono.delay(Duration.ofMillis(100)))
                        .filter(e -> "tool_start".equals(e.event()))
                        .collectList()
                        .block(Duration.ofSeconds(1))
                        .size();

        assertThat(toolStartEvents).isEqualTo(1);
    }

    @Test
    void emitsAccumulatedAgentResultOnceAtStreamEndNotOnFirstFragment() throws Exception {
        // Prior bug: AGENT_RESULT on doOnNext fired emitReasoningDelta on the FIRST fragment
        // and locked answerEmitted, so the user only ever saw the early partial answer.
        // After the fix, emit happens once at .then(...) with the fully-accumulated text.
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        registry.register("trace-final", emitter);

        Event part1 =
                new Event(
                        EventType.AGENT_RESULT,
                        Msg.builder().content(TextBlock.builder().text("Hello").build()).build(),
                        false);
        Event part2 =
                new Event(
                        EventType.AGENT_RESULT,
                        Msg.builder()
                                .content(TextBlock.builder().text(", world").build())
                                .build(),
                        false);

        A2aAgent guideAgent = org.mockito.Mockito.mock(A2aAgent.class);
        when(guideAgent.stream(any(Msg.class), any(StreamOptions.class)))
                .thenReturn(Flux.just(part1, part2));
        A2aAgentTools tools = createToolsWith(guideAgent, registry);

        String result =
                tools.callGuideAgent("<traceId>trace-final</traceId>请检查", "user-1")
                        .block(Duration.ofSeconds(2));

        assertThat(result).isEqualTo("Hello, world");

        List<ServerSentEvent<String>> reasoningEvents =
                sink.asFlux()
                        .takeUntilOther(reactor.core.publisher.Mono.delay(Duration.ofMillis(100)))
                        .filter(e -> "reasoning_delta".equals(e.event()))
                        .collectList()
                        .block(Duration.ofSeconds(1));
        assertThat(reasoningEvents).hasSize(1);

        Map<String, Object> payload =
                objectMapper.readValue(reasoningEvents.get(0).data(), new TypeReference<>() {});
        assertThat(payload).containsEntry("agent", "guide_agent").containsEntry("text", "Hello, world");
    }

    private A2aAgentTools createToolsWith(A2aAgent guideAgent, StructuredTraceRegistry registry) {
        return new A2aAgentTools(
                provider(guideAgent),
                emptyProvider(),
                registry,
                Duration.ofSeconds(5),
                1,
                Duration.ofMillis(1));
    }

    @Test
    void returnsNullEmitterWhenTraceIdIsMissingAndContextHasNone() throws Exception {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        // Another user's emitter is registered under a different traceId; we must NOT receive it
        // as a fallback when our own traceId cannot be resolved.
        registry.register(
                "other-users-trace",
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper));
        A2aAgentTools tools = createTools(registry);

        Method resolveEmitter =
                A2aAgentTools.class.getDeclaredMethod(
                        "resolveEmitter", String.class, ContextView.class);
        resolveEmitter.setAccessible(true);

        Object resolved = resolveEmitter.invoke(tools, "", Context.empty().readOnly());

        assertThat(resolved).isNull();
    }

    @Test
    void resolvesEmitterFromReactorContextEvenWhenTraceIdIsStripped() throws Exception {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        A2aAgentTools tools = createTools(registry);
        StructuredSseEmitter perRequestEmitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        ContextView ctx =
                Context.of(StructuredSseEmitter.CONTEXT_KEY, perRequestEmitter).readOnly();

        Method resolveEmitter =
                A2aAgentTools.class.getDeclaredMethod(
                        "resolveEmitter", String.class, ContextView.class);
        resolveEmitter.setAccessible(true);

        Object resolved = resolveEmitter.invoke(tools, "", ctx);

        assertThat(resolved).isSameAs(perRequestEmitter);
    }

    @Test
    void prefersReactorContextOverRegistryWhenBothAreAvailable() throws Exception {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        StructuredSseEmitter registryEmitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        registry.register("trace-1", registryEmitter);
        A2aAgentTools tools = createTools(registry);
        StructuredSseEmitter contextEmitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        ContextView ctx =
                Context.of(StructuredSseEmitter.CONTEXT_KEY, contextEmitter).readOnly();

        Method resolveEmitter =
                A2aAgentTools.class.getDeclaredMethod(
                        "resolveEmitter", String.class, ContextView.class);
        resolveEmitter.setAccessible(true);

        Object resolved = resolveEmitter.invoke(tools, "trace-1", ctx);

        assertThat(resolved).isSameAs(contextEmitter);
    }

    @Test
    void fallsBackToRegistryWhenContextIsEmptyButTraceIdIsKnown() throws Exception {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        StructuredSseEmitter registryEmitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        registry.register("trace-1", registryEmitter);
        A2aAgentTools tools = createTools(registry);

        Method resolveEmitter =
                A2aAgentTools.class.getDeclaredMethod(
                        "resolveEmitter", String.class, ContextView.class);
        resolveEmitter.setAccessible(true);

        Object resolved = resolveEmitter.invoke(tools, "trace-1", Context.empty().readOnly());

        assertThat(resolved).isSameAs(registryEmitter);
    }

    @Test
    void treatsChunkedEofAsTransientA2aTransportFailure() {
        IOException chunkedFailure =
                new IOException(
                        "chunked transfer encoding, state: READING_LENGTH",
                        new EOFException("EOF reached while reading"));

        assertThat(A2aAgentTools.isTransientA2aTransportFailure(chunkedFailure)).isTrue();
    }

    @Test
    void doesNotTreatUnrelatedFailuresAsTransientA2aTransportFailure() {
        IllegalArgumentException failure = new IllegalArgumentException("invalid tool input");

        assertThat(A2aAgentTools.isTransientA2aTransportFailure(failure)).isFalse();
    }

    @Test
    void retriesTransientA2aTransportFailuresForAtMostThreeTotalAttempts() {
        RuntimeException failure =
                new RuntimeException(
                        new IOException(
                                "chunked transfer encoding, state: READING_LENGTH",
                                new EOFException("EOF reached while reading")));

        assertThat(A2aAgentTools.shouldRetryChildAgentCall(failure, 1)).isTrue();
        assertThat(A2aAgentTools.shouldRetryChildAgentCall(failure, 2)).isTrue();
        assertThat(A2aAgentTools.shouldRetryChildAgentCall(failure, 3)).isFalse();
    }

    @Test
    void doesNotRetryTimeoutFailures() {
        TimeoutException failure = new TimeoutException("child agent timeout");

        assertThat(A2aAgentTools.isTransientA2aTransportFailure(failure)).isFalse();
        assertThat(A2aAgentTools.isTimeoutFailure(new RuntimeException(failure))).isTrue();
        assertThat(A2aAgentTools.shouldRetryChildAgentCall(new RuntimeException(failure), 1))
                .isFalse();
    }

    @Test
    void detectsInterruptedFailuresSeparatelyFromTransientTransportFailures() {
        RuntimeException failure = new RuntimeException(new InterruptedException("interrupted"));

        assertThat(A2aAgentTools.isInterruptedFailure(failure)).isTrue();
        assertThat(A2aAgentTools.isTransientA2aTransportFailure(failure)).isFalse();
    }

    @Test
    void returnsStructuredTimeoutErrorAndInterruptsChildAgent() {
        A2aAgent guideAgent = org.mockito.Mockito.mock(A2aAgent.class);
        when(guideAgent.stream(any(Msg.class), any(StreamOptions.class))).thenReturn(Flux.never());
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        registry.register("trace-timeout", emitter);
        A2aAgentTools tools =
                new A2aAgentTools(
                        provider(guideAgent),
                        emptyProvider(),
                        registry,
                        Duration.ofMillis(10),
                        3,
                        Duration.ofMillis(1));

        String result =
                tools.callGuideAgent("<traceId>trace-timeout</traceId>请诊断", "user-1")
                        .block(Duration.ofSeconds(1));

        assertThat(result).contains("子 Agent 执行超时");
        verify(guideAgent).interrupt();
        verify(guideAgent, never()).call(anyList());

        ServerSentEvent<String> event = sink.asFlux().next().block(Duration.ofSeconds(1));
        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("error");
    }

    private A2aAgentTools createTools(StructuredTraceRegistry registry) {
        return new A2aAgentTools(
                emptyProvider(),
                emptyProvider(),
                registry,
                Duration.ofMinutes(10),
                3,
                Duration.ofMillis(200));
    }

    private ObjectProvider<A2aAgent> provider(A2aAgent agent) {
        return new ObjectProvider<>() {
            @Override
            public A2aAgent getObject(Object... args) {
                return agent;
            }

            @Override
            public A2aAgent getIfAvailable() {
                return agent;
            }

            @Override
            public A2aAgent getIfUnique() {
                return agent;
            }

            @Override
            public A2aAgent getObject() {
                return agent;
            }

            @Override
            public Stream<A2aAgent> stream() {
                return Stream.of(agent);
            }

            @Override
            public Stream<A2aAgent> orderedStream() {
                return Stream.of(agent);
            }

            @Override
            public java.util.Iterator<A2aAgent> iterator() {
                return Collections.singleton(agent).iterator();
            }
        };
    }

    private ObjectProvider<A2aAgent> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public A2aAgent getObject(Object... args) {
                return null;
            }

            @Override
            public A2aAgent getIfAvailable() {
                return null;
            }

            @Override
            public A2aAgent getIfUnique() {
                return null;
            }

            @Override
            public A2aAgent getObject() {
                return null;
            }

            @Override
            public Stream<A2aAgent> stream() {
                return Stream.empty();
            }

            @Override
            public Stream<A2aAgent> orderedStream() {
                return Stream.empty();
            }

            @Override
            public java.util.Iterator<A2aAgent> iterator() {
                return Collections.emptyIterator();
            }
        };
    }
}
