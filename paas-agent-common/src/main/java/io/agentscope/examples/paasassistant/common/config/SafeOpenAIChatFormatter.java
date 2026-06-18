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

import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.formatter.openai.dto.OpenAIMessage;
import io.agentscope.core.message.Msg;
import java.util.List;

/**
 * Workaround for agentscope-core 1.0.12 regression.
 *
 * <p>Regression in {@code OpenAIMessageConverter.convertAssistantMessage()}:
 * In 1.0.12, when an assistant message has no text content (e.g. only
 * tool_calls), the converter explicitly sets {@code content = ""} instead
 * of leaving it {@code null}. This causes serialization errors with strict APIs.
 *
 * <p>This formatter post-processes the formatted messages and replaces any
 * empty-string content on assistant messages with {@code null} so that the
 * field is omitted during JSON serialization.
 */
public class SafeOpenAIChatFormatter extends OpenAIChatFormatter {

    @Override
    protected List<OpenAIMessage> doFormat(List<Msg> messages) {
        if (messages == null) {
            return List.of();
        }

        List<OpenAIMessage> formatted = super.doFormat(messages);
        for (OpenAIMessage msg : formatted) {
            if ("assistant".equals(msg.getRole()) && isEmptyStringContent(msg.getContent())) {
                msg.setContent(null);
            }
        }
        return formatted;
    }

    private static boolean isEmptyStringContent(Object content) {
        return content instanceof String s && s.isEmpty();
    }
}

