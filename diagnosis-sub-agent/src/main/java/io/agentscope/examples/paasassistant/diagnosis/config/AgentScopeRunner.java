package io.agentscope.examples.paasassistant.diagnosis.config;
import io.agentscope.examples.paasassistant.common.config.AgentPromptConfig;
import io.agentscope.examples.paasassistant.common.config.AgentConstants;
import io.agentscope.examples.paasassistant.common.hooks.TruncationHook;
import io.agentscope.examples.paasassistant.common.hooks.MonitoringHook;
import io.agentscope.examples.paasassistant.common.memory.CompatibleMem0LongTermMemory;



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



import io.agentscope.core.tool.mcp.McpClientBuilder;

import io.agentscope.core.tool.mcp.McpClientWrapper;

import io.agentscope.examples.paasassistant.common.utils.SanitizingMcpClient;





import io.agentscope.core.skill.SkillBox;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;

import io.agentscope.core.skill.repository.AgentSkillRepository;

import io.agentscope.core.skill.AgentSkill;

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

    @Value("${agentscope.mem0.infer-enabled:true}")
    boolean mem0InferEnabled;

    @Value("${agentscope.mcp.k8sgpt-mcp-url:http://localhost:8089/mcp}")
    String k8sgptMcpUrl;

    @Value("${agentscope.mcp.k8s-mcp-url:http://localhost:9096/mcp}")
    String k8sMcpUrl;

    @Value("${agentscope.mcp.tool-timeout:PT60S}")
    Duration mcpToolTimeout;

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig, AiService aiService, Model model) {

        Toolkit toolkit = new Toolkit(io.agentscope.core.tool.ToolkitConfig.builder().parallel(false).build());
        AutoContextConfig autoContextConfig = AutoContextConfig.builder().tokenRatio(0.4).lastKeep(10).build();

        AgentSkillRepository skillRepository;
        SkillBox skillBox = new SkillBox(toolkit);
        try {
            skillRepository = new ClasspathSkillRepository("skills");
            for (AgentSkill skill : skillRepository.getAllSkills()) {
                skillBox.registration().skill(skill).apply();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load skills", e);
        }

        return new CustomAgentRunner(
                "diagnosis_agent",
                promptConfig.getAgentInstruction(),
                model,
                aiService,
                toolkit,
                skillBox,
                autoContextConfig,
                mem0ApiKey,
                mem0BaseUrl,
                mem0ApiType,
                mem0InferEnabled,
                k8sgptMcpUrl,
                k8sMcpUrl,
                mcpToolTimeout);
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


        private final String agentName;
        private final String sysPrompt;
        private final Model model;
        private final Toolkit toolkit;
        private final SkillBox skillBox;
        private final AutoContextConfig autoContextConfig;
        private final Map<String, ReActAgent> agentCache;
        private final String mem0ApiKey;
        private final String mem0BaseUrl;
        private final String mem0ApiType;
        private final boolean mem0InferEnabled;
        private final String k8sMcpUrl;
        private final String k8sgptMcpUrl;
        private final Duration mcpToolTimeout;
        private volatile boolean mcpInitialized = false;

        private CustomAgentRunner(
                String agentName,
                String sysPrompt,
                Model model,
                AiService aiService,
                Toolkit toolkit,
                SkillBox skillBox,
                AutoContextConfig autoContextConfig,
                String mem0ApiKey,
                String mem0BaseUrl,
                String mem0ApiType,
                boolean mem0InferEnabled,
                String k8sgptMcpUrl,
                String k8sMcpUrl,
                Duration mcpToolTimeout) {
            this.agentName = agentName;
            this.sysPrompt = sysPrompt;
            this.model = model;
            this.toolkit = toolkit;
            this.skillBox = skillBox;
            this.autoContextConfig = autoContextConfig;
            this.agentCache = new ConcurrentHashMap<>();
            this.mem0ApiKey = mem0ApiKey;
            this.mem0BaseUrl = mem0BaseUrl;
            this.mem0ApiType = mem0ApiType;
            this.mem0InferEnabled = mem0InferEnabled;
            this.k8sMcpUrl = k8sMcpUrl;
            this.k8sgptMcpUrl = k8sgptMcpUrl;
            this.mcpToolTimeout = mcpToolTimeout;
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
                    Duration.ofSeconds(60),
                    resolveInferEnabled(apiType, mem0InferEnabled));

            return ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(sysPrompt)
                    .model(model)
                    .memory(new AutoContextMemory(autoContextConfig, model))
                    .toolkit(toolkit)
                    .skillBox(skillBox)
                    .longTermMemory(longTermMemory)
                    .hooks(List.of(new MonitoringHook(), new TruncationHook()))
                    .maxIters(150)
                    .build();
        }

        private void initializeMcpOnce() {
            if (!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {
                        try {
                            // k8sgpt mcp 工具注册
                            McpClientWrapper k8sgptMcpClient = McpClientBuilder.create("k8sgpt-mcp-server")
                                    .streamableHttpTransport(k8sgptMcpUrl)
                                    .timeout(mcpToolTimeout)
                                    .buildSync();
                            // 1. 资源问题快速分析
                            toolkit.createToolGroup("k8s_resource_analyze", "k8s资源问题快速分析", true);
                            toolkit.registration().mcpClient(k8sgptMcpClient)
                                    .enableTools(List.of(
                                            "analyze",
                                            "list-filters",
                                            "add-filters"))
                                    .group("k8s_resource_analyze")
                                    .apply();


                            // Register standard local MCP service via SSE
                            McpClientWrapper komMcpClient = McpClientBuilder.create("kom-mcp-server")
                                    .streamableHttpTransport(k8sMcpUrl)
                                    .timeout(mcpToolTimeout)
                                    .buildSync();
                                    
                            McpClientWrapper sanitizedK8sClient = new SanitizingMcpClient(komMcpClient);


                            // 1. 集群管理 (Cluster Management)
                            toolkit.createToolGroup("cluster_management", "Kubernetes多集群管理，支持列出/查询可用K8s集群列表", true);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of("list_k8s_clusters"))
                                    .group("cluster_management").apply();

                            // 2. 节点管理 (Node)
                            toolkit.createToolGroup("node_management", "Kubernetes节点管理，包含节点IP/资源占用/运行Pod数量查询，支持系统日志检索和内核dmesg OOM排查", false);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of(
                                            "list_k8s_node","get_k8s_node_ip_usage",
                                            "get_k8s_top_node", "get_k8s_node_resource_usage",
                                            "get_k8s_pod_count_running_on_node",
                                             "get_k8s_node_system_logs", "get_k8s_node_dmesg_oom"))
                                    .group("node_management").apply();

                            // 3. 部署管理 (Deployment)
                            toolkit.createToolGroup("deployment_management", "工作负载与部署管理，包含Deployment的Rollout历史与状态查询，HPA配置列表及资源历史指标(PromQL历史曲线)", true);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of(
                                            "get_k8s_deployment_rollout_history", "get_k8s_deployment_rollout_status",
                                            "get_k8s_deployment_hpa_list","get_k8s_resource_metrics_history"))
                                    .group("deployment_management").apply();


                            // 4. Pod管理
                            toolkit.createToolGroup("pod_management", "Pod生命周期与容器内诊断管理，支持Pod日志拉取、环境变量与关联Services/Endpoints查询、容器内命令执行与文件列表检索", true);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of(
                                            "describe_k8s_pod","list_k8s_pod","list_k8s_pod_event",
                                            "get_k8s_pod_logs","get_k8s_pod_linked_env","get_pod_linked_env_from_yaml",
                                             "get_k8s_pod_linked_services","get_pod_linked_endpoints",
                                            "get_k8s_pod_resource_usage","get_k8s_top_pod",
                                             "list_files_in_k8s_pod","list_pod_all_files",
                                            "run_command_in_k8s_pod"))
                                    .group("pod_management").apply();

                            // 5. 事件管理 (Event)
                            toolkit.createToolGroup("event_management", "Kubernetes事件诊断管理，支持通过命名空间和涉及对象名称/类型过滤检索集群事件", true);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of("list_k8s_event"))
                                    .group("event_management").apply();

                            // 6. 资源管理 (Dynamic Resource/CRD)
                            toolkit.createToolGroup("dynamic_resource_management", "Kubernetes任意资源与CRD动态诊断管理，支持命名空间列表查询以及任意特定类型K8s资源(如Quota等)的Get/List/Describe", false);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of(
                                            "describe_k8s_resource", "get_k8s_resource",
                                            "list_k8s_resource", "list_k8s_namespace"))
                                    .group("dynamic_resource_management").apply();

                            // 7. 存储管理 (Storage)
                            toolkit.createToolGroup("storage_management", "持久化存储与PV/PVC管理，支持Pod关联存储卷绑定状态、StorageClass的PV/PVC数量统计及CSI驱动挂载卡死故障诊断", false);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of(
                                            "get_k8s_pod_linked_pv", "get_k8s_pod_linked_pvc",
                                            "get_k8s_storageclass_pv_count", "get_k8s_storageclass_pvc_count",
                                            "diagnose_k8s_csi_driver"))
                                    .group("storage_management").apply();

                            // 8. Ingress与网络路由管理
                            toolkit.createToolGroup("ingress_management", "网络连通性与路由管理，支持Pod关联Ingress路由查询、Pod内网络连通性探测(nc/curl/wget/临时诊断Pod测试)及CoreDNS域名解析测试", false);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of(
                                            "get_pod_linked_ingresses", "diagnose_k8s_pod_network",
                                            "test_k8s_dns_resolve"))
                                    .group("ingress_management").apply();

                            // 9. Kubectl 兜底管理
                            toolkit.createToolGroup("kubectl_management", "Kubectl只读兜底命令行管理，支持在专用MCP工具受限时执行只读命令(如get/describe/logs -p等)", false);
                            toolkit.registration().mcpClient(sanitizedK8sClient)
                                    .enableTools(List.of("kubectl"))
                                    .group("kubectl_management").apply();

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
            return configuredInferEnabled;
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

            return reactor.core.publisher.Mono.fromCallable(() -> {
                        String userId = parseUserIdFromMessages(requestMessages);
                        String clusterId = parseClusterIdFromMessages(requestMessages);
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
                        return new Object[]{agent, clusterId, userId};
                    })
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .flatMapMany(params -> {
                        ReActAgent agent = (ReActAgent) params[0];
                        String clusterId = (String) params[1];
                        String userId = (String) params[2];
                        return agent.stream(requestMessages, FULL_STREAM_OPTIONS)
                                .contextWrite(ctx -> ctx.put(AgentConstants.CTX_TOOL_CACHE, new ConcurrentHashMap<String, McpSchema.CallToolResult>())
                                        .put(AgentConstants.CTX_CLUSTER_ID, clusterId)
                                        .put(AgentConstants.CTX_USER_ID, userId)
                                        .put(AgentConstants.CTX_CHAT_ID, options.getTaskId()))
                                .doFinally(signal -> {
                                    ReActAgent cachedAgent = agentCache.remove(options.getTaskId());
                                    if (cachedAgent != null) {
                                        logger.info("Interrupting agent {} in doFinally on signal: {}", options.getTaskId(), signal);
                                        cachedAgent.interrupt();
                                    }
                                });
                    })
                    .onErrorResume(e -> {
                        logger.error("Error during agent stream for taskId {}: ", options.getTaskId(), e);
                        Msg errorMsg = Msg.builder()
                                .role(MsgRole.ASSISTANT)
                                .content(TextBlock.builder()
                                        .text("\n\n> [!CAUTION]\n> **代理执行异常**\n> \n> 抱歉，诊断过程中遇到了技术故障：\n> `" + e.getMessage() + "`\n> \n> 请尝试精简您的问题，或者稍后重试。")
                                        .build())
                                .build();
                        return Flux.just(new Event(EventType.AGENT_RESULT, errorMsg, false));
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
                            Matcher matcher = AgentConstants.USER_ID_PATTERN.matcher(text);
                            if (matcher.find()) {
                                return matcher.group(1).trim();
                            }
                        }
                    }
                }
            }
            return AgentConstants.DEFAULT_USER_ID;
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
                            Matcher matcher = AgentConstants.CLUSTER_ID_PATTERN.matcher(text);
                            if (matcher.find()) {
                                return matcher.group(1).trim();
                            }
                        }
                    }
                }
            }
            return "";
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
