package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class StructuredTraceRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsRegisteredEmitterForExactTraceId() {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        StructuredSseEmitter emitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);

        registry.register("trace-1", emitter);

        assertThat(registry.get("trace-1")).isSameAs(emitter);
    }

    @Test
    void returnsNullForUnknownTraceId() {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        registry.register(
                "trace-1",
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper));

        assertThat(registry.get("trace-unknown")).isNull();
    }

    @Test
    void returnsNullForNullOrBlankTraceId() {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        registry.register(
                "trace-1",
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper));

        assertThat(registry.get(null)).isNull();
        assertThat(registry.get("")).isNull();
        assertThat(registry.get("   ")).isNull();
    }

    @Test
    void doesNotReturnAnotherUsersEmitterWhenLookupFails() {
        // Cross-user isolation guarantee: a request whose traceId cannot be resolved must NOT
        // receive any other user's emitter as a fallback. Prior to this fix, getLatest() would
        // hand back the most recently registered emitter (i.e. another user's), causing
        // child-agent SSE events to leak across users.
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        StructuredSseEmitter userA =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        StructuredSseEmitter userB =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        registry.register("trace-A", userA);
        registry.register("trace-B", userB);

        assertThat(registry.get(null)).isNull();
        assertThat(registry.get("")).isNull();
        assertThat(registry.get("trace-stripped-by-llm")).isNull();
    }

    @Test
    void unregisterRemovesEmitter() {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        StructuredSseEmitter emitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        registry.register("trace-1", emitter);

        registry.unregister("trace-1");

        assertThat(registry.get("trace-1")).isNull();
    }
}
