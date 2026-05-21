package io.agentscope.examples.paasassistant.supervisor.utils;

/**
 * Centralized constants for the Supervisor Agent module.
 */
public final class AgentConstants {

    private AgentConstants() {
        // Prevent instantiation
    }

    // -------------------------------------------------------------------------
    // Reactor Context Keys
    // -------------------------------------------------------------------------

    public static final String CTX_USER_ID = "user_id";
    public static final String CTX_TRACE_ID = "trace_id";
    public static final String CTX_CLUSTER_ID = "cluster_id";
    public static final String CTX_SSE_EMITTER = "structuredSseEmitter";

    // -------------------------------------------------------------------------
    // XML Tags (embedded in messages by the supervisor to pass metadata)
    // -------------------------------------------------------------------------

    public static final String TAG_USER_ID = "userId";
    public static final String TAG_TRACE_ID = "traceId";
    public static final String TAG_CLUSTER_ID = "clusterId";

    // -------------------------------------------------------------------------
    // Agent Names
    // -------------------------------------------------------------------------

    public static final String AGENT_NAME_SUPERVISOR = "supervisor_agent";
    public static final String AGENT_NAME_ANALYZE = "analyze_agent";
    public static final String AGENT_NAME_DIAGNOSIS = "diagnosis_agent";
    public static final String AGENT_NAME_GUIDE = "guide_agent";

    // -------------------------------------------------------------------------
    // Model Providers
    // -------------------------------------------------------------------------

    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_OPENAI = "openai";

    // -------------------------------------------------------------------------
    // Common Error / Status Messages
    // -------------------------------------------------------------------------

    public static final String CHILD_AGENT_UNAVAILABLE_MESSAGE =
            "子 Agent 暂时不可用，A2A 流式响应在读取过程中中断。请稍后重试；如果持续出现，请检查子 Agent 服务状态和网络连接。";

    public static final String CHILD_AGENT_TIMEOUT_MESSAGE =
            "子 Agent 执行超时，已取消本次 A2A 任务。请稍后重试，或缩小诊断范围后再次发起。";

    // -------------------------------------------------------------------------
    // Conversation History Sanitizer
    // -------------------------------------------------------------------------

    /**
     * Maximum number of visible user-turns kept in the supervisor's session history.
     * Older turns are dropped to prevent context bloat.
     */
    public static final int DEFAULT_MAX_VISIBLE_TURNS = 6;

    /**
     * Prefix injected at the start of the condensed history reference message that the
     * supervisor prepends to its memory. Used both when writing and when detecting whether
     * an ASSISTANT message represents a history block (to avoid re-sanitizing it).
     */
    public static final String HISTORY_REFERENCE_PREFIX =
            "历史对话参考（仅用于理解省略指代，不是当前任务）：";

    // -------------------------------------------------------------------------
    // Tool Execution
    // -------------------------------------------------------------------------

    /**
     * Fallback display name used when a ToolResultBlock carries a blank or null tool name.
     * Centralised here so monitoring / logging code can match against the same literal.
     */
    public static final String FALLBACK_TOOL_NAME = "unknown_tool";

    /**
     * printf-style format for the one-line tool-result summary emitted to the SSE stream.
     * Argument: the localised tool title from {@code ToolNarrator.titleForTool()}.
     * Example output: "已完成列出 Pod。"
     */
    public static final String TOOL_RESULT_SUMMARY_FORMAT = "已完成%s。";
}
