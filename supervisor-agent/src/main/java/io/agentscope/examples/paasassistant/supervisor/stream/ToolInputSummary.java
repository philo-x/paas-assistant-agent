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

import java.util.Map;

record ToolInputSummary(Map<String, String> values) {

    String namespaceLabel() {
        String namespace = value("namespace");
        if (namespace.isBlank()) {
            return "";
        }
        return namespace + " 命名空间中的";
    }

    String kindOr(String fallback) {
        String kind = value("kind");
        if (kind.isBlank()) {
            return fallback;
        }
        return kind;
    }

    String nameLabel() {
        String name = value("name");
        if (name.isBlank()) {
            return "";
        }
        return " " + name;
    }

    String value(String key) {
        return values.getOrDefault(key, "").trim();
    }
}
