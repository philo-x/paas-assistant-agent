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

    /** SSE emitter reference used to stream sub-agent events back to the supervisor. */
    public static final String CTX_SSE_EMITTER = "structuredSseEmitter";

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

    /**
     * Shared StreamOptions that request REASONING, TOOL_RESULT, and AGENT_RESULT events
     * so that the A2A transport can relay tool-level detail back to the supervisor.
     */
    public static final io.agentscope.core.agent.StreamOptions FULL_STREAM_OPTIONS = io.agentscope.core.agent.StreamOptions.builder()
            .eventTypes(
                    io.agentscope.core.agent.EventType.REASONING,
                    io.agentscope.core.agent.EventType.TOOL_RESULT,
                    io.agentscope.core.agent.EventType.AGENT_RESULT)
            .incremental(true)
            .includeReasoningChunk(true)
            .includeReasoningResult(false)
            .includeActingChunk(false)
            .includeSummaryChunk(false)
            .includeSummaryResult(false)
            .build();

    // -------------------------------------------------------------------------
    // Model Capabilities & Custom Parameters
    // -------------------------------------------------------------------------

    /** Model name snippet used to identify DeepSeek-compatible models. */
    public static final String MODEL_DEEPSEEK_MARK = "ds";

    /** Request body parameter key for passing custom chat template arguments (vLLM/DeepSeek). */
    public static final String KEY_CHAT_TEMPLATE_KWARGS = "chat_template_kwargs";

    /** Parameter key for enabling/disabling thinking/reasoning. */
    public static final String KEY_THINKING = "thinking";

    /** Parameter key for specifying reasoning effort (e.g. low, medium, high). */
    public static final String KEY_REASONING_EFFORT = "reasoning_effort";

    /** The disabled budget (0 tokens) for thinking reasoning process. */
    public static final int DISABLE_THINKING_BUDGET = 0;

    // -------------------------------------------------------------------------
    // Agent Names & Descriptions
    // -------------------------------------------------------------------------

    public static final String AGENT_NAME_DIAGNOSIS = "diagnosis_agent";
    public static final String AGENT_NAME_ANALYZE = "analyze_agent";
    public static final String AGENT_NAME_GUIDE = "guide_agent";

    public static final String AGENT_DESC_DIAGNOSIS = "Diagnosis agent for PaaS Assistant";
    public static final String AGENT_DESC_ANALYZE = "Quick diagnosis agent for PaaS Assistant";
    public static final String AGENT_DESC_GUIDE = "Guide agent for PaaS Assistant";

    // -------------------------------------------------------------------------
    // Agent Execution Limits
    // -------------------------------------------------------------------------

    public static final int MAX_ITERS_DIAGNOSIS = 150;
    public static final int MAX_ITERS_ANALYZE = 80;
    public static final int MAX_ITERS_GUIDE = 50;

    // -------------------------------------------------------------------------
    // Memory Names & Timeout Defaults
    // -------------------------------------------------------------------------

    public static final String MEMORY_NAME_DIAGNOSIS = "DiagnosisAgent";
    public static final String MEMORY_NAME_ANALYZE = "AnalyzeAgent";
    public static final String MEMORY_NAME_GUIDE = "GuideAgent";

    public static final long DEFAULT_MEMORY_TIMEOUT_SECONDS = 60L;
    public static final int DEFAULT_MIN_COMPRESSION_TOKEN_THRESHOLD = 15000;

    // -------------------------------------------------------------------------
    // Skills Repository Location
    // -------------------------------------------------------------------------

    public static final String SKILL_DIRECTORY = "skills";

    // -------------------------------------------------------------------------
    // Error Markdown Templates for Sub-agents
    // -------------------------------------------------------------------------

    public static final String DIAGNOSIS_ERROR_MARKDOWN_TEMPLATE =
            "\n\n> [!CAUTION]\n> **代理执行异常**\n> \n> 抱歉，诊断过程中遇到了技术故障：\n> `%s`\n> \n> 请尝试精简您的问题，或者稍后重试。";

    public static final String ANALYZE_ERROR_MARKDOWN_TEMPLATE =
            "\n\n> [!CAUTION]\n> **代理执行异常**\n> \n> 抱歉，助手在处理您的请求时遇到了技术故障：\n> `%s`\n> \n> 请尝试精简您的问题，或者稍后重试。";

    // -------------------------------------------------------------------------
    // Controller HTTP Headers, Stages & Comments
    // -------------------------------------------------------------------------

    public static final String HTTP_HEADER_X_ACCEL_BUFFERING = "X-Accel-Buffering";
    public static final String HTTP_HEADER_VALUE_NO = "no";
    public static final String HTTP_HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HTTP_HEADER_VALUE_NO_CACHE = "no-cache";
    public static final String HTTP_HEADER_CONNECTION = "Connection";
    public static final String HTTP_HEADER_VALUE_KEEP_ALIVE = "keep-alive";

    public static final String KEEP_ALIVE_COMMENT = "keep-alive";
    public static final long KEEP_ALIVE_INTERVAL_SECONDS = 15L;

    public static final String SSE_EVENT_ERROR = "error";
    public static final String SSE_EVENT_STAGE_STREAM = "stream";
    public static final String SSE_EVENT_STAGE_REQUEST = "request";
    public static final String SYSTEM_ERROR_MESSAGE = "System processing error, please try again later.";
    public static final String SYSTEM_ERROR_JSON_FORMAT = "{\"message\":\"%s\",\"stage\":\"%s\"}";

    // -------------------------------------------------------------------------
    // Metadata XML Tags (Start & End)
    // -------------------------------------------------------------------------

    public static final String TAG_USER_ID_START = "<userId>";
    public static final String TAG_USER_ID_END = "</userId>";
    public static final String TAG_CLUSTER_ID_START = "<clusterId>";
    public static final String TAG_CLUSTER_ID_END = "</clusterId>";
    public static final String TAG_TRACE_ID_START = "<traceId>";
    public static final String TAG_TRACE_ID_END = "</traceId>";

    // -------------------------------------------------------------------------
    // LongTermMemory Constants & Patterns
    // -------------------------------------------------------------------------

    public static final int MAX_MEMORY_TEXT_LENGTH = 8000;
    public static final Pattern METADATA_TAG_PATTERN = Pattern.compile("<(?:userId|traceId)>.*?</(?:userId|traceId)>", Pattern.DOTALL);
    public static final String TAG_COMPRESSED_HISTORY = "<compressed_history>";
    public static final int MEM0_SEARCH_TOP_K = 5;
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
}


