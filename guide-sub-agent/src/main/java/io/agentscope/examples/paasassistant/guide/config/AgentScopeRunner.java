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
import io.agentscope.examples.paasassistant.guide.hooks.A2aStreamingHook;
import io.agentscope.examples.paasassistant.guide.utils.MonitoringHook;
import io.agentscope.extensions.nacos.mcp.tool.NacosToolkit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

@Configuration
public class AgentScopeRunner {

    @Value("${agentscope.mem0.api-key:}")
    String mem0ApiKey;

    @Value("${agentscope.mem0.base-url:https://api.mem0.ai}")
    String mem0BaseUrl;

    @Value("${agentscope.mem0.api-type:auto}")
    String mem0ApiType;

    @Value("${agentscope.mem0.infer-enabled:false}")
    boolean mem0InferEnabled;

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig,
            GuideTools guideTools,
            Knowledge knowledge,
            Model model) {

        Toolkit toolkit = new NacosToolkit();
        toolkit.registerTool(guideTools);

        AutoContextConfig autoContextConfig =
                AutoContextConfig.builder().tokenRatio(0.4).lastKeep(10).build();
        AutoContextMemory memory = new AutoContextMemory(autoContextConfig, model);

        ReActAgent.Builder builder =
                ReActAgent.builder()
                        .name("guide_agent")
                        .sysPrompt(promptConfig.getGuideAgentInstruction())
                        .memory(memory)
                        .hooks(List.of(new MonitoringHook()))
                        .model(model)
                        .toolkit(toolkit)
                        .knowledge(knowledge)
                        .ragMode(RAGMode.AGENTIC);

        return new CustomAgentRunner(
                builder,
                mem0ApiKey,
                mem0BaseUrl,
                mem0ApiType,
                mem0InferEnabled);
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
                        .includeReasoningResult(false)
                        .includeActingChunk(false)
                        .includeSummaryChunk(false)
                        .includeSummaryResult(false)
                        .build();

        private static final Pattern USER_ID_PATTERN =
                Pattern.compile("<userId>(.+?)</userId>");

        private final ReActAgent.Builder agentBuilder;
        private final Map<String, ReActAgent> agentCache;
        private final String mem0ApiKey;
        private final String mem0BaseUrl;
        private final String mem0ApiType;
        private final boolean mem0InferEnabled;

        private CustomAgentRunner(
                ReActAgent.Builder agentBuilder,
                String mem0ApiKey,
                String mem0BaseUrl,
                String mem0ApiType,
                boolean mem0InferEnabled) {
            this.agentBuilder = agentBuilder;
            this.agentCache = new ConcurrentHashMap<>();
            this.mem0ApiKey = mem0ApiKey;
            this.mem0BaseUrl = mem0BaseUrl;
            this.mem0ApiType = mem0ApiType;
            this.mem0InferEnabled = mem0InferEnabled;
        }

        private ReActAgent buildReActAgent() {
            return agentBuilder.build();
        }

        private ReActAgent buildReActAgent(String userId, A2aStreamingHook streamingHook) {
            Mem0ApiType apiType = resolveMem0ApiType(mem0BaseUrl, mem0ApiType);
            CompatibleMem0LongTermMemory longTermMemory =
                    new CompatibleMem0LongTermMemory(
                            "GuideAgent",
                            userId,
                            null,
                            Map.of(),
                            mem0BaseUrl,
                            mem0ApiKey,
                            apiType,
                            java.time.Duration.ofSeconds(30),
                            resolveInferEnabled(apiType, mem0InferEnabled));
            synchronized (this) {
                return agentBuilder
                        .longTermMemory(longTermMemory)
                        .hooks(List.of(new MonitoringHook(), streamingHook))
                        .build();
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
            return buildReActAgent().getName();
        }

        @Override
        public String getAgentDescription() {
            return buildReActAgent().getDescription();
        }

        @Override
        public Flux<Event> stream(List<Msg> requestMessages, AgentRequestOptions options) {
            if (agentCache.containsKey(options.getTaskId())) {
                throw new IllegalStateException(
                        "Agent already exists for taskId: " + options.getTaskId());
            }
            String userId = parseUserIdFromMessages(requestMessages);
            // Per-request Hook: captures tool events into a buffer queue
            A2aStreamingHook streamingHook = new A2aStreamingHook();
            ReActAgent agent = buildReActAgent(userId, streamingHook);
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
            // Merge the agent's natural stream with the synthetic events from the Hook.
            // Run the hook flux eagerly alongside the native one.
            Flux<Event> naturalStream = agent.stream(requestMessages, FULL_STREAM_OPTIONS)
                    .filter(event -> event.getType() != EventType.TOOL_RESULT)
                    .doFinally(signal -> streamingHook.complete());

            return reactor.core.publisher.Flux.merge(naturalStream, streamingHook.asFlux())
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
