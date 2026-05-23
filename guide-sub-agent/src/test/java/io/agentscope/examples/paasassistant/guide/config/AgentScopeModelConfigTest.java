package io.agentscope.examples.paasassistant.guide.config;
import io.agentscope.examples.paasassistant.common.config.AgentScopeModelConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgentScopeModelConfigTest {

    @Test
    void createsDashScopeModelForDashScopeProvider() {
        Model model = config("dashscope").model();

        assertThat(model).isInstanceOf(DashScopeChatModel.class);
        assertThat(model.getModelName()).isEqualTo("qwen-test");
    }

    @Test
    void createsOpenAIModelForOpenAIProvider() {
        Model model = config(" openai ").model();

        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-test");
    }

    @Test
    void rejectsUnsupportedProvider() {
        AgentScopeModelConfig config = config("anthropic");

        assertThatThrownBy(config::model)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported agentscope model provider")
                .hasMessageContaining("dashscope, openai");
    }

    private AgentScopeModelConfig config(String provider) {
        AgentScopeModelConfig config = new AgentScopeModelConfig();
        ReflectionTestUtils.setField(config, "modelProvider", provider);
        ReflectionTestUtils.setField(config, "dashscopeApiKey", "dashscope-key");
        ReflectionTestUtils.setField(config, "dashscopeModelName", "qwen-test");
        ReflectionTestUtils.setField(config, "dashscopeBaseUrl", "-");
        ReflectionTestUtils.setField(config, "openaiApiKey", "openai-key");
        ReflectionTestUtils.setField(config, "openaiModelName", "gpt-test");
        ReflectionTestUtils.setField(config, "openaiBaseUrl", "-");
        return config;
    }
}
