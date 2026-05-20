package io.agentscope.examples.paasassistant.guide.config;

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
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.paasassistant.guide.memory.CompatibleMem0LongTermMemory;
import io.agentscope.examples.paasassistant.guide.tools.GuideTools;
import io.agentscope.examples.paasassistant.guide.hooks.MonitoringHook;
import io.agentscope.examples.paasassistant.guide.hooks.TruncationHook;
import io.agentscope.extensions.nacos.mcp.tool.NacosToolkit;
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

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig,
            GuideTools guideTools,
            Knowledge knowledge,
            Model model) {

        Toolkit toolkit = new NacosToolkit();
        toolkit.registerTool(guideTools);

        AutoContextConfig autoContextConfig = AutoContextConfig.builder().tokenRatio(0.2).lastKeep(10).build();

        return new CustomAgentRunner(
                "guide_agent",
                promptConfig.getGuideAgentInstruction(),
                model,
                toolkit,
                autoContextConfig,
                knowledge,
                mem0ApiKey,
                mem0BaseUrl,
                mem0ApiType,
                mem0InferEnabled);
    }

    private static class CustomAgentRunner implements AgentRunner {

        /**
         * StreamOptions that request REASONING, TOOL_RESULT, and AGENT_RESULT events
         * so that the A2A transport can relay tool-level detail back to the supervisor.
         *
         * <p>MUST stay byte-for-byte in sync with:
         * <ul>
         *   <li>{@code diagnosis-sub-agent}'s {@code FULL_STREAM_OPTIONS}
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

        private final String agentName;
        private final String sysPrompt;
        private final Model model;
        private final Toolkit toolkit;
        private final AutoContextConfig autoContextConfig;
        private final Knowledge knowledge;
        private final Map<String, ReActAgent> agentCache;
        private final String mem0ApiKey;
        private final String mem0BaseUrl;
        private final String mem0ApiType;
        private final boolean mem0InferEnabled;

        private CustomAgentRunner(
                String agentName,
                String sysPrompt,
                Model model,
                Toolkit toolkit,
                AutoContextConfig autoContextConfig,
                Knowledge knowledge,
                String mem0ApiKey,
                String mem0BaseUrl,
                String mem0ApiType,
                boolean mem0InferEnabled) {
            this.agentName = agentName;
            this.sysPrompt = sysPrompt;
            this.model = model;
            this.toolkit = toolkit;
            this.autoContextConfig = autoContextConfig;
            this.knowledge = knowledge;
            this.agentCache = new ConcurrentHashMap<>();
            this.mem0ApiKey = mem0ApiKey;
            this.mem0BaseUrl = mem0BaseUrl;
            this.mem0ApiType = mem0ApiType;
            this.mem0InferEnabled = mem0InferEnabled;
        }

        private ReActAgent buildReActAgent(String userId) {
            Mem0ApiType apiType = resolveMem0ApiType(mem0BaseUrl, mem0ApiType);
            CompatibleMem0LongTermMemory longTermMemory = new CompatibleMem0LongTermMemory(
                    "GuideAgent",
                    userId,
                    null,
                    Map.of(),
                    mem0BaseUrl,
                    mem0ApiKey,
                    apiType,
                    java.time.Duration.ofSeconds(30),
                    resolveInferEnabled(apiType, mem0InferEnabled));

            return ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(sysPrompt)
                    .model(model)
                    .memory(new AutoContextMemory(autoContextConfig, model))
                    .toolkit(toolkit)
                    .knowledge(knowledge)
                    .ragMode(RAGMode.AGENTIC)
                    .longTermMemory(longTermMemory)
                    .hooks(List.of(new MonitoringHook(), new TruncationHook()))
                    .maxIters(50)
                    .build();
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
            return "Guide agent for PaaS Assistant";
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
                    .onErrorResume(e -> {
                        logger.error("Error during agent stream for taskId {}: ", options.getTaskId(), e);
                        Msg errorMsg = Msg.builder()
                                .role(MsgRole.ASSISTANT)
                                .content(TextBlock.builder()
                                        .text("\n\n> [!CAUTION]\n> **代理执行异常**\n> \n> 抱歉，助手在处理您的请求时遇到了技术故障：\n> `" + e.getMessage() + "`\n> \n> 请尝试精简您的问题，或者稍后重试。")
                                        .build())
                                .build();
                        return Flux.just(new Event(EventType.AGENT_RESULT, errorMsg, false));
                    })
                    .doFinally(signal -> {
                        ReActAgent cachedAgent = agentCache.remove(options.getTaskId());
                        if (cachedAgent != null) {
                            logger.info("Interrupting agent {} in doFinally on signal: {}", options.getTaskId(), signal);
                            cachedAgent.interrupt();
                        }
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
