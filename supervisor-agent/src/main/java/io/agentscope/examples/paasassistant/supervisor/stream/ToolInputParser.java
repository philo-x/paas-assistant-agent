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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ToolInputParser {

    private static final Pattern MAP_ENTRY_PATTERN =
            Pattern.compile("([A-Za-z_][\\w-]*)=([^,}]+)");

    private ToolInputParser() {}

    static ToolInputSummary parse(String inputSummary) {
        Map<String, String> values = new LinkedHashMap<>();
        if (inputSummary == null || inputSummary.isBlank()) {
            return new ToolInputSummary(Map.of());
        }

        Matcher matcher = MAP_ENTRY_PATTERN.matcher(inputSummary);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2).trim());
        }
        return new ToolInputSummary(Map.copyOf(values));
    }
}
