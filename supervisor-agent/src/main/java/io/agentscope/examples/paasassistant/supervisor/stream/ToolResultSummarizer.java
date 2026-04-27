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
            String inputSummary,
            ToolNarrationDefinition definition) {
        return switch (tool) {
            case "resource-list", "list-resources" ->
                    summarizeResourceListResult(outputSummary, inputSummary, definition);
            case "resource-events", "list-events" ->
                    summarizeResourceEventsResult(outputSummary, inputSummary, definition);
            default -> definition.successTemplate();
        };
    }

    private static String summarizeResourceListResult(
            String outputSummary, String inputSummary, ToolNarrationDefinition definition) {
        ToolInputSummary input = ToolInputParser.parse(inputSummary);
        String resourceLabel = input.namespaceLabel() + input.kindOr("资源");

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
                            || itemText.contains("\"phase\": \"Pending\"")
                            || itemText.contains("\"ready\":false")
                            || itemText.contains("\"ready\": false")) {
                        failingCount++;
                    }
                }

                if (itemCount > 0 && failingCount > 0) {
                    return "已扫描" + resourceLabel + "列表，发现 " + failingCount + " 个异常对象。";
                }
                if (itemCount > 0) {
                    return "已扫描" + resourceLabel + "列表，共检查 " + itemCount + " 个对象。";
                }
            }
        } catch (Exception ignored) {
            // Fall back to the catalog summary when the output is not standard JSON.
        }
        return definition.successTemplate();
    }

    private static String summarizeResourceEventsResult(
            String outputSummary, String inputSummary, ToolNarrationDefinition definition) {
        ToolInputSummary input = ToolInputParser.parse(inputSummary);
        String target = input.kindOr("资源") + input.nameLabel();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(outputSummary);
            if (root.isArray()) {
                for (JsonNode event : root) {
                    String reason = event.path("reason").asText("");
                    String message = event.path("message").asText("");
                    String focused = summarizeImagePullEvent(target, reason, message);
                    if (!focused.isBlank()) {
                        return focused;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to text matching below.
        }

        String focused = summarizeImagePullEvent(target, "", outputSummary);
        if (!focused.isBlank()) {
            return focused;
        }
        return definition.successTemplate();
    }

    private static String summarizeImagePullEvent(String target, String reason, String message) {
        if (message == null) {
            return "";
        }
        if (message.contains("ImagePullBackOff")) {
            return "已收集 " + target + " 的事件，确认存在镜像拉取失败。";
        }
        if (message.contains("ErrImagePull")) {
            return "已收集 " + target + " 的事件，确认存在镜像拉取错误。";
        }
        if (message.contains("Back-off pulling image") || "BackOff".equalsIgnoreCase(reason)) {
            return "已收集 " + target + " 的事件，确认出现持续回退拉取镜像。";
        }
        return "";
    }
}
