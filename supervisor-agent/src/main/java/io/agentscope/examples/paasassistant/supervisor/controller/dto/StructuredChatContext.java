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

package io.agentscope.examples.paasassistant.supervisor.controller.dto;

public record StructuredChatContext(String namespace, String kind, String name, String mode) {

    public String normalizedNamespace() {
        return (namespace == null || namespace.isBlank()) ? "default" : namespace.trim();
    }

    public String normalizedKind() {
        return kind == null ? "" : kind.trim();
    }

    public String normalizedName() {
        return name == null ? "" : name.trim();
    }

    public String normalizedMode() {
        if (mode == null || mode.isBlank()) {
            return "auto";
        }
        String trimmed = mode.trim().toLowerCase();
        return switch (trimmed) {
            case "diagnose", "guide" -> trimmed;
            default -> "auto";
        };
    }
}
