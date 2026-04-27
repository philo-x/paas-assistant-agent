/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.examples.paasassistant.supervisor.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.paasassistant.supervisor.tools.A2aAgentTools;
import io.agentscope.examples.paasassistant.supervisor.utils.MonitoringHook;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * SupervisorAgent wrapper that creates a new ReActAgent instance for each request.
 * This ensures complete isolation between requests without maintaining conversation context.
 */
public class SupervisorAgent {

    private static final Logger logger = LoggerFactory.getLogger(SupervisorAgent.class);

    private final Model model;

    private final A2aAgentTools tools;

    private final String sysPrompt;

    private final String dbName;

    private final Path sessionPath;

    private final DataSource dataSource;

    private final SupervisorConversationHistorySanitizer historySanitizer;

    private final SupervisorSessionHistoryStore sessionHistoryStore;

    public SupervisorAgent(
            Model model,
            A2aAgentTools tools,
            String sysPrompt,
            String dbName,
            DataSource dataSource) {
        this.model = model;
        this.tools = tools;
        this.sysPrompt = sysPrompt;
        this.dbName = dbName;
        this.dataSource = dataSource;
        this.historySanitizer = new SupervisorConversationHistorySanitizer();
        this.sessionHistoryStore =
                new SupervisorSessionHistoryStore(model, dataSource, dbName, historySanitizer);
        this.sessionPath =
                Paths.get(
                        System.getProperty("java.io.tmpdir"),
                        ".agentscope",
                        "examples",
                        "sessions");
        logger.info("Session path: {}", sessionPath);
    }

    /**
     * Stream method that handles user messages by creating a new agent for each request.
     *
     * @param msg    the user message
     * @return Flux of Events from the agent
     */
    public Flux<Event> stream(Msg msg, String sessionId, String userId) {
        return stream(msg, sessionId, userId, new MonitoringHook());
    }

    public Flux<Event> stream(Msg msg, String sessionId, String userId, Hook hook) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        List<Msg> visibleHistory = sessionHistoryStore.loadVisibleHistory(sessionId);
        AutoContextMemory memory = sessionHistoryStore.createMemory();
        historySanitizer.toHistoryReferenceMessage(visibleHistory).ifPresent(memory::addMessage);

        ReActAgent agent = createAgent(toolkit, memory, hook);
        return agent.stream(msg)
                .doFinally(
                        signalType -> {
                            logger.info(
                                    "Stream terminated with signal: {}, saving current sanitized session: {}",
                                    signalType,
                                    sessionId);
                            sessionHistoryStore.saveSanitizedHistory(sessionId, memory);
                        });
    }

    /**
     * Create a new ReActAgent instance for the given userId.
     *
     * @return newly created ReActAgent
     */
    private ReActAgent createAgent(Toolkit toolkit, Memory memory, Hook hook) {
        ReActAgent agent =
                ReActAgent.builder()
                        .name("supervisor_agent")
                        .sysPrompt(sysPrompt)
                        .toolkit(toolkit)
                        .hook(hook)
                        .model(model)
                        .memory(memory)
                        .build();
        return agent;
    }
}
