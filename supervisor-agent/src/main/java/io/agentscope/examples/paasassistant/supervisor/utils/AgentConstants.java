package io.agentscope.examples.paasassistant.supervisor.utils;

/**
 * Centralized constants for the Supervisor Agent module.
 */
public final class AgentConstants {

    private AgentConstants() {
        // Prevent instantiation
    }

    // Context Keys
    public static final String CTX_USER_ID = "user_id";
    public static final String CTX_TRACE_ID = "trace_id";
    public static final String CTX_CLUSTER_ID = "cluster_id";
    public static final String CTX_SSE_EMITTER = "structuredSseEmitter";

    // XML Tags
    public static final String TAG_USER_ID = "userId";
    public static final String TAG_TRACE_ID = "traceId";
    public static final String TAG_CLUSTER_ID = "clusterId";

    // Agent Names
    public static final String AGENT_NAME_SUPERVISOR = "supervisor_agent";
    public static final String AGENT_NAME_DIAGNOSIS = "diagnosis_agent";
    public static final String AGENT_NAME_GUIDE = "guide_agent";

    // Model Providers
    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_OPENAI = "openai";

    // Common Messages
    public static final String CHILD_AGENT_UNAVAILABLE_MESSAGE =
            "子 Agent 暂时不可用，A2A 流式响应在读取过程中中断。请稍后重试；如果持续出现，请检查子 Agent 服务状态和网络连接。";

    public static final String CHILD_AGENT_TIMEOUT_MESSAGE =
            "子 Agent 执行超时，已取消本次 A2A 任务。请稍后重试，或缩小诊断范围后再次发起。";
}
