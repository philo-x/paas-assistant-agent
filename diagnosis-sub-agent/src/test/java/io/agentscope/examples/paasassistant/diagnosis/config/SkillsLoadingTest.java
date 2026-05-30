package io.agentscope.examples.paasassistant.diagnosis.config;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SkillsLoadingTest {

    @Test
    void testSkillsCanBeLoadedSuccessfully() throws IOException {
        ClasspathSkillRepository repository = new ClasspathSkillRepository("skills");
        List<AgentSkill> skills = repository.getAllSkills();
        
        assertThat(skills).isNotEmpty();
        
        boolean hasNetworkingDiagnosis = false;
        for (AgentSkill skill : skills) {
            if ("networking_diagnosis".equals(skill.getName())) {
                hasNetworkingDiagnosis = true;
                // Basic validation of the loaded skill
                assertThat(skill.getMetadataValue("version")).isEqualTo("1.2");
                assertThat(skill.getMetadataValue("category")).isEqualTo("Networking");
                assertThat(skill.getDescription()).contains("诊断 Kubernetes Service 连通性");
            }
        }
        
        assertThat(hasNetworkingDiagnosis).isTrue();
    }
}
