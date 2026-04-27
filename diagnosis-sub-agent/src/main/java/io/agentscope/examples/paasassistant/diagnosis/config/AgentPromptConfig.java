package io.agentscope.examples.paasassistant.diagnosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt configuration for the diagnosis sub-agent.
 */
@Configuration
@ConfigurationProperties(prefix = "agent.prompts")
public class AgentPromptConfig {

    private String diagnosisAgentInstruction;

    public String getDiagnosisAgentInstruction() {
        return diagnosisAgentInstruction;
    }

    public void setDiagnosisAgentInstruction(String diagnosisAgentInstruction) {
        this.diagnosisAgentInstruction = diagnosisAgentInstruction;
    }
}
