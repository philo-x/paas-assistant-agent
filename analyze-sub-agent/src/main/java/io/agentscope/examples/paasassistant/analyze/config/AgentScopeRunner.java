package io.agentscope.examples.paasassistant.analyze.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.examples.paasassistant.analyze.hooks.MonitoringHook;
import io.agentscope.examples.paasassistant.analyze.hooks.TruncationHook;
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

    @Value("${agentscope.mcp.k8sgpt-mcp-url:http://localhost:8089/mcp}")
    String k8sgptMcpUrl;

    @Value("${agentscope.mcp.tool-timeout:PT3M}")
    Duration mcpToolTimeout;

    @Bean
    public AgentRunner agentRunner(
            AgentPromptConfig promptConfig,
            Model model) {

        Toolkit toolkit = new NacosToolkit();
        AutoContextConfig autoContextConfig = AutoContextConfig.builder().tokenRatio(0.2).lastKeep(10).build();

        return new CustomAgentRunner(
                "analyze_agent",
                promptConfig.getAnalyzeAgentInstruction(),
                model,
                toolkit,
                autoContextConfig,
                k8sgptMcpUrl,
                mcpToolTimeout);
    }

    private static class CustomAgentRunner implements AgentRunner {

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
        private final Map<String, ReActAgent> agentCache;
        private final String k8sgptMcpUrl;
        private final Duration mcpToolTimeout;
        private volatile boolean mcpInitialized = false;

        private CustomAgentRunner(
                String agentName,
                String sysPrompt,
                Model model,
                Toolkit toolkit,
                AutoContextConfig autoContextConfig,
                String k8sgptMcpUrl,
                Duration mcpToolTimeout) {
            this.agentName = agentName;
            this.sysPrompt = sysPrompt;
            this.model = model;
            this.toolkit = toolkit;
            this.autoContextConfig = autoContextConfig;
            this.agentCache = new ConcurrentHashMap<>();
            this.k8sgptMcpUrl = k8sgptMcpUrl;
            this.mcpToolTimeout = mcpToolTimeout;
        }

        private void initializeMcpOnce() {
            if (!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {
                        try {
                            McpClientWrapper mcpClient = McpClientBuilder.create("k8sgpt-mcp-server")
                                    .streamableHttpTransport(k8sgptMcpUrl)
                                    .timeout(mcpToolTimeout)
                                    .buildSync();
                            
                            toolkit.createToolGroup("k8sgpt", "k8sgpt快速扫描与概览", true);
                            toolkit.registration().mcpClient(mcpClient)
                                    .group("k8sgpt")
                                    .enableTools(List.of(
                                            "analyze",
                                            "cluster-info",
                                            "list-resources",
                                            "get-resource",
                                            "list-events",
                                            "get-logs",
                                            "list-namespaces",
                                            "config",
                                            "list-integrations",
                                            "list-filters",
                                            "add-filters",
                                            "remove-filters"
                                    ))
                                    .apply();
                                    
                            mcpInitialized = true;
                            logger.info("Successfully initialized k8sgpt MCP client at {}", k8sgptMcpUrl);
                        } catch (Exception e) {
                            logger.error("Failed to initialize k8sgpt MCP client: {}", e.getMessage(), e);
                            throw new RuntimeException("Failed to initialize k8sgpt MCP client", e);
                        }
                    }
                }
            }
        }

        private ReActAgent buildReActAgent(String userId) {
            initializeMcpOnce();
            return ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(sysPrompt)
                    .model(model)
                    .memory(new AutoContextMemory(autoContextConfig, model))
                    .toolkit(toolkit)
                    .hooks(List.of(new MonitoringHook(), new TruncationHook()))
                    .maxIters(5)
                    .build();
        }

        @Override
        public String getAgentName() {
            return agentName;
        }

        @Override
        public String getAgentDescription() {
            return "Quick diagnosis agent for PaaS Assistant";
        }

        @Override
        public Flux<Event> stream(List<Msg> requestMessages, AgentRequestOptions options) {
            if (agentCache.containsKey(options.getTaskId())) {
                throw new IllegalStateException(
                        "Agent already exists for taskId: " + options.getTaskId());
            }
            String userId = parseUserIdFromMessages(requestMessages);
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
