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

import io.agentscope.core.memory.autocontext.AutoContextHook;

import io.agentscope.core.session.Session;

import io.agentscope.core.session.mysql.MysqlSession;

import javax.sql.DataSource;

import io.agentscope.core.memory.mem0.Mem0ApiType;

import io.agentscope.core.message.Msg;

import io.agentscope.core.message.MsgRole;

import io.agentscope.core.message.TextBlock;

import io.agentscope.core.model.Model;

import io.agentscope.core.tool.Toolkit;



import io.agentscope.examples.paasassistant.common.config.McpToolRegistrar;
import io.agentscope.examples.paasassistant.common.stream.ToolNarrator;





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

    @Value("${agentscope.memory.auto-context.max-token:225280}")
    int autoContextMaxToken;

    @Value("${agentscope.memory.auto-context.token-ratio:0.75}")
    double autoContextTokenRatio;

    @Value("${agentscope.memory.auto-context.msg-threshold:300}")
    int autoContextMsgThreshold;

    @Value("${agentscope.memory.auto-context.last-keep:5}")
    int autoContextLastKeep;

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig, AiService aiService, Model model,
            DataSource dataSource) {

        Toolkit toolkit = new Toolkit(io.agentscope.core.tool.ToolkitConfig.builder().parallel(true).build());
        AutoContextConfig autoContextConfig = AutoContextConfig.builder()
                .maxToken(autoContextMaxToken)
                .tokenRatio(autoContextTokenRatio)
                .msgThreshold(autoContextMsgThreshold)
                .lastKeep(autoContextLastKeep)
                .minCompressionTokenThreshold(15000)
                .build();

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
                mcpToolTimeout,
                dataSource);
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
        private final DataSource dataSource;
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
                Duration mcpToolTimeout,
                DataSource dataSource) {
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
            this.dataSource = dataSource;
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
                    .longTermMemoryAsyncRecord(true)
                    .hooks(List.of(new MonitoringHook(), new TruncationHook(), new AutoContextHook()))
                    .maxIters(150)
                    .build();
        }

        private void initializeMcpOnce() {
            if (!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {
                        try {
                            McpToolRegistrar.registerK8sToolGroups(
                                    toolkit, k8sgptMcpUrl, k8sMcpUrl, mcpToolTimeout);
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

                        // Load existing session state from MySQL
                        Session session = new MysqlSession(dataSource, true);
                        agent.loadIfExists(session, options.getTaskId());
                        cleanMemoryMessages(agent.getMemory());

                        if (agent.getMemory().getMessages().isEmpty()) {
                            agent.getMemory()
                                    .addMessage(
                                            Msg.builder()
                                                    .role(MsgRole.USER)
                                                    .content(
                                                            TextBlock.builder()
                                                                    .text("<userId>" + userId + "</userId>")
                                                                    .build())
                                                    .build());
                        }
                        return new Object[]{agent, clusterId, userId, session};
                    })
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .flatMapMany(params -> {
                        ReActAgent agent = (ReActAgent) params[0];
                        String clusterId = (String) params[1];
                        String userId = (String) params[2];
                        Session session = (Session) params[3];
                        return agent.stream(requestMessages, FULL_STREAM_OPTIONS)
                                .contextWrite(ctx -> ctx.put(AgentConstants.CTX_TOOL_CACHE, new ConcurrentHashMap<String, McpSchema.CallToolResult>())
                                        .put(AgentConstants.CTX_CLUSTER_ID, clusterId)
                                        .put(AgentConstants.CTX_USER_ID, userId)
                                        .put(AgentConstants.CTX_CHAT_ID, options.getTaskId()))
                                .doOnComplete(() -> {
                                    try {
                                        cleanMemoryMessages(agent.getMemory());
                                        agent.saveTo(session, options.getTaskId());
                                        logger.info("Successfully saved session state for taskId: {}", options.getTaskId());
                                    } catch (Exception e) {
                                        logger.error("Failed to save session state", e);
                                    }
                                })
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

        private void cleanMemoryMessages(io.agentscope.core.memory.Memory memory) {
            if (memory == null) {
                return;
            }
            cleanMessageList(memory.getMessages());
            try {
                java.lang.reflect.Method getOriginal = memory.getClass().getMethod("getOriginalMemoryMsgs");
                Object origList = getOriginal.invoke(memory);
                if (origList instanceof List<?> list) {
                    cleanMessageList((List<Msg>) list);
                }
            } catch (Exception ignored) {
            }
        }

        private void cleanMessageList(List<Msg> messages) {
            if (messages == null) {
                return;
            }
            for (Msg msg : messages) {
                if (msg == null || msg.getContent() == null) {
                    continue;
                }
                for (Object block : msg.getContent()) {
                    if (block instanceof TextBlock textBlock) {
                        try {
                            java.lang.reflect.Field field = TextBlock.class.getDeclaredField("text");
                            field.setAccessible(true);
                            String rawText = (String) field.get(textBlock);
                            if (rawText != null && !rawText.isEmpty()) {
                                String cleaned = ToolNarrator.cleanLlmTokens(rawText);
                                field.set(textBlock, cleaned);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to clean TextBlock via reflection: {}", e.getMessage());
                        }
                    }
                }
            }
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
