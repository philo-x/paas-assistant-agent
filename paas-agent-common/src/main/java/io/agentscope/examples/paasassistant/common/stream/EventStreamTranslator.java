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

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonUtils;
import java.util.HashSet;
import java.util.Set;

public class EventStreamTranslator {

    private final String agentName;
    private final StructuredSseEmitter emitter;
    private final ThinkingStreamSplitter thinkingSplitter;
    
    private final Set<String> emittedToolStartIds = new HashSet<>();
    private final Set<String> emittedToolResultIds = new HashSet<>();

    public EventStreamTranslator(String agentName, StructuredSseEmitter emitter) {
        this.agentName = agentName;
        this.emitter = emitter;
        this.thinkingSplitter = new ThinkingStreamSplitter(
            text -> emitter.emitReasoningDelta(agentName, text),
            text -> emitter.emitAnswerDelta(text)
        );
    }

    public void handleEvent(Event event) {
        if (event == null || event.getMessage() == null) {
            return;
        }

        EventType type = event.getType();

        if (type == EventType.REASONING) {
            emitToolSteps(event.getMessage());
            
            for (ThinkingBlock block : event.getMessage().getContentBlocks(ThinkingBlock.class)) {
                // If it's natively a ThinkingBlock, it's definitely reasoning (no tags needed)
                if (block.getThinking() != null && !block.getThinking().isEmpty()) {
                    emitter.emitReasoningDelta(agentName, block.getThinking());
                }
            }
            
            for (TextBlock block : event.getMessage().getContentBlocks(TextBlock.class)) {
                // TextBlock might contain <thinking> tags, pass through splitter
                if (block.getText() != null && !block.getText().isEmpty()) {
                    thinkingSplitter.processChunk(block.getText());
                }
            }
            return;
        }

        if (type == EventType.TOOL_RESULT) {
            emitToolSteps(event.getMessage());
            return;
        }

        if (type == EventType.AGENT_RESULT) {
            // 文本已经在 EventType.REASONING 阶段通过流式 chunk 发送完毕。
            // 这里如果再次将最终完整的 TextBlock 送入 thinkingSplitter，会导致前端重复拼接整段文本。
            // 因此直接忽略 AGENT_RESULT 中的文本内容。
            return;
        }
    }

    public void emitFinalAnswer() {
        // Leftover handling is done internally by splitter if needed, 
        // but splitter processes immediately. We don't need to manually emit anything here anymore.
    }

    private void emitToolSteps(Msg message) {
        for (ToolUseBlock block : message.getContentBlocks(ToolUseBlock.class)) {
            if (!emittedToolStartIds.add(block.getId())) {
                continue;
            }
            String toolName = block.getName();
            Object inputObj = block.getInput();
            String inputSummary = "";
            if (inputObj != null) {
                try {
                    inputSummary = JsonUtils.getJsonCodec().toJson(inputObj);
                } catch (Exception e) {
                    inputSummary = inputObj.toString();
                }
            } else if (block.getContent() != null) {
                inputSummary = block.getContent();
            }
            emitter.emitToolStart(agentName, toolName, inputSummary);
        }

        for (ToolResultBlock block : message.getContentBlocks(ToolResultBlock.class)) {
            if (!emittedToolResultIds.add(block.getId())) {
                continue;
            }
            String toolName = (block.getName() == null || block.getName().isBlank()) ? "unknown_tool" : block.getName();
            String title = ToolNarrator.titleForTool(toolName);
            String summary = "已完成" + title + "。";
            emitter.emitToolResult(agentName, toolName, "success", summary, "");
        }
    }
}
