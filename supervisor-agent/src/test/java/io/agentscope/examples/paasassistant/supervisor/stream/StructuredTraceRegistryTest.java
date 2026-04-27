package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

class StructuredTraceRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsLatestActiveEmitterWhenTraceIdIsMissing() {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        StructuredSseEmitter first =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        StructuredSseEmitter second =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);

        registry.register("trace-1", first);
        registry.register("trace-2", second);

        assertThat(registry.getLatest()).isSameAs(second);

        registry.unregister("trace-2");
        assertThat(registry.getLatest()).isSameAs(first);
    }
}
