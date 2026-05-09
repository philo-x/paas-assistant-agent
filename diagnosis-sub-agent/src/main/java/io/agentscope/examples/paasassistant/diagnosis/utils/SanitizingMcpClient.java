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

    @Override
    public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> arguments) {
        String signature = name + ":" + (arguments != null ? arguments.toString() : "{}");
        return Mono.deferContextual(ctx -> {
            Map<String, McpSchema.CallToolResult> cache = ctx.getOrDefault("tool_cache", null);
            if (cache != null && cache.containsKey(signature)) {
                logger.info("Skipping duplicate tool call for '{}' (returning cached result)", name);
                return Mono.just(cache.get(signature));
            }
            return delegate.callTool(name, arguments)
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
