package io.agentscope.examples.paasassistant.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Common prompt configuration for all sub-agents.
 */
@Configuration
@ConfigurationProperties(prefix = "agentscope.prompt")
public class AgentPromptConfig {

    private String agentInstruction;

    public String getAgentInstruction() {
        return agentInstruction;
    }

    public void setAgentInstruction(String agentInstruction) {
        this.agentInstruction = agentInstruction;
    }
}
