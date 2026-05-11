package io.agentscope.examples.paasassistant.diagnosis.utils;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * A wrapper for McpClientWrapper that intercepts tool calls and applies
 * K8s data sanitization to the output content.
 */
public class SanitizingMcpClient extends McpClientWrapper {

    private static final Logger logger = LoggerFactory.getLogger(SanitizingMcpClient.class);

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
        return delegate.listTools();
    }

    private static final List<String> DESTRUCTIVE_PREFIXES = List.of(
            "delete_", "restart_", "scale_", "stop_", "update_", "apply_", 
            "cordon_", "drain_", "taint_", "uncordon_", "untaint_", "patch_", "restore_", "undo_", "run_command_"
    );

    @Override
    public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> arguments) {
        return Mono.deferContextual(ctx -> {
            String clusterId = ctx.getOrDefault("cluster_id", "");
            Map<String, Object> finalArgs = arguments;
            if (clusterId != null && !clusterId.isEmpty()) {
                finalArgs = new java.util.HashMap<>(arguments != null ? arguments : java.util.Map.of());
                finalArgs.put("cluster", clusterId);
            }
            
            String signature = name + ":" + finalArgs.toString();
            
            Map<String, McpSchema.CallToolResult> cache = ctx.getOrDefault("tool_cache", null);
            if (cache != null && cache.containsKey(signature)) {
                logger.info("Skipping duplicate tool call for '{}' (returning cached result)", name);
                return Mono.just(cache.get(signature));
            }
            
            java.util.Set<String> approvedTools = ctx.getOrDefault("approved_tools", java.util.Collections.emptySet());
            if (isDestructive(name) && !approvedTools.contains(name)) {
                logger.warn("Intercepted unapproved destructive tool call: {}", name);
                return Mono.error(new io.agentscope.core.tool.ToolSuspendException(
                        "⚠️ 智能体申请执行高危操作：" + name + "，请人工确认。"
                ));
            }

            return delegate.callTool(name, finalArgs)
                    .map(result -> sanitizeResult(name, result))
                    .doOnNext(res -> {
                        if (cache != null) {
                            cache.put(signature, res);
                        }
                    });
        });
    }

    @Override
    public void close() {
        delegate.close();
    }

    private boolean isDestructive(String toolName) {
        if (toolName == null) return false;
        String baseName = toolName.contains("__") ? 
                toolName.substring(toolName.lastIndexOf("__") + 2) : toolName;
        return DESTRUCTIVE_PREFIXES.stream().anyMatch(baseName::startsWith);
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
