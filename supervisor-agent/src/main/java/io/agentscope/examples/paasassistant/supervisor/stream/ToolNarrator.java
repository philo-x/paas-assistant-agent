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

public final class ToolNarrator {

    private ToolNarrator() {}

    public static boolean isDelegationTool(String tool) {
        return ToolNarrationCatalog.definitionFor(tool).delegation();
    }

    public static String titleForTool(String tool) {
        return ToolNarrationCatalog.definitionFor(tool).titleFor(tool);
    }

    public static String summarizeToolStart(String agent, String tool, String inputSummary) {
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(tool);
        return ToolNarrationTemplates.render(
                definition.startTemplate(), ToolInputParser.parse(inputSummary));
    }

    public static String summarizeToolResult(
            String agent, String tool, String outputSummary, String inputSummary) {
        ToolNarrationDefinition definition = ToolNarrationCatalog.definitionFor(tool);
        return ToolResultSummarizer.summarize(tool, outputSummary, inputSummary, definition);
    }

    public static String extractThinkingText(String raw) {
        return ReasoningTextSanitizer.extractThinkingText(raw);
    }

    public static String extractThinkingChunk(String raw) {
        return ReasoningTextSanitizer.extractIncrementalChunk(raw);
    }

    public static String summarizeReasoningText(String raw) {
        return ReasoningTextSanitizer.summarize(raw);
    }
}
