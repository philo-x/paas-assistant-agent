package io.agentscope.examples.paasassistant.guide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt configuration for the guide sub-agent.
 */
@Configuration
@ConfigurationProperties(prefix = "agent.prompts")
public class AgentPromptConfig {

    private String guideAgentInstruction;

    public String getGuideAgentInstruction() {
        return guideAgentInstruction;
    }

    public void setGuideAgentInstruction(String guideAgentInstruction) {
        this.guideAgentInstruction = guideAgentInstruction;
    }
}
