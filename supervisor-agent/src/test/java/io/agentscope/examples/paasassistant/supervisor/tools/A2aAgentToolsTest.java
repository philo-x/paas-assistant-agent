package io.agentscope.examples.paasassistant.supervisor.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredSseEmitter;
import io.agentscope.examples.paasassistant.supervisor.stream.StructuredTraceRegistry;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

class A2aAgentToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsChildToolResultBlocksIntoStructuredTimeline() throws Exception {
        A2aAgentTools tools =
                new A2aAgentTools(emptyProvider(), emptyProvider(), new StructuredTraceRegistry());
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);
        Msg message =
                Msg.builder()
                        .content(
                                ToolResultBlock.of(
                                        "tool-1",
                                        "resource-list",
                                        TextBlock.builder().text("found 3 pods").build()))
                        .build();

        Method emitToolResultBlocks =
                A2aAgentTools.class.getDeclaredMethod(
                        "emitToolResultBlocks",
                        String.class,
                        Msg.class,
                        StructuredSseEmitter.class);
        emitToolResultBlocks.setAccessible(true);

        boolean emitted =
                (boolean)
                        emitToolResultBlocks.invoke(
                                tools, "diagnosis_agent", message, emitter);

        assertThat(emitted).isTrue();

        ServerSentEvent<String> event = sink.asFlux().next().block();
        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("tool_result");

        Map<String, Object> payload =
                objectMapper.readValue(event.data(), new TypeReference<>() {});
        assertThat(payload)
                .containsEntry("agent", "diagnosis_agent")
                .containsEntry("tool", "resource-list")
                .containsEntry("title", "查询资源列表 (resource-list)")
                .containsEntry("delegation", false)
                .containsEntry("status", "success")
                .containsEntry("summary", "已查询目标资源列表，用于筛查异常对象。");
    }

    @Test
    void fallsBackToLatestEmitterWhenTraceIdIsNotPresentInContext() throws Exception {
        StructuredTraceRegistry registry = new StructuredTraceRegistry();
        A2aAgentTools tools = new A2aAgentTools(emptyProvider(), emptyProvider(), registry);
        StructuredSseEmitter latestEmitter =
                new StructuredSseEmitter(Sinks.many().replay().all(), objectMapper);
        registry.register("trace-latest", latestEmitter);

        Method resolveEmitter =
                A2aAgentTools.class.getDeclaredMethod("resolveEmitter", String.class);
        resolveEmitter.setAccessible(true);

        Object resolved = resolveEmitter.invoke(tools, "用户问题: 请检查 default 命名空间中的 Pod");

        assertThat(resolved).isSameAs(latestEmitter);
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
