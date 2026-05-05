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

package io.agentscope.core.a2a.agent.message;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ideal A2A Message Assembler: Resolves the conflict between streaming segmentation and atomic parsing.
 * It is responsible for reassembling scattered DataPart fragments into complete business objects.
 */
public class MessageAssembler {
    private final Map<String, ContentBlock> blockCache = new ConcurrentHashMap<>();

    /**
     * Assembles and returns the latest complete state of the block.
     *
     * @param blockId Unique ID for the block (e.g., tool_call_id)
     * @param delta The new fragment received from the stream
     * @return The fully reassembled block (up to the current point)
     */
    public ContentBlock assemble(String blockId, ContentBlock delta) {
        if (delta == null) {
            return null;
        }
        if (blockId == null) {
            return delta;
        }

        return blockCache.compute(
                blockId,
                (id, existing) -> {
                    if (existing == null) {
                        return delta;
                    }

                    if (existing instanceof ToolResultBlock eb && delta instanceof ToolResultBlock db) {
                        return mergeToolResult(id, eb, db);
                    } else if (existing instanceof ToolUseBlock eb && delta instanceof ToolUseBlock db) {
                        return mergeToolUse(id, eb, db);
                    } else if (existing instanceof ThinkingBlock et && delta instanceof ThinkingBlock dt) {
                        return ThinkingBlock.builder().thinking(et.getThinking() + dt.getThinking()).build();
                    } else if (existing instanceof TextBlock et && delta instanceof TextBlock dt) {
                        return TextBlock.builder().text(et.getText() + dt.getText()).build();
                    }
                    // For other types, we currently just replace or can extend merging logic
                    return delta;
                });
    }

    @SuppressWarnings("unchecked")
    private ToolUseBlock mergeToolUse(String id, ToolUseBlock existing, ToolUseBlock delta) {
        String name = delta.getName() != null ? delta.getName() : existing.getName();
        Object input = delta.getInput() != null ? delta.getInput() : existing.getInput();
        String content = delta.getContent() != null ? delta.getContent() : existing.getContent();
        
        // If content is text and both have it, concatenate? 
        // Usually ToolUseBlock.content is used for the raw tool call string.
        if (existing.getContent() != null && delta.getContent() != null) {
            content = existing.getContent() + delta.getContent();
        }

        Map<String, Object> deltaMeta = (Map<String, Object>) delta.getMetadata();
        Map<String, Object> existingMeta = (Map<String, Object>) existing.getMetadata();
        Map<String, Object> mergedMeta = (deltaMeta != null && !deltaMeta.isEmpty()) ? deltaMeta : existingMeta;

        return ToolUseBlock.builder()
                .id(id)
                .name(name)
                .input((Map<String, Object>) input)
                .content(content)
                .metadata(mergedMeta)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ToolResultBlock mergeToolResult(String id, ToolResultBlock existing, ToolResultBlock delta) {
        List<ContentBlock> existingOutput = new ArrayList<>(existing.getOutput());
        List<ContentBlock> deltaOutput = delta.getOutput();

        if (deltaOutput.isEmpty()) {
            return existing;
        }

        // Core logic: Handle text truncation. If the end of existing and start of delta are both text, concatenate them.
        if (!existingOutput.isEmpty() && !deltaOutput.isEmpty()) {
            ContentBlock last = existingOutput.get(existingOutput.size() - 1);
            ContentBlock first = deltaOutput.get(0);

            if (last instanceof TextBlock lt && first instanceof TextBlock ft) {
                // Merge the two text blocks into one
                existingOutput.set(
                        existingOutput.size() - 1,
                        TextBlock.builder().text(lt.getText() + ft.getText()).build());
                // Add the rest of the delta output (skipping the first one which was merged)
                for (int i = 1; i < deltaOutput.size(); i++) {
                    existingOutput.add(deltaOutput.get(i));
                }
            } else {
                // No text merge possible, just append all
                existingOutput.addAll(deltaOutput);
            }
        } else {
            existingOutput.addAll(deltaOutput);
        }

        Map<String, Object> deltaMeta = (Map<String, Object>) delta.getMetadata();
        Map<String, Object> existingMeta = (Map<String, Object>) existing.getMetadata();
        Map<String, Object> mergedMeta = (deltaMeta != null && !deltaMeta.isEmpty()) ? deltaMeta : existingMeta;

        // Return a new builder result with the merged output
        return ToolResultBlock.builder()
                .id(id)
                .name(delta.getName() != null ? delta.getName() : existing.getName())
                .metadata(mergedMeta)
                .output(existingOutput)
                .build();
    }

    /**
     * Clears the cache. Should be called when a stream session ends.
     */
    public void clear() {
        blockCache.clear();
    }
}
