package io.agentscope.examples.paasassistant.change.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.paasassistant.change.tools.ChangeMcpTools;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * Adapts AgentScope local tools to an MCP server exposed over WebFlux SSE.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public Toolkit changeToolkit(ChangeMcpTools changeMcpTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(changeMcpTools);
        return toolkit;
    }

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public WebFluxSseServerTransportProvider webFluxSseServerTransportProvider(
            McpJsonMapper mcpJsonMapper) {
        return WebFluxSseServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
                .sseEndpoint("/mcp/sse")
                .messageEndpoint("/mcp/message")
                .keepAliveInterval(Duration.ofSeconds(15))
                .build();
    }

    @Bean
    public RouterFunction<?> mcpRouterFunction(
            WebFluxSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(
            Toolkit changeToolkit,
            WebFluxSseServerTransportProvider transportProvider,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:change-mcp-server}") String serviceName) {
        McpSyncServer mcpSyncServer =
                McpServer.sync(transportProvider)
                        .serverInfo(serviceName, "1.0.0")
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                        .build();

        for (ToolSchema toolSchema : changeToolkit.getToolSchemas()) {
            AgentTool agentTool = changeToolkit.getTool(toolSchema.getName());
            if (agentTool == null) {
                continue;
            }

            McpSchema.Tool tool = toMcpTool(toolSchema);

            McpServerFeatures.SyncToolSpecification specification =
                    McpServerFeatures.SyncToolSpecification.builder()
                            .tool(tool)
                            .callHandler(
                                    (exchange, request) ->
                                            invokeTool(toolSchema.getName(), agentTool, request, objectMapper))
                            .build();
            mcpSyncServer.addTool(specification);
        }

        return mcpSyncServer;
    }

    private McpSchema.CallToolResult invokeTool(
            String toolName,
            AgentTool agentTool,
            McpSchema.CallToolRequest request,
            ObjectMapper objectMapper) {
        Map<String, Object> arguments =
                request.arguments() == null ? Collections.emptyMap() : request.arguments();
        ToolUseBlock toolUseBlock =
                ToolUseBlock.builder()
                        .id(UUID.randomUUID().toString())
                        .name(toolName)
                        .input(arguments)
                        .build();
        ToolCallParam toolCallParam =
                ToolCallParam.builder().toolUseBlock(toolUseBlock).input(arguments).build();

        try {
            ToolResultBlock toolResult = agentTool.callAsync(toolCallParam).block();
            return toCallToolResult(toolResult, objectMapper);
        } catch (Exception exception) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(errorMessage(exception))), true);
        }
    }

    private McpSchema.CallToolResult toCallToolResult(
            ToolResultBlock toolResult, ObjectMapper objectMapper) {
        if (toolResult == null) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Tool returned no output.")), false);
        }

        List<McpSchema.Content> content = new ArrayList<>();
        for (ContentBlock block : toolResult.getOutput()) {
            content.add(toContent(block, objectMapper));
        }
        if (content.isEmpty()) {
            content.add(new McpSchema.TextContent(""));
        }

        return new McpSchema.CallToolResult(content, isErrorResult(toolResult, objectMapper));
    }

    private McpSchema.Content toContent(ContentBlock block, ObjectMapper objectMapper) {
        if (block instanceof TextBlock textBlock) {
            return new McpSchema.TextContent(textBlock.getText());
        }
        try {
            return new McpSchema.TextContent(objectMapper.writeValueAsString(block));
        } catch (JsonProcessingException exception) {
            return new McpSchema.TextContent(block.toString());
        }
    }

    private boolean isErrorResult(ToolResultBlock toolResult, ObjectMapper objectMapper) {
        for (ContentBlock block : toolResult.getOutput()) {
            if (!(block instanceof TextBlock textBlock)) {
                continue;
            }
            String text = textBlock.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.startsWith("Error:")) {
                return true;
            }
            try {
                JsonNode payload = objectMapper.readTree(text);
                JsonNode status = payload.get("status");
                if (status != null && "ERROR".equalsIgnoreCase(status.asText())) {
                    return true;
                }
            } catch (JsonProcessingException ignored) {
                // Plain text results are expected for many tools; only structured error payloads matter here.
            }
        }
        return false;
    }

    McpSchema.Tool toMcpTool(ToolSchema toolSchema) {
        return McpSchema.Tool.builder()
                .name(toolSchema.getName())
                .description(toolSchema.getDescription())
                .inputSchema(toJsonSchema(toolSchema.getParameters()))
                .outputSchema(toolSchema.getOutputSchema())
                .build();
    }

    McpSchema.JsonSchema toJsonSchema(Map<String, Object> parameters) {
        Map<String, Object> schema =
                parameters == null ? Collections.emptyMap() : parameters;
        return new McpSchema.JsonSchema(
                asString(schema.getOrDefault("type", "object")),
                asMap(schema.get("properties")),
                asStringList(schema.get("required")),
                asBoolean(schema.get("additionalProperties")),
                asMap(schema.get("$defs")),
                asMap(schema.get("definitions")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        return value instanceof List<?> list ? (List<String>) list : Collections.emptyList();
    }

    private Boolean asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String errorMessage(Exception exception) {
        return exception.getMessage() == null ? "Tool execution failed." : exception.getMessage();
    }
}
