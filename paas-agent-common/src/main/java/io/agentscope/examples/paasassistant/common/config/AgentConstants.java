package io.agentscope.examples.paasassistant.common.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Central constants repository for the analyze-sub-agent module.
 *
 * <p>All magic strings, Reactor context keys, MCP parameter names, and
 * regex patterns are defined here to avoid scatter across classes and
 * reduce the risk of typo-driven runtime failures.
 */
public final class AgentConstants {

    private AgentConstants() {
        // Utility class – do not instantiate
    }

    // -------------------------------------------------------------------------
    // Reactor Context Keys
    // Keys used when writing/reading values from the Reactor subscriber context.
    // Must match exactly between AgentScopeRunner (writer) and
    // SanitizingMcpClient (reader).
    // -------------------------------------------------------------------------

    /** Per-request call-result cache used to deduplicate identical tool invocations. */
    public static final String CTX_TOOL_CACHE = "tool_cache";

    /** Kubernetes cluster identifier injected into every MCP tool call. */
    public static final String CTX_CLUSTER_ID = "cluster_id";

    /** End-user identifier, threaded through the request context for auditing. */
    public static final String CTX_USER_ID = "user_id";

    /** A2A task/chat identifier used for session-scoped tracking. */
    public static final String CTX_CHAT_ID = "chat_id";

    // -------------------------------------------------------------------------
    // MCP Tool Parameter Names
    // Hidden system-level parameters injected by SanitizingMcpClient before
    // forwarding each call to the upstream MCP server.  They are also filtered
    // out of the tool schema exposed to the LLM.
    // -------------------------------------------------------------------------

    /** MCP parameter: target Kubernetes cluster ID. */
    public static final String PARAM_CLUSTER = "cluster";

    /** MCP parameter: user identifier. */
    public static final String PARAM_USER_ID = "userId";

    /** MCP parameter: chat/task identifier. */
    public static final String PARAM_CHAT_ID = "chatId";

    // -------------------------------------------------------------------------
    // Destructive Tool Prefixes
    // Tool names whose base-name (the part after the last "__") starts with any
    // of these prefixes are classified as destructive.  SanitizingMcpClient
    // intercepts these calls and returns a structured error result directing the
    // LLM to use the platform change-plan workflow instead.
    // -------------------------------------------------------------------------

    public static final List<String> DESTRUCTIVE_TOOL_PREFIXES = List.of(
            "delete_", "restart_", "scale_", "stop_", "update_", "apply_",
            "cordon_", "drain_", "taint_", "uncordon_", "untaint_",
            "patch_", "restore_", "undo_"
    );

    // -------------------------------------------------------------------------
    // Message-parsing Patterns
    // XML-tag-delimited values embedded in the user message by the supervisor.
    // -------------------------------------------------------------------------

    /** Extracts the userId from {@code <userId>…</userId>} in message text. */
    public static final Pattern USER_ID_PATTERN = Pattern.compile("<userId>(.+?)</userId>");

    /** Extracts the clusterId from {@code <clusterId>…</clusterId>} in message text. */
    public static final Pattern CLUSTER_ID_PATTERN = Pattern.compile("<clusterId>(.+?)</clusterId>");


    // Default / Fallback Values
    // -------------------------------------------------------------------------

    /** Fallback userId when none can be parsed from the incoming messages. */
    public static final String DEFAULT_USER_ID = "default_userId";
}
