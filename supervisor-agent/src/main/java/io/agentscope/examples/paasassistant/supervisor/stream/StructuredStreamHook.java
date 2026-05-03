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

package io.agentscope.examples.paasassistant.supervisor.stream;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import reactor.core.publisher.Mono;

public class StructuredStreamHook implements Hook {

    private final String agentName;

    private final StructuredSseEmitter emitter;

    public StructuredStreamHook(String agentName, StructuredSseEmitter emitter) {
        this.agentName = agentName;
        this.emitter = emitter;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ReasoningChunkEvent reasoningChunk) {
            String text = ToolNarrator.extractThinkingChunk(extractThinking(reasoningChunk.getIncrementalChunk()));
            if (!text.isEmpty()) {
                emitter.emitReasoningDelta(agentName, text);
            }
        } else if (event instanceof PreActingEvent preActing) {
            emitter.emitToolStart(
                    agentName,
                    preActing.getToolUse().getName(),
                    summarize(preActing.getToolUse().getInput()));
        } else if (event instanceof PostActingEvent postActing) {
            ToolResultBlock result = postActing.getToolResult();
            String summary =
                    result == null || result.getOutput() == null || result.getOutput().isEmpty()
                            ? ""
                            : ToolNarrator.summarizeToolResult(
                                    agentName,
                                    postActing.getToolUse().getName(),
                                    summarize(result.getOutput().get(0)),
                                    summarize(postActing.getToolUse().getInput()));
            emitter.emitToolResult(
                    agentName,
                    postActing.getToolUse().getName(),
                    "success",
                    summary,
                    summarize(postActing.getToolUse().getInput()));
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
        // Fallback to TextBlock when the model does not produce ThinkingBlock
        // (aligned with A2aAgentTools.handleChildEvent logic for consistency)
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
        if (value == null) {
            return "";
        }
        String normalized;
        if (value instanceof java.util.Map) {
            try {
                normalized = io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(value);
            } catch (Exception e) {
                normalized = value.toString();
            }
        } else {
            normalized = value.toString();
        }
        
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() <= 2000) {
            return normalized;
        }
        return normalized.substring(0, 1997) + "...";
    }
}
