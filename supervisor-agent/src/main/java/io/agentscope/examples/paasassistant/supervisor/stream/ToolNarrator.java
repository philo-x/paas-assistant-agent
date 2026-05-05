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

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.util.JsonUtils;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolNarrator {

    private static final Pattern THINKING_PATTERN = Pattern.compile("<thinking>(.*?)</thinking>", Pattern.DOTALL);

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
        return chunk.replaceAll("(?s)<thinking>.*?</thinking>", "");
    }

    public static String extractThinkingText(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        return chunk.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();
    }
}
