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

package io.agentscope.examples.paasassistant.supervisor.config;

import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.examples.paasassistant.supervisor.utils.AgentConstants;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class A2aAgentConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public A2aAgent guideAgent(AiService a2aService) {
        return A2aAgent.builder()
                .name(AgentConstants.AGENT_NAME_GUIDE)
                .agentCardResolver(new NacosAgentCardResolver(a2aService))
                .build();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public A2aAgent diagnosisAgent(AiService a2aService) {
        return A2aAgent.builder()
                .name(AgentConstants.AGENT_NAME_DIAGNOSIS)
                .agentCardResolver(new NacosAgentCardResolver(a2aService))
                .build();
    }
}
