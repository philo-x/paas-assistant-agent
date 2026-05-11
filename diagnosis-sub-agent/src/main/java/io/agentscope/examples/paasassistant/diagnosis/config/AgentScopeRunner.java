package io.agentscope.examples.paasassistant.diagnosis.config;

import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.paasassistant.diagnosis.memory.CompatibleMem0LongTermMemory;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.examples.paasassistant.diagnosis.utils.SanitizingMcpClient;
import io.agentscope.examples.paasassistant.diagnosis.hooks.MonitoringHook;
import io.agentscope.examples.paasassistant.diagnosis.hooks.TruncationHook;
import io.agentscope.extensions.nacos.mcp.tool.NacosToolkit;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

@Configuration
public class AgentScopeRunner {

    private static final Logger logger = LoggerFactory.getLogger(AgentScopeRunner.class);

    @Value("${agentscope.mem0.api-key:}")
    String mem0ApiKey;

    @Value("${agentscope.mem0.base-url:https://api.mem0.ai}")
    String mem0BaseUrl;

    @Value("${agentscope.mem0.api-type:auto}")
    String mem0ApiType;

    @Value("${agentscope.mem0.infer-enabled:false}")
    boolean mem0InferEnabled;

    @Value("${agentscope.k8sgpt.mcp-url:http://k8sgpt-server:8089/mcp}")
    String k8sgptMcpUrl;

