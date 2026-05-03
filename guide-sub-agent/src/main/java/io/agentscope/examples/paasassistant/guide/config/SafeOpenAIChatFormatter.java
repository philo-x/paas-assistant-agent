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

package io.agentscope.examples.paasassistant.guide.config;

import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.formatter.openai.dto.OpenAIMessage;
import io.agentscope.core.message.Msg;
import java.util.List;

/**
 * Workaround for agentscope-core 1.0.12 regression in
 * {@code OpenAIMessageConverter.convertAssistantMessage()}.
 *
 * <p>In 1.0.12, when an assistant message has no text content (e.g. only
 * tool_calls), the converter explicitly sets {@code content = ""} instead
 * of leaving it {@code null} (as 1.0.11 did). Because {@code OpenAIMessage}
 * is annotated with {@code @JsonInclude(NON_NULL)}, the empty string is
 * serialized as {@code "content": ""}, which strict OpenAI-compatible APIs
 * (such as code-relay.com) reject with:
 * <pre>
 *   "messages: text content blocks must be non-empty"
 * </pre>
 *
 * <p>This formatter post-processes the formatted messages and replaces any
 * empty-string content on assistant messages with {@code null} so that the
 * field is omitted during JSON serialization.
 *
 * <p><b>Remove this class once the framework fixes the issue.</b>
 */
public class SafeOpenAIChatFormatter extends OpenAIChatFormatter {

    @Override
    protected List<OpenAIMessage> doFormat(List<Msg> messages) {
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
