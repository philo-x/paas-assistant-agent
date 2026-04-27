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

import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.state.SimpleSessionKey;
import java.util.List;
import javax.sql.DataSource;

public class SupervisorSessionHistoryStore {

    private final Model model;

    private final DataSource dataSource;

    private final String dbName;

    private final SupervisorConversationHistorySanitizer historySanitizer;

    public SupervisorSessionHistoryStore(
            Model model,
            DataSource dataSource,
            String dbName,
            SupervisorConversationHistorySanitizer historySanitizer) {
        this.model = model;
        this.dataSource = dataSource;
        this.dbName = dbName;
        this.historySanitizer = historySanitizer;
    }

    public List<Msg> loadVisibleHistory(String sessionId) {
        AutoContextMemory memory = createMemory();
        memory.loadFrom(openSession(), SimpleSessionKey.of(sessionId));
        return historySanitizer.toVisibleMessages(memory.getMessages());
    }

    public void saveSanitizedHistory(String sessionId, AutoContextMemory memory) {
        historySanitizer.sanitize(memory);
        memory.saveTo(openSession(), SimpleSessionKey.of(sessionId));
    }

    AutoContextMemory createMemory() {
        AutoContextConfig autoContextConfig =
                AutoContextConfig.builder().tokenRatio(0.4).lastKeep(10).build();
        return new AutoContextMemory(autoContextConfig, model);
    }

    Session openSession() {
        return new MysqlSession(dataSource, dbName, null, true);
    }
}