    @Value("${agentscope.mcp.k8s-mcp-url:http://localhost:9096/mcp}")
    String k8sMcpUrl;

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig, AiService aiService, Model model) {

        Toolkit toolkit = new NacosToolkit();
        AutoContextConfig autoContextConfig = AutoContextConfig.builder().tokenRatio(0.4).lastKeep(10).build();

        return new CustomAgentRunner(
                "diagnosis_agent",
                promptConfig.getDiagnosisAgentInstruction(),
                model,
                aiService,
                toolkit,
                autoContextConfig,
                mem0ApiKey,
                mem0BaseUrl,
                mem0ApiType,
                mem0InferEnabled,
                k8sgptMcpUrl,
                k8sMcpUrl);
    }

    private static class CustomAgentRunner implements AgentRunner {

        /**
         * StreamOptions that request REASONING, TOOL_RESULT, and AGENT_RESULT events
         * so that the A2A transport can relay tool-level detail back to the supervisor.
         *
         * <p>MUST stay byte-for-byte in sync with:
         * <ul>
         *   <li>{@code guide-sub-agent}'s {@code FULL_STREAM_OPTIONS}
         *   <li>{@code supervisor-agent}'s {@code A2aAgentTools.CHILD_AGENT_STREAM_OPTIONS}
         * </ul>
         *
         * <p>{@code includeActingChunk(false)}: we don't need streaming partial tool input/output;
         * the structured timeline only renders "tool started" / "tool completed" markers, and
         * fragmented acting chunks would force the supervisor to dedup repeated TOOL_RESULT
         * events. Disable here so the framework only emits one complete TOOL_RESULT per call.
         */
        private static final StreamOptions FULL_STREAM_OPTIONS = StreamOptions.builder()
                .eventTypes(
                        EventType.REASONING,
                        EventType.TOOL_RESULT,
                        EventType.AGENT_RESULT)
                .incremental(true)
                .includeReasoningChunk(true)
                .includeReasoningResult(false)
                .includeActingChunk(false)
                .includeSummaryChunk(false)
                .includeSummaryResult(false)
                .build();

        private static final Pattern USER_ID_PATTERN = Pattern.compile("<userId>(.+?)</userId>");
        private static final Pattern CLUSTER_ID_PATTERN = Pattern.compile("<clusterId>(.+?)</clusterId>");

        private final String agentName;
        private final String sysPrompt;
        private final Model model;
        private final AiService aiService;
        private final Toolkit toolkit;
        private final AutoContextConfig autoContextConfig;
        private final Map<String, ReActAgent> agentCache;
        private final String mem0ApiKey;
        private final String mem0BaseUrl;
        private final String mem0ApiType;
        private final boolean mem0InferEnabled;
        private final String k8sgptMcpUrl;
        private final String k8sMcpUrl;
        private volatile boolean mcpInitialized = false;

        private CustomAgentRunner(
                String agentName,
                String sysPrompt,
                Model model,
                AiService aiService,
                Toolkit toolkit,
                AutoContextConfig autoContextConfig,
                String mem0ApiKey,
                String mem0BaseUrl,
                String mem0ApiType,
                boolean mem0InferEnabled,
                String k8sgptMcpUrl,
                String k8sMcpUrl) {
            this.agentName = agentName;
            this.sysPrompt = sysPrompt;
            this.model = model;
            this.aiService = aiService;
            this.toolkit = toolkit;
            this.autoContextConfig = autoContextConfig;
            this.agentCache = new ConcurrentHashMap<>();
            this.mem0ApiKey = mem0ApiKey;
            this.mem0BaseUrl = mem0BaseUrl;
            this.mem0ApiType = mem0ApiType;
            this.mem0InferEnabled = mem0InferEnabled;
            this.k8sgptMcpUrl = k8sgptMcpUrl;
            this.k8sMcpUrl = k8sMcpUrl;
        }

        private ReActAgent buildReActAgent(String userId) {
            initializeMcpOnce();
            Mem0ApiType apiType = resolveMem0ApiType(mem0BaseUrl, mem0ApiType);
            CompatibleMem0LongTermMemory longTermMemory = new CompatibleMem0LongTermMemory(
                    "DiagnosisAgent",
                    userId,
                    null,
                    Map.of(),
                    mem0BaseUrl,
                    mem0ApiKey,
                    apiType,
                    Duration.ofSeconds(30),
                    resolveInferEnabled(apiType, mem0InferEnabled));

            return ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(sysPrompt)
                    .model(model)
                    .memory(new AutoContextMemory(autoContextConfig, model))
                    .toolkit(toolkit)
                    .longTermMemory(longTermMemory)
                    .hooks(List.of(new MonitoringHook(), new TruncationHook()))
                    .build();
        }

        private void initializeMcpOnce() {
            if (!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {
                        try {
                            // Register standard local MCP service via SSE
                            McpClientWrapper rawMcpClient = McpClientBuilder.create("k8s-mcp-server")
                                    .streamableHttpTransport(k8sMcpUrl)
                                    .timeout(Duration.ofSeconds(60))
                                    .buildSync();
                                    
                            McpClientWrapper localMcpClient = new SanitizingMcpClient(rawMcpClient);

                            // 1. 集群管理 (Cluster Management)
                            toolkit.createToolGroup("cluster_management", "Kubernetes集群的注册与列表管理", true);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of("list_k8s_clusters", "register_k8s_cluster", "unregister_k8s_cluster"))
                                    .group("cluster_management").apply();

                            // 2. DaemonSet管理
                            toolkit.createToolGroup("daemonset_management", "DaemonSet重启与管理", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of("restart_k8s_daemonset"))
                                    .group("daemonset_management").apply();

                            // 3. 部署管理 (Deployment)
                            toolkit.createToolGroup("deployment_management", "Deployment的伸缩、重启、回滚与状态管理", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of(
                                            "get_k8s_deployment_hpa_list", "get_k8s_deployment_rollout_history",
                                            "get_k8s_deployment_rollout_status", "list_k8s_deploy_event",
                                            "pause_k8s_deployment_rollout", "restart_k8s_deployment",
                                            "restore_k8s_deployment", "resume_k8s_deployment_rollout",
                                            "scale_k8s_deployment", "stop_k8s_deployment",
                                            "undo_k8s_deployment_rollout", "update_k8s_deployment_image_tag"))
                                    .group("deployment_management").apply();

                            // 4. 动态资源管理 (Dynamic Resource/CRD)
                            toolkit.createToolGroup("dynamic_resource_management", "Kubernetes任意资源(含CRD)的动态增删改查", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of(
                                            "annotate_k8s_resource", "delete_k8s_resource",
                                            "describe_k8s_resource", "get_k8s_resource",
                                            "label_k8s_resource", "list_k8s_resource",
                                            "patch_k8s_resource", "list_k8s_namespace"))
                                    .group("dynamic_resource_management").apply();

                            // 5. 节点管理 (Node)
                            toolkit.createToolGroup("node_management", "节点状态、资源占用及调度管理", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of(
                                            "cordon_k8s_node", "drain_k8s_node",
                                            "get_k8s_node_ip_usage", "get_k8s_node_resource_usage",
                                            "get_k8s_pod_count_running_on_node", "get_k8s_top_node",
                                            "list_k8s_node", "taint_k8s_node",
                                            "uncordon_k8s_node", "untaint_k8s_node"))
                                    .group("node_management").apply();

                            // 6. 事件管理 (Event)
                            toolkit.createToolGroup("event_management", "Kubernetes集群事件查询", true);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of("list_k8s_event"))
                                    .group("event_management").apply();

                            // 7. Ingress管理
                            toolkit.createToolGroup("ingress_management", "Ingress路由与配置管理", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of("get_pod_linked_ingresses", "set_default_k8s_ingressclass"))
                                    .group("ingress_management").apply();

                            // 8. Pod管理
                            toolkit.createToolGroup("pod_management", "Pod的生命周期、日志、执行与关联资源管理", true);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of(
                                            "delete_k8s_pod", "delete_pod_file", "describe_k8s_pod",
                                            "get_k8s_pod_linked_env", "get_k8s_pod_linked_services",
                                            "get_k8s_pod_logs", "get_k8s_pod_resource_usage",
                                            "get_k8s_top_pod", "get_pod_linked_endpoints",
                                            "get_pod_linked_env_from_yaml", "list_files_in_k8s_pod",
                                            "list_k8s_pod", "list_k8s_pod_event", "list_pod_all_files",
                                            "run_command_in_k8s_pod", "upload_file_to_k8s_pod"))
                                    .group("pod_management").apply();

                            // 9. 存储管理 (Storage)
                            toolkit.createToolGroup("storage_management", "PV、PVC与StorageClass管理", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of(
                                            "get_k8s_pod_linked_pv", "get_k8s_pod_linked_pvc",
                                            "get_k8s_storageclass_pv_count", "get_k8s_storageclass_pvc_count",
                                            "set_k8s_default_storageclass"))
                                    .group("storage_management").apply();

                            // 10. YAML管理
                            toolkit.createToolGroup("yaml_management", "通过YAML文件部署或删除资源", false);
                            toolkit.registration().mcpClient(localMcpClient)
                                    .enableTools(List.of("apply_k8s_yaml", "delete_k8s_yaml"))
                                    .group("yaml_management").apply();

                            // 注册 Meta Tool，允许 LLM 在运行时动态激活或停用上述工具组
                            toolkit.registerMetaTool();

                            mcpInitialized = true;
                        } catch (Exception exception) {
                            logger.warn(
                                    "Failed to initialize MCP client: {}",
                                    exception.getMessage());
                        }
                    }
                }
            }
        }

        private Mem0ApiType resolveMem0ApiType(String baseUrl, String configuredApiType) {
            if (configuredApiType != null
                    && !configuredApiType.isBlank()
                    && !"auto".equalsIgnoreCase(configuredApiType)) {
                return Mem0ApiType.fromString(configuredApiType);
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                return Mem0ApiType.PLATFORM;
            }
            String normalizedBaseUrl = baseUrl.toLowerCase();
            if (normalizedBaseUrl.contains("api.mem0.ai")) {
                return Mem0ApiType.PLATFORM;
            }
            return Mem0ApiType.SELF_HOSTED;
        }

        private boolean resolveInferEnabled(Mem0ApiType apiType, boolean configuredInferEnabled) {
            if (apiType == Mem0ApiType.SELF_HOSTED) {
                return configuredInferEnabled;
            }
            return true;
        }

        @Override
        public String getAgentName() {
            return agentName;
        }

        @Override
        public String getAgentDescription() {
            return "Diagnosis agent for PaaS Assistant";
        }

        @Override
        public Flux<Event> stream(List<Msg> requestMessages, AgentRequestOptions options) {
            if (agentCache.containsKey(options.getTaskId())) {
                throw new IllegalStateException(
                        "Agent already exists for taskId: " + options.getTaskId());
            }
            String userId = parseUserIdFromMessages(requestMessages);
            String clusterId = parseClusterIdFromMessages(requestMessages);
            java.util.Set<String> approvedTools = parseApprovedTools(requestMessages);
            // Per-request isolation: create a fresh agent instance
            ReActAgent agent = buildReActAgent(userId);
            agentCache.put(options.getTaskId(), agent);

            agent.getMemory()
                    .addMessage(
                            Msg.builder()
                                    .role(MsgRole.USER)
                                    .content(
                                            TextBlock.builder()
                                                    .text("<userId>" + userId + "</userId>")
                                                    .build())
                                    .build());

            return agent.stream(requestMessages, FULL_STREAM_OPTIONS)
                    .contextWrite(ctx -> ctx.put("tool_cache", new ConcurrentHashMap<String, McpSchema.CallToolResult>())
                                            .put("approved_tools", approvedTools)
                                            .put("cluster_id", clusterId))
                    .onErrorResume(e -> {
                        logger.error("Error during agent stream for taskId {}: ", options.getTaskId(), e);
                        Msg errorMsg = Msg.builder()
                                .role(MsgRole.ASSISTANT)
                                .content(TextBlock.builder()
                                        .text("\n\n> [!CAUTION]\n> **代理执行异常**\n> \n> 抱歉，诊断过程中遇到了技术故障：\n> `" + e.getMessage() + "`\n> \n> 请尝试精简您的问题，或者稍后重试。")
                                        .build())
                                .build();
                        return Flux.just(new Event(EventType.AGENT_RESULT, errorMsg, false));
                    })
                    .doFinally(signal -> {
                        agentCache.remove(options.getTaskId());
                    });
        }

        private String parseUserIdFromMessages(List<Msg> requestMessages) {
            for (Msg msg : requestMessages) {
                if (msg.getContent() == null) {
                    continue;
                }
                for (var block : msg.getContent()) {
                    if (block instanceof TextBlock textBlock) {
                        String text = textBlock.getText();
                        if (text != null) {
                            Matcher matcher = USER_ID_PATTERN.matcher(text);
                            if (matcher.find()) {
                                return matcher.group(1).trim();
                            }
                        }
                    }
                }
            }
            return "default_userId";
        }

        private String parseClusterIdFromMessages(List<Msg> requestMessages) {
            for (Msg msg : requestMessages) {
                if (msg.getContent() == null) {
                    continue;
                }
                for (var block : msg.getContent()) {
                    if (block instanceof TextBlock textBlock) {
                        String text = textBlock.getText();
                        if (text != null) {
                            Matcher matcher = CLUSTER_ID_PATTERN.matcher(text);
                            if (matcher.find()) {
                                return matcher.group(1).trim();
                            }
                        }
                    }
                }
            }
            return "";
        }

        private java.util.Set<String> parseApprovedTools(List<Msg> requestMessages) {
            java.util.Set<String> approved = new java.util.HashSet<>();
            for (Msg msg : requestMessages) {
                if (msg.getContent() != null) {
                    for (var block : msg.getContent()) {
                        if (block instanceof TextBlock textBlock) {
                            String text = textBlock.getText();
                            if (text != null) {
                                Matcher m = java.util.regex.Pattern.compile("\\[APPROVE\\]\\s+([a-zA-Z0-9_\\-]+)").matcher(text);
                                while (m.find()) {
                                    approved.add(m.group(1));
                                }
                            }
                        }
                    }
                }
            }
            return approved;
        }

        @Override
        public void stop(String taskId) {
            ReActAgent agent = agentCache.remove(taskId);
            if (agent != null) {
                agent.interrupt();
            }
        }
    }
}
