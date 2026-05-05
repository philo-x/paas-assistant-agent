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
import io.agentscope.examples.paasassistant.diagnosis.utils.MonitoringHook;
import io.agentscope.extensions.nacos.mcp.NacosMcpServerManager;
import io.agentscope.extensions.nacos.mcp.client.NacosMcpClientBuilder;
import io.agentscope.extensions.nacos.mcp.client.NacosMcpClientWrapper;
import io.agentscope.extensions.nacos.mcp.tool.NacosToolkit;
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

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig, AiService aiService, Model model) {

        Toolkit toolkit = new NacosToolkit();
        AutoContextConfig autoContextConfig =
                AutoContextConfig.builder().tokenRatio(0.4).lastKeep(10).build();
        AutoContextMemory memory = new AutoContextMemory(autoContextConfig, model);

        return new CustomAgentRunner(
                "diagnosis_agent",
                promptConfig.getDiagnosisAgentInstruction(),
                model,
                aiService,
                toolkit,
                memory,
                mem0ApiKey,
                mem0BaseUrl,
                mem0ApiType,
                mem0InferEnabled,
                k8sgptMcpUrl);
    }

    private static class CustomAgentRunner implements AgentRunner {

        /**
         * StreamOptions that request REASONING, TOOL_RESULT, and AGENT_RESULT events
         * so that the A2A transport can relay tool-level detail back to the supervisor.
         */
        private static final StreamOptions FULL_STREAM_OPTIONS =
                StreamOptions.builder()
                        .eventTypes(
                                EventType.REASONING,
                                EventType.TOOL_RESULT,
                                EventType.AGENT_RESULT)
                        .incremental(true)
                        .includeReasoningChunk(true)
                        .includeReasoningResult(true)
                        .includeActingChunk(false)
                        .includeSummaryChunk(false)
                        .includeSummaryResult(false)
                        .build();

        private static final Pattern USER_ID_PATTERN =
                Pattern.compile("<userId>(.+?)</userId>");

        private final String agentName;
        private final String sysPrompt;
        private final Model model;
        private final AiService aiService;
        private final Toolkit toolkit;
        private final AutoContextMemory memory;
        private final Map<String, ReActAgent> agentCache;
        private final String mem0ApiKey;
        private final String mem0BaseUrl;
        private final String mem0ApiType;
        private final boolean mem0InferEnabled;
        private final String k8sgptMcpUrl;
        private volatile boolean mcpInitialized = false;

        private CustomAgentRunner(
                String agentName,
                String sysPrompt,
                Model model,
                AiService aiService,
                Toolkit toolkit,
                AutoContextMemory memory,
                String mem0ApiKey,
                String mem0BaseUrl,
                String mem0ApiType,
                boolean mem0InferEnabled,
                String k8sgptMcpUrl) {
            this.agentName = agentName;
            this.sysPrompt = sysPrompt;
            this.model = model;
            this.aiService = aiService;
            this.toolkit = toolkit;
            this.memory = memory;
            this.agentCache = new ConcurrentHashMap<>();
            this.mem0ApiKey = mem0ApiKey;
            this.mem0BaseUrl = mem0BaseUrl;
            this.mem0ApiType = mem0ApiType;
            this.mem0InferEnabled = mem0InferEnabled;
            this.k8sgptMcpUrl = k8sgptMcpUrl;
        }

        private ReActAgent buildReActAgent(String userId) {
            initializeMcpOnce();
            Mem0ApiType apiType = resolveMem0ApiType(mem0BaseUrl, mem0ApiType);
            CompatibleMem0LongTermMemory longTermMemory =
                    new CompatibleMem0LongTermMemory(
                            "DiagnosisAgent",
                            userId,
                            null,
                            Map.of(),
                            mem0BaseUrl,
                            mem0ApiKey,
                            apiType,
                            Duration.ofSeconds(30),
                            resolveInferEnabled(apiType, mem0InferEnabled));

            // Create a fresh builder and agent instance for each request to ensure
            // complete isolation and avoid "Agent is still running" errors caused
            // by shared builder/state.
            return ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(sysPrompt)
                    .model(model)
                    .memory(memory)
                    .toolkit(toolkit)
                    .longTermMemory(longTermMemory)
                    .hooks(List.of(new MonitoringHook()))
                    .build();
        }

        private void initializeMcpOnce() {
            if (!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {
                        try {
                            // Register platform-mcp-server (mutation tools) via Nacos
                            NacosMcpServerManager mcpServerManager =
                                    new NacosMcpServerManager(aiService);
                            NacosMcpClientWrapper mcpClientWrapper =
                                    NacosMcpClientBuilder.create(
                                                    "platform-mcp-server", mcpServerManager)
                                              .build();
                            toolkit.registerMcpClient(mcpClientWrapper).block();

                            // Register K8sGPT (diagnosis tools) via direct HTTP
                            McpClientWrapper k8sgptClient =
                                    McpClientBuilder.create("k8s-mcp-server")
                                              .streamableHttpTransport(k8sgptMcpUrl)
                                              .timeout(Duration.ofSeconds(60))
                                              .buildSync();
                            toolkit.registerMcpClient(k8sgptClient).block();

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

        @Override
        public void stop(String taskId) {
            ReActAgent agent = agentCache.remove(taskId);
            if (agent != null) {
                agent.interrupt();
            }
        }
    }
}
