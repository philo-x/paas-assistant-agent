package io.agentscope.examples.paasassistant.analyze.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt configuration for the guide sub-agent.
 */
@Configuration
@ConfigurationProperties(prefix = "agent.prompts")
public class AgentPromptConfig {

    private String analyzeAgentInstruction;

    public String getAnalyzeAgentInstruction() {
        return analyzeAgentInstruction;
    }

    public void setAnalyzeAgentInstruction(String analyzeAgentInstruction) {
        this.analyzeAgentInstruction = analyzeAgentInstruction;
    }
}
