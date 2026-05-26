package io.agentscope.examples.paasassistant.common.utils;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.examples.paasassistant.common.config.AgentConstants;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.io.IOException;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * A wrapper for McpClientWrapper that intercepts tool calls and applies
 * K8s data sanitization to the output content.
 */
public class SanitizingMcpClient extends McpClientWrapper {

    private static final Logger logger = LoggerFactory.getLogger(SanitizingMcpClient.class);

    private static final ObjectMapper signatureMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final McpClientWrapper delegate;

    public SanitizingMcpClient(McpClientWrapper delegate) {
        super(delegate.getName());
        this.delegate = delegate;
    }

    @Override
    public Mono<Void> initialize() {
        return delegate.initialize();
    }

    @Override
    public Mono<List<McpSchema.Tool>> listTools() {
        return delegate.listTools().map(tools -> 
            tools.stream().map(this::filterSystemParameters).collect(Collectors.toList())
        );
    }

    private McpSchema.Tool filterSystemParameters(McpSchema.Tool tool) {
        if (tool.inputSchema() == null || tool.inputSchema().properties() == null) {
            return tool;
        }

        Map<String, Object> properties = new HashMap<>(tool.inputSchema().properties());
        List<String> required = tool.inputSchema().required() != null ? 
                new ArrayList<>(tool.inputSchema().required()) : new ArrayList<>();

        // Hide system-level parameters from the Agent
        properties.remove(AgentConstants.PARAM_CHAT_ID);
        properties.remove(AgentConstants.PARAM_USER_ID);
        properties.remove(AgentConstants.PARAM_CLUSTER);

        required.remove(AgentConstants.PARAM_CHAT_ID);
        required.remove(AgentConstants.PARAM_USER_ID);
        required.remove(AgentConstants.PARAM_CLUSTER);

        // Construct new JsonSchema
        McpSchema.JsonSchema newSchema = new McpSchema.JsonSchema(
                tool.inputSchema().type(),
                properties,
                required,
                tool.inputSchema().additionalProperties(),
                tool.inputSchema().defs(),
                tool.inputSchema().definitions()
        );

        // Construct new Tool using builder
        return McpSchema.Tool.builder()
                .name(tool.name())
                .title(tool.title())
                .description(tool.description())
                .inputSchema(newSchema)
                .outputSchema(tool.outputSchema())
                .annotations(tool.annotations())
                .meta(tool.meta())
                .build();
    }


