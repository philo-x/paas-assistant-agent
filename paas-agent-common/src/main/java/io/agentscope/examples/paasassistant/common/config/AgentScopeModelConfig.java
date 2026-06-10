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

package io.agentscope.examples.paasassistant.common.config;

import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.HttpVersion;
import io.agentscope.core.model.transport.JdkHttpTransport;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope Model and Formatter Configuration
 * Supports both DashScope and OpenAI model providers
 */
@Configuration
public class AgentScopeModelConfig {
    private static final Logger logger = LoggerFactory.getLogger(AgentScopeModelConfig.class);

    private static final String PROVIDER_DASHSCOPE = "dashscope";
    private static final String PROVIDER_OPENAI = "openai";

    @Value("${agentscope.model.provider}")
    private String modelProvider;

    @Value("${agentscope.dashscope.api-key}")
    private String dashscopeApiKey;

    @Value("${agentscope.dashscope.model-name:qwen-max}")
    private String dashscopeModelName;

    @Value("${agentscope.dashscope.base-url}")
    private String dashscopeBaseUrl;

    @Value("${agentscope.openai.api-key}")
    private String openaiApiKey;

    @Value("${agentscope.openai.model-name:gpt-5}")
    private String openaiModelName;

    @Value("${agentscope.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${agentscope.openai.thinking:false}")
    private boolean openaiThinking;

    @Value("${agentscope.openai.reasoning-effort:}")
    private String openaiReasoningEffort;

    @Value("${agentscope.model.disable-parallel-tools:false}")
    private boolean disableParallelTools;

    private HttpTransport createHttpTransport() {
        return JdkHttpTransport.builder()
                .config(HttpTransportConfig.builder()
                        .httpVersion(HttpVersion.HTTP_1_1)
                        .build())
                .build();
    }

    @Bean
    public Model model() {
        String provider = normalizeProvider(modelProvider);
        if (PROVIDER_OPENAI.equals(provider)) {
            logger.info(
                    "Creating OpenAI Model with model: {}, baseUrl: {}",
                    openaiModelName,
                    openaiBaseUrl);
            OpenAIChatModel.Builder builder =
                    OpenAIChatModel.builder()
                            .apiKey(openaiApiKey)
                            .modelName(openaiModelName)
                            .stream(true)
                            .httpTransport(createHttpTransport())
                            .formatter(new SafeOpenAIChatFormatter());

            GenerateOptions.Builder optionsBuilder = GenerateOptions.builder();
            if (disableParallelTools) {
                optionsBuilder.additionalBodyParam("parallel_tool_calls", false);
            }
            if (openaiThinking || (openaiReasoningEffort != null && !openaiReasoningEffort.isEmpty())) {
                // 仅当模型名称包含 deepseek 时才注入 chat_template_kwargs，避免干扰 Qwen 等其他模型
                if (openaiModelName != null && openaiModelName.toLowerCase(Locale.ROOT).contains(AgentConstants.MODEL_DEEPSEEK_MARK)) {
                    java.util.Map<String, Object> chatTemplateKwargs = new java.util.HashMap<>();
                    chatTemplateKwargs.put(AgentConstants.KEY_THINKING, openaiThinking);
                    if (openaiReasoningEffort != null && !openaiReasoningEffort.isEmpty()) {
                        chatTemplateKwargs.put(AgentConstants.KEY_REASONING_EFFORT, openaiReasoningEffort);
                    }
                    optionsBuilder.additionalBodyParam(AgentConstants.KEY_CHAT_TEMPLATE_KWARGS, chatTemplateKwargs);
                }
            }
            builder.generateOptions(optionsBuilder.build());
            if (openaiBaseUrl != null && !openaiBaseUrl.isEmpty() && !openaiBaseUrl.equals("-")) {
                builder.baseUrl(openaiBaseUrl);
            }
            return builder.build();
        }
        if (PROVIDER_DASHSCOPE.equals(provider)) {
            logger.info(
                    "Creating DashScope Model with model: {}, baseUrl: {}",
                    dashscopeModelName,
                    dashscopeBaseUrl);
            DashScopeChatModel.Builder builder =
                    DashScopeChatModel.builder()
                            .apiKey(dashscopeApiKey)
                            .modelName(dashscopeModelName)
                            .httpTransport(createHttpTransport())
                            .formatter(new DashScopeChatFormatter());
            if (disableParallelTools) {
                builder.defaultOptions(GenerateOptions.builder()
                        .additionalBodyParam("parallel_tool_calls", false)
                        .build());
            }
            if (dashscopeBaseUrl != null
                    && !dashscopeBaseUrl.isEmpty()
                    && !dashscopeBaseUrl.equals("-")) {
                builder.baseUrl(dashscopeBaseUrl);
            }
            return builder.build();
        }
        throw new IllegalArgumentException(
                "Unsupported agentscope model provider: "
                        + modelProvider
                        + ". Supported providers: dashscope, openai");
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}

