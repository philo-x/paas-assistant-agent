package io.agentscope.examples.paasassistant.supervisor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredStreamHook;
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
    void processStructuredStreamOnlyEmitsDoneOnComplete() throws Exception {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        SupervisorAgentController controller =
                new SupervisorAgentController(null, objectMapper, new StructuredTraceRegistry());

        controller.processStructuredStream(
                Flux.empty(),
                sink,
                emitter);

        List<ServerSentEvent<String>> events =
                sink.asFlux().collectList().block(Duration.ofSeconds(1));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("done");
        assertThat(payload(events.get(0))).containsEntry("status", "completed");
    }

    @Test
    void structuredStreamHookEmitsAnswerDeltaOnPostCallEvent() throws Exception {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        StructuredStreamHook hook = new StructuredStreamHook("supervisor_agent", emitter);

        PostCallEvent postCall = mock(PostCallEvent.class);
        Msg finalMsg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("final diagnosis answer").build())
                .build();
        when(postCall.getFinalMessage()).thenReturn(finalMsg);

        hook.onEvent(postCall).block();
        sink.tryEmitComplete();

        List<ServerSentEvent<String>> events =
                sink.asFlux().collectList().block(Duration.ofSeconds(1));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("answer_delta");
        assertThat(payload(events.get(0))).containsEntry("text", "final diagnosis answer");
    }

    private Map<String, Object> payload(ServerSentEvent<String> event) throws Exception {
        return objectMapper.readValue(event.data(), new TypeReference<>() {});
    }

    @Test
    void testReflect() {
        System.out.println("AutoCloseable? " + java.lang.AutoCloseable.class.isAssignableFrom(io.agentscope.core.session.mysql.MysqlSession.class));
        for (java.lang.reflect.Constructor<?> c : io.agentscope.core.session.mysql.MysqlSession.class.getConstructors()) {
            System.out.println("Constructor: " + c.toString());
        }
        for (java.lang.reflect.Field f : io.agentscope.core.session.mysql.MysqlSession.class.getDeclaredFields()) {
            System.out.println("Field: " + f.toString());
        }
    }
}
