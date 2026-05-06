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
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.List;

/**
 * Workaround for agentscope-core 1.0.12 regression and OpenAI protocol constraints.
 *
 * <p>1. Regression in {@code OpenAIMessageConverter.convertAssistantMessage()}:
 * In 1.0.12, when an assistant message has no text content (e.g. only
 * tool_calls), the converter explicitly sets {@code content = ""} instead
 * of leaving it {@code null}. This causes serialization errors with strict APIs.
 *
 * <p>2. OpenAI Protocol Constraint:
 * OpenAI does not support {@link ToolUseBlock} or {@link ToolResultBlock} in messages
 * with {@code role="user"}. This formatter sanitizes such messages by converting
 * these blocks into plain {@link TextBlock} representations before formatting.
 *
 * <p>This formatter post-processes the formatted messages and replaces any
 * empty-string content on assistant messages with {@code null} so that the
 * field is omitted during JSON serialization.
 */
public class SafeOpenAIChatFormatter extends OpenAIChatFormatter {

    @Override
    protected List<OpenAIMessage> doFormat(List<Msg> messages) {
        // Sanitize messages to avoid "ToolUseBlock/ToolResultBlock is not supported in user messages" warnings
        List<Msg> sanitized = new ArrayList<>(messages.size());
        for (Msg msg : messages) {
            sanitized.add(sanitizeMsg(msg));
        }

        List<OpenAIMessage> formatted = super.doFormat(sanitized);
        for (OpenAIMessage msg : formatted) {
            if ("assistant".equals(msg.getRole()) && isEmptyStringContent(msg.getContent())) {
                msg.setContent(null);
            }
        }
        return formatted;
    }

    /**
     * Sanitizes a message by converting Tool blocks to Text blocks if the role is USER.
     */
    private Msg sanitizeMsg(Msg msg) {
        if (msg.getRole() != MsgRole.USER || msg.getContent() == null) {
            return msg;
        }

        boolean hasUnsupportedBlocks = msg.getContent().stream()
                .anyMatch(block -> block instanceof ToolUseBlock || block instanceof ToolResultBlock);

        if (!hasUnsupportedBlocks) {
            return msg;
        }

        List<ContentBlock> newContent = new ArrayList<>();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolUseBlock tub) {
                newContent.add(TextBlock.builder()
                        .text(String.format("[Tool Call: %s, Input: %s]", tub.getName(), tub.getInput()))
                        .build());
            } else if (block instanceof ToolResultBlock trb) {
                newContent.add(TextBlock.builder()
                        .text(String.format("[Tool Result: %s]", trb.getOutput()))
                        .build());
            } else {
                newContent.add(block);
            }
        }

        return Msg.builder()
                .id(msg.getId())
                .name(msg.getName())
                .role(msg.getRole())
                .content(newContent)
                .timestamp(msg.getTimestamp())
                .metadata(msg.getMetadata())
                .build();
    }

    private static boolean isEmptyStringContent(Object content) {
        return content instanceof String s && s.isEmpty();
    }
}

