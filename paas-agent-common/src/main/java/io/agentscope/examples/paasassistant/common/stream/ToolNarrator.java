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

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.util.JsonUtils;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolNarrator {

    private static final Pattern THINKING_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);

    private ToolNarrator() {}

    static String normalizeToolName(String tool) {
        if (tool == null) {
            return null;
        }
        int index = tool.lastIndexOf("__");
        return index != -1 ? tool.substring(index + 2) : tool;
    }

    public static String extractText(Object obj) {
        if (obj == null) {
            return "";
        }
        if (obj instanceof TextBlock textBlock) {
            return textBlock.getText();
        }
        if (obj instanceof String s) {
            return s;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Object text = map.get("text");
            if (text != null) {
                return text.toString();
            }
            try {
                return JsonUtils.getJsonCodec().toJson(obj);
            } catch (Exception e) {
                return obj.toString();
            }
        }
        return obj.toString();
    }

    public static String summarizeToolStart(String agentName, String tool, String inputSummary) {
        String baseTool = normalizeToolName(tool);
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(baseTool);
        String title = definition.title();
        if (definition.appendToolNameToTitle()) {
            title += " (" + baseTool + ")";
        }
        return "正在" + title + "。";
    }

    public static String titleForTool(String tool) {
        String baseTool = normalizeToolName(tool);
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(baseTool);
        String title = definition.title();
        if (definition.appendToolNameToTitle()) {
            title += " (" + baseTool + ")";
        }
        return title;
    }

    public static boolean isDelegationTool(String tool) {
        return ToolNarrationCatalog.definitionFor(normalizeToolName(tool)).delegation();
    }

    public static String extractThinkingChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        // Use (?s) so DOTALL is enabled for the regex, matching newlines inside the tags.
        // DO NOT use .trim() here, as it destroys spaces and newlines between streaming chunks!
        return chunk.replaceAll("(?s)<think>.*?</think>", "");
    }

    public static String extractThinkingText(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        return chunk.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    public static String cleanReActSyntax(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // Remove native tool calls starting with <function=
        String result = text.replaceAll("(?m)^<function=.*$", "");
        
        // Remove <tool_call> and <tool_response> blocks completely (Qwen 3 tool tokens)
        result = result.replaceAll("(?s)<tool_call>.*?</tool_call>", "");
        result = result.replaceAll("(?s)<tool_response>.*?</tool_response>", "");
        
        // Remove ReAct Action/Action Input/Thought completely if it's at the end
        result = result.replaceAll("(?m)^Action:.*$", "");
        result = result.replaceAll("(?m)^Action Input:.*$", "");
        
        return result.trim();
    }

    public static String cleanLlmTokens(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;

        // 0. Clean hook logs (both lines starting with [HOOK] and hooks in the middle of a line)
        result = result.replaceAll("(?m)^ *\\[HOOK\\].*(?:\\r?\\n)?", "");
        result = result.replaceAll("\\[HOOK\\].*", "");

        // 1. Clean compound tokens like <|DSML|...<|begin_of_sentence|>> or similar
        result = result.replaceAll("(?i)< *[|｜] *DSML *[|｜] *[^<>|｜]* *[|｜]? *< *[|｜] *begin[^a-zA-Z]+of[^a-zA-Z]+(?:sentence|text) *[|｜]? *>? *>? *", "");
        result = result.replaceAll("(?i)< *[|｜] *DSML *[|｜] *[^<>|｜]* *[|｜]? *< *[|｜] *end[^a-zA-Z]+of[^a-zA-Z]+(?:sentence|text) *[|｜]? *>? *>? *", "");

        // 2. Clean begin/end of sentence/text tokens (with optional backticks and enclosing delimiters)
        result = result.replaceAll("(?i)`?< *[|｜]? *(?:begin|end)[^a-zA-Z]+of[^a-zA-Z]+(?:sentence|text) *[|｜]? *>`?", "");
        result = result.replaceAll("(?i)`?[|｜] *(?:begin|end)[^a-zA-Z]+of[^a-zA-Z]+(?:sentence|text) *[|｜]? *>?`?", "");
        result = result.replaceAll("(?i)`?(?:begin|end)[_▁]of[_▁](?:sentence|text)`?", "");

        // 3. Clean Chat/Role control tokens (like Qwen's im_start/im_end/endoftext, DeepSeek's User/Assistant/Outputs/System/EOT)
        result = result.replaceAll("(?i)`?< *[|｜]? *(?:im_(?:start|end)|endoftext|EOT|user|assistant|system|outputs) *[|｜]? *>`?", "");
        result = result.replaceAll("(?i)`?[|｜] *(?:im_(?:start|end)|endoftext|EOT|user|assistant|system|outputs) *[|｜]? *>?`?", "");

        // 4. Clean DSML tool/invocation tags (e.g. <|DSML|tool_calls>, <｜DSML｜call:xxx｜>, DSML|tool_calls)
        result = result.replaceAll("(?i)< *[|｜] *DSML *[|｜] *[^>|｜]+ *[|｜]? *>? *", "");
        result = result.replaceAll("(?i)[|｜] *DSML *[|｜] *[^>|｜]+ *[|｜]? *>? *", "");
        result = result.replaceAll("(?i)DSML *[|｜] *[^>|｜\\s]+ *", "");

        // 5. Clean <dsml:...> / </dsml:...> XML-style DSML tags (DeepSeek-V4)
        result = result.replaceAll("</?dsml:[^>]*>?", "");

        // 6. Catch-all: Clean any remaining <｜...｜> DeepSeek-style control tokens
        // (e.g. <｜tool▁calls▁begin｜>, <｜tool▁sep｜>, <｜tool▁output▁end｜> etc.)
        // Uses the full-width pipe ｜ (U+FF5C) as anchor — normal text never contains this pattern.
        result = result.replaceAll("<｜[^>]+｜>", "");

        return result;
    }
}
