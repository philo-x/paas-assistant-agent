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

package io.agentscope.examples.paasassistant.common.stream;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import reactor.core.publisher.Mono;

public class StructuredStreamHook implements Hook {

    private final String agentName;

    private final StructuredSseEmitter emitter;

    private final ThinkingStreamFilter thinkingFilter = new ThinkingStreamFilter();

    public StructuredStreamHook(String agentName, StructuredSseEmitter emitter) {
        this.agentName = agentName;
        this.emitter = emitter;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ReasoningChunkEvent reasoningChunk) {
            String chunk = extractThinking(reasoningChunk.getIncrementalChunk());
            String text = thinkingFilter.filterChunk(chunk);
            if (!text.isEmpty()) {
                emitter.emitReasoningDelta(agentName, text);
            }
        } else if (event instanceof PreActingEvent preActing) {
            emitter.emitToolStart(
                    agentName,
                    preActing.getToolUse().getName(),
                    summarize(preActing.getToolUse().getInput()));
        } else if (event instanceof PostActingEvent postActing) {
            String toolName = postActing.getToolUse().getName();
            String title = ToolNarrator.titleForTool(toolName);
            String summary = "已完成" + title + "。";

            emitter.emitToolResult(
                    agentName,
                    toolName,
                    "success",
                    summary,
                    summarize(postActing.getToolUse().getInput()));
        } else if (event instanceof PostCallEvent postCall) {
            emitter.emitAnswerDelta(postCall.getFinalMessage().getTextContent());

        }
        return Mono.just(event);
    }

    private String extractThinking(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        msg.getContent().forEach(
                block -> {
                    if (block instanceof ThinkingBlock thinkingBlock) {
                        builder.append(thinkingBlock.getThinking());
                    }
                });
        if (builder.isEmpty()) {
            msg.getContent().forEach(
                    block -> {
                        if (block instanceof TextBlock textBlock) {
                            String text = textBlock.getText();
                            if (text != null && !text.isEmpty()) {
                                builder.append(text);
                            }
                        }
                    });
        }
        return builder.toString();
    }

    private String summarize(Object value) {
        String normalized = ToolNarrator.extractText(value);
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() <= 2000) {
            return normalized;
        }
        return normalized.substring(0, 1997) + "...";
    }
}
