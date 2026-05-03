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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ToolResultSummarizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ToolResultSummarizer() {}

    static String summarize(
            String tool,
            String outputSummary,
            ToolNarrationDefinition definition) {
        String title = definition.title();
        if (definition.appendToolNameToTitle()) {
            title += " (" + tool + ")";
        }

        return switch (tool) {
            case "list-resources" -> summarizeResourceListResult(outputSummary, title);
            case "list-events" -> summarizeResourceEventsResult(outputSummary, title);
            default -> "已完成" + title + "。";
        };
    }

    private static String summarizeResourceListResult(String outputSummary, String title) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(outputSummary);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                int itemCount = items.size();
                int failingCount = 0;
                for (JsonNode item : items) {
                    String itemText = item.toString();
                    if (itemText.contains("ImagePullBackOff")
                            || itemText.contains("\"phase\":\"Pending\"")
                            || itemText.contains("\"ready\":false")) {
                        failingCount++;
                    }
                }

                if (itemCount > 0 && failingCount > 0) {
                    return "已扫描资源列表，发现 " + failingCount + " 个异常对象。";
                }
                if (itemCount > 0) {
                    return "已扫描资源列表，共检查 " + itemCount + " 个对象。";
                }
            }
        } catch (Exception ignored) {}
        return "已完成" + title + "。";
    }

    private static String summarizeResourceEventsResult(String outputSummary, String title) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(outputSummary);
            if (root.isArray()) {
                for (JsonNode event : root) {
                    String message = event.path("message").asText("");
                    if (message.contains("ImagePullBackOff") || message.contains("ErrImagePull")) {
                        return "已收集资源事件，确认存在镜像拉取问题。";
                    }
                }
            }
        } catch (Exception ignored) {}
        return "已完成" + title + "。";
    }
}
