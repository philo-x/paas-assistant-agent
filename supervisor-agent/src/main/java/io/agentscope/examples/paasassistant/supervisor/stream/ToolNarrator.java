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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolNarrator {

    private static final Pattern THINKING_PATTERN = Pattern.compile("<thinking>(.*?)</thinking>", Pattern.DOTALL);

    private ToolNarrator() {}

    public static String summarizeToolStart(String agentName, String tool, String inputSummary) {
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(tool);
        String title = definition.title();
        if (definition.appendToolNameToTitle()) {
            title += " (" + tool + ")";
        }
        return "正在" + title + "。";
    }

    public static String summarizeToolResult(String agentName, String tool, String outputSummary, String inputSummary) {
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(tool);
        return ToolResultSummarizer.summarize(tool, outputSummary, definition);
    }

    public static String titleForTool(String tool) {
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(tool);
        String title = definition.title();
        if (definition.appendToolNameToTitle()) {
            title += " (" + tool + ")";
        }
        return title;
    }

    public static boolean isDelegationTool(String tool) {
        return ToolNarrationCatalog.definitionFor(tool).delegation();
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
