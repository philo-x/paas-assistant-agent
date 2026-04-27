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

package io.agentscope.examples.paasassistant.supervisor.agent;

import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SupervisorConversationHistorySanitizer {

    public static final int DEFAULT_MAX_VISIBLE_TURNS = 6;

    private static final String HISTORY_REFERENCE_PREFIX =
            "历史对话参考（仅用于理解省略指代，不是当前任务）：";

    private static final Pattern CURRENT_USER_QUESTION_PATTERN =
            Pattern.compile("(?:本轮用户问题|用户问题):\\s*(.*?)(?:<traceId>|<userId>|\\z)", Pattern.DOTALL);

    private static final Pattern XML_TAG_PATTERN =
            Pattern.compile("<(?:traceId|userId)>.*?</(?:traceId|userId)>", Pattern.DOTALL);

    private static final Pattern HISTORY_REFERENCE_LINE_PATTERN =
            Pattern.compile("^(用户|助手):\\s*(.+)$");

    private final int maxVisibleTurns;

    public SupervisorConversationHistorySanitizer() {
        this(DEFAULT_MAX_VISIBLE_TURNS);
    }

    public SupervisorConversationHistorySanitizer(int maxVisibleTurns) {
        this.maxVisibleTurns = Math.max(1, maxVisibleTurns);
    }

    public void sanitize(Memory memory) {
        if (memory == null) {
            return;
        }

        List<Msg> sanitized = toVisibleMessages(memory.getMessages());
        memory.clear();
        sanitized.forEach(memory::addMessage);
    }

    public List<Msg> sanitize(List<Msg> messages) {
        List<Msg> visibleMessages = toVisibleMessages(messages);
        if (visibleMessages.isEmpty()) {
            return List.of();
        }
        return toHistoryReferenceMessage(visibleMessages).stream().toList();
    }

    public List<Msg> toVisibleMessages(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<VisibleMessage> visibleMessages = new ArrayList<>();
        for (Msg message : messages) {
            visibleMessages.addAll(sanitizeMessage(message));
        }

        return keepRecentTurns(visibleMessages).stream().map(this::toMsg).toList();
    }

    public Optional<Msg> toHistoryReferenceMessage(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        List<VisibleMessage> visibleMessages =
                messages.stream()
                        .map(this::toVisibleMessage)
                        .flatMap(Optional::stream)
                        .toList();
        if (visibleMessages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toHistoryReference(visibleMessages));
    }

    private List<VisibleMessage> sanitizeMessage(Msg message) {
        if (message == null || message.getRole() == null) {
            return List.of();
        }
        if (message.getRole() != MsgRole.USER && message.getRole() != MsgRole.ASSISTANT) {
            return List.of();
        }
        if (containsToolOrThinkingBlocks(message)) {
            return List.of();
        }

        String text = message.getTextContent();
        if (message.getRole() == MsgRole.USER) {
            text = extractUserQuestion(text);
        } else {
            text = cleanText(text);
            if (text.startsWith(HISTORY_REFERENCE_PREFIX)) {
                return parseHistoryReference(text);
            }
        }
        if (text.isBlank() || isSyntheticToolResult(text)) {
            return List.of();
        }

        return List.of(new VisibleMessage(message.getRole(), text));
    }

    private Optional<VisibleMessage> toVisibleMessage(Msg message) {
        if (message == null || message.getRole() == null) {
            return Optional.empty();
        }
        String text = cleanText(message.getTextContent());
        if (text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new VisibleMessage(message.getRole(), text));
    }

    private boolean containsToolOrThinkingBlocks(Msg message) {
        return message.hasContentBlocks(ToolUseBlock.class)
                || message.hasContentBlocks(ToolResultBlock.class)
                || message.hasContentBlocks(ThinkingBlock.class);
    }

    private String extractUserQuestion(String text) {
        String cleaned = cleanText(text);
        Matcher matcher = CURRENT_USER_QUESTION_PATTERN.matcher(text == null ? "" : text);
        if (matcher.find()) {
            cleaned = cleanText(matcher.group(1));
        }
        return cleaned;
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return XML_TAG_PATTERN.matcher(text)
                .replaceAll("")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private boolean isSyntheticToolResult(String text) {
        return text != null && text.stripLeading().startsWith("[SYNTHETIC_TOOL_RESULT]");
    }

    private List<VisibleMessage> parseHistoryReference(String text) {
        List<VisibleMessage> parsed = new ArrayList<>();
        for (String line : cleanText(text).lines().toList()) {
            Matcher matcher = HISTORY_REFERENCE_LINE_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            MsgRole role = "用户".equals(matcher.group(1)) ? MsgRole.USER : MsgRole.ASSISTANT;
            String content = cleanText(matcher.group(2));
            if (!content.isBlank()) {
                parsed.add(new VisibleMessage(role, content));
            }
        }
        return parsed;
    }

    private Msg toHistoryReference(List<VisibleMessage> messages) {
        StringBuilder builder = new StringBuilder(HISTORY_REFERENCE_PREFIX);
        builder.append('\n');
        for (VisibleMessage message : messages) {
            builder.append(message.role() == MsgRole.USER ? "用户: " : "助手: ");
            builder.append(message.text()).append('\n');
        }
        builder.append("请只在本轮用户问题存在省略指代时参考以上历史，不能把历史中的问题当作本轮请求。");
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(builder.toString().trim()).build())
                .build();
    }

    private Msg toMsg(VisibleMessage message) {
        return Msg.builder()
                .role(message.role())
                .content(TextBlock.builder().text(message.text()).build())
                .build();
    }

    private List<VisibleMessage> keepRecentTurns(List<VisibleMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        int userCount = 0;
        int startIndex = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == MsgRole.USER) {
                userCount++;
                if (userCount == maxVisibleTurns) {
                    startIndex = i;
                    break;
                }
            }
        }
        return List.copyOf(messages.subList(startIndex, messages.size()));
    }

    private record VisibleMessage(MsgRole role, String text) {}
}