    @Override
    public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> arguments) {
        return Mono.deferContextual(ctx -> {
            String clusterId = ctx.getOrDefault(AgentConstants.CTX_CLUSTER_ID, "");
            String userId = ctx.getOrDefault(AgentConstants.CTX_USER_ID, "");
            String chatId = ctx.getOrDefault(AgentConstants.CTX_CHAT_ID, "");

            Map<String, Object> finalArgs = new HashMap<>(arguments != null ? arguments : Map.of());
            
            // Automatically inject system-level parameters
            if (clusterId != null && !clusterId.isEmpty()) {
                finalArgs.put(AgentConstants.PARAM_CLUSTER, clusterId);
            }
            if (userId != null && !userId.isEmpty()) {
                finalArgs.put(AgentConstants.PARAM_USER_ID, userId);
            }
            if (chatId != null && !chatId.isEmpty()) {
                finalArgs.put(AgentConstants.PARAM_CHAT_ID, chatId);
            }
            
            // Use Jackson to generate a canonical JSON string with sorted keys (recursive)
            // Extracted to a method so the variable is effectively final (required for inner lambda use)
            final String signature = buildSignature(name, finalArgs);
            
            Map<String, McpSchema.CallToolResult> cache = ctx.getOrDefault(AgentConstants.CTX_TOOL_CACHE, null);
            if (cache != null && cache.containsKey(signature)) {
                logger.info("Skipping duplicate tool call for '{}' (returning cached result)", name);
                return Mono.just(cache.get(signature));
            }

            // Destructive tools are not permitted in the diagnosis agent.
            // Return a structured error result so the LLM can explain to the user
            // that controlled changes must go through the platform change-plan workflow.
            if (isDestructive(name)) {
                logger.warn("Blocked destructive tool call in analyze agent: {}", name);
                McpSchema.TextContent errorContent = new McpSchema.TextContent(
                        null,
                        "[TOOL_BLOCKED] The tool '" + name + "' performs a destructive Kubernetes operation "
                                + "and is not permitted within the analyze agent. "
                                + "Please use the platform change-plan tools (change-plan-restart, "
                                + "change-plan-scale, change-plan-delete-pod, change-plan-patch) "
                                + "to create an approved change plan instead.",
                        null);
                return Mono.just(new McpSchema.CallToolResult(List.of(errorContent), true, null, null));
            }

            Mono<McpSchema.CallToolResult> call = delegate.callTool(name, finalArgs)
                    .map(result -> sanitizeResult(name, result))
                    .doOnNext(res -> {
                        if (cache != null) {
                            cache.put(signature, res);
                        }
                    });

            // For non-destructive tools only: retry once on transient network failures.
            // Destructive tools (delete, restart, scale, etc.) are never retried automatically
            // to prevent accidental double-execution.
            if (!isDestructive(name)) {
                call = call.retryWhen(
                        Retry.backoff(1, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(10))
                                .filter(SanitizingMcpClient::isTransientMcpFailure)
                                .doBeforeRetry(signal -> logger.warn(
                                        "Transient MCP failure for tool '{}', retrying (attempt {}/1): {}",
                                        name, signal.totalRetriesInARow() + 1, signal.failure().getMessage())));
            }
            return call;
        });
    }

    private String buildSignature(String toolName, Map<String, Object> args) {
        try {
            return toolName + ":" + signatureMapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to generate stable signature for tool {}, falling back to toString", toolName);
            return toolName + ":" + args.toString();
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    private boolean isDestructive(String toolName) {
        if (toolName == null) return false;
        String baseName = toolName.contains("__") ?
                toolName.substring(toolName.lastIndexOf("__") + 2) : toolName;
        return AgentConstants.DESTRUCTIVE_TOOL_PREFIXES.stream().anyMatch(baseName::startsWith);
    }

    /**
     * Returns true for transient network/IO errors that are safe to retry.
     * Excludes application-level errors (e.g. MCP tool not found, bad arguments).
     */
    private static boolean isTransientMcpFailure(Throwable e) {
        if (e instanceof IOException) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("timeout") || msg.contains("connection reset")
                || msg.contains("EOF") || msg.contains("broken pipe"));
    }

    /**
     * Internal logic to identify and sanitize K8s list/get results.
     */
    private McpSchema.CallToolResult sanitizeResult(String toolName, McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return result;
        }

        // Requirement: Only sanitize "list" or "get" methods for K8s diagnostic data
        if (!isListOrGetMethod(toolName)) {
            return result;
        }

        boolean changed = false;
        List<McpSchema.Content> newContentList = new ArrayList<>();

        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                String rawText = textContent.text();
                if (rawText != null) {
                    String sanitizedText = K8sDataSanitizer.processGenericResource(rawText);
                    if (sanitizedText.length() != rawText.length()) {
                        newContentList.add(new McpSchema.TextContent(
                                textContent.annotations(),
                                sanitizedText,
                                textContent.meta()
                        ));
                        changed = true;
                        logger.info("Sanitized MCP tool output for '{}' ({} -> {} chars)", 
                                toolName, rawText.length(), sanitizedText.length());
                        continue;
                    }
                }
            }
            newContentList.add(content);
        }

        if (changed) {
            return new McpSchema.CallToolResult(
                    newContentList,
                    result.isError(),
                    result.structuredContent(),
                    result.meta()
            );
        }

        return result;
    }

    private boolean isListOrGetMethod(String toolName) {
        if (toolName == null) return false;
        // In MCP, tool name might be prefixed like "k8s-mcp-server__list-pods"
        String baseName = toolName.contains("__") ? 
                toolName.substring(toolName.lastIndexOf("__") + 2) : toolName;
        return baseName.startsWith("list") || baseName.startsWith("get");
    }
}
