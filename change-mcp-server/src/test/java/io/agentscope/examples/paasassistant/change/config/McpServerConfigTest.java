package io.agentscope.examples.paasassistant.change.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.model.ToolSchema;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerConfigTest {

    private final McpServerConfig config = new McpServerConfig();

    @Test
    void toJsonSchemaPreservesStructuredSchemaFields() {
        Map<String, Object> properties =
                Map.of("namespace", Map.of("type", "string"), "name", Map.of("type", "string"));
        Map<String, Object> defs =
                Map.of("ResourceRef", Map.of("type", "object", "properties", properties));
        Map<String, Object> definitions =
                Map.of("RiskLevel", Map.of("type", "string", "enum", List.of("LOW", "HIGH")));
        Map<String, Object> parameters =
                Map.of(
                        "type", "object",
                        "properties", properties,
                        "required", List.of("namespace", "name"),
                        "additionalProperties", false,
                        "$defs", defs,
                        "definitions", definitions);

        McpSchema.JsonSchema schema = config.toJsonSchema(parameters);

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).isEqualTo(properties);
        assertThat(schema.required()).containsExactly("namespace", "name");
        assertThat(schema.additionalProperties()).isFalse();
        assertThat(schema.defs()).isEqualTo(defs);
        assertThat(schema.definitions()).isEqualTo(definitions);
    }

    @Test
    void toMcpToolPreservesAgentScopeOutputSchema() {
        Map<String, Object> outputSchema =
                Map.of(
                        "type", "object",
                        "properties", Map.of("status", Map.of("type", "string")),
                        "required", List.of("status"));
        ToolSchema toolSchema =
                ToolSchema.builder()
                        .name("change-get-status")
                        .description("Gets a change status.")
                        .parameters(Map.of("type", "object", "additionalProperties", true))
                        .outputSchema(outputSchema)
                        .build();

        McpSchema.Tool tool = config.toMcpTool(toolSchema);

        assertThat(tool.name()).isEqualTo("change-get-status");
        assertThat(tool.description()).isEqualTo("Gets a change status.");
        assertThat(tool.inputSchema().additionalProperties()).isTrue();
        assertThat(tool.outputSchema()).isEqualTo(outputSchema);
    }
}
