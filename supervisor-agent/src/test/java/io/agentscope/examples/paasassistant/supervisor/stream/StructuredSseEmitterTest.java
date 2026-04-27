package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

class StructuredSseEmitterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsSequenceAndToolMetadataForStructuredTimeline() throws Exception {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
        StructuredSseEmitter emitter = new StructuredSseEmitter(sink, objectMapper);

        emitter.emitReasoningDelta("supervisor_agent", "先分析请求。");
        emitter.emitToolStart(
                "supervisor_agent",
                "callDiagnosisAgent",
                "{namespace=default, kind=Pod}");
        emitter.emitToolResult(
                "diagnosis_agent",
                "resource-list",
                "success",
                "已查询目标资源列表，用于筛查异常对象。",
                "{namespace=default, kind=Pod}");

        List<ServerSentEvent<String>> events = sink.asFlux().take(3).collectList().block();
        assertThat(events).hasSize(3);

        Map<String, Object> reasoningPayload = payload(events.get(0));
        assertThat(events.get(0).event()).isEqualTo("reasoning_delta");
        assertThat(reasoningPayload)
                .containsEntry("sequence", 1)
                .containsEntry("agent", "supervisor_agent")
                .containsEntry("text", "先分析请求。");

        Map<String, Object> handoffPayload = payload(events.get(1));
        assertThat(events.get(1).event()).isEqualTo("tool_start");
        assertThat(handoffPayload)
                .containsEntry("sequence", 2)
                .containsEntry("agent", "supervisor_agent")
                .containsEntry("tool", "callDiagnosisAgent")
                .containsEntry("title", "转交 Diagnosis Agent")
                .containsEntry("delegation", true);

        Map<String, Object> toolPayload = payload(events.get(2));
        assertThat(events.get(2).event()).isEqualTo("tool_result");
        assertThat(toolPayload)
                .containsEntry("sequence", 3)
                .containsEntry("agent", "diagnosis_agent")
                .containsEntry("tool", "resource-list")
                .containsEntry("title", "查询资源列表 (resource-list)")
                .containsEntry("delegation", false)
                .containsEntry("status", "success");
    }

    private Map<String, Object> payload(ServerSentEvent<String> event) throws Exception {
        return objectMapper.readValue(event.data(), new TypeReference<>() {});
    }
}
