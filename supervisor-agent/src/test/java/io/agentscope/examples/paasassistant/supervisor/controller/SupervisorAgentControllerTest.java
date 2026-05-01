package io.agentscope.examples.paasassistant.supervisor.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredTraceRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class SupervisorAgentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void structuredStreamKeepsExistingTextBlockStreamingBehavior() throws Exception {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        SupervisorAgentController controller =
                new SupervisorAgentController(null, objectMapper, new StructuredTraceRegistry());

        controller.processStructuredStream(
                Flux.just(
                        event(EventType.REASONING, "final answer chunk"),
                        event(EventType.TOOL_RESULT, "tool result text"),
                        event(EventType.AGENT_RESULT, "agent result chunk")),
                sink,
                emitter);

        List<ServerSentEvent<String>> events =
                sink.asFlux().collectList().block(Duration.ofSeconds(1));

        assertThat(events).hasSize(4);
        assertThat(events.get(0).event()).isEqualTo("answer_delta");
        assertThat(payload(events.get(0))).containsEntry("text", "final answer chunk");
        assertThat(events.get(1).event()).isEqualTo("answer_delta");
        assertThat(payload(events.get(1))).containsEntry("text", "tool result text");
        assertThat(events.get(2).event()).isEqualTo("answer_delta");
        assertThat(payload(events.get(2))).containsEntry("text", "agent result chunk");
        assertThat(events.get(3).event()).isEqualTo("done");
    }

    private Event event(EventType eventType, String text) {
        return new Event(
                eventType,
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .content(TextBlock.builder().text(text).build())
                        .build(),
                false);
    }

    private Map<String, Object> payload(ServerSentEvent<String> event) throws Exception {
        return objectMapper.readValue(event.data(), new TypeReference<>() {});
    }
}
