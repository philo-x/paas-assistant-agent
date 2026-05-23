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

import org.springframework.beans.factory.InitializingBean;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class ToolNarrationCatalog implements InitializingBean {

    private static Map<String, ToolNarrationDefinition> definitions = new HashMap<>();

    private final ToolNarrationProperties properties;

    public ToolNarrationCatalog(ToolNarrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, ToolNarrationDefinition> newDefinitions = new HashMap<>();
        if (properties.getGroups() != null) {
            for (ToolNarrationProperties.Group group : properties.getGroups()) {
                if (group.getItems() != null) {
                    for (ToolNarrationProperties.Item item : group.getItems()) {
                        ToolNarrationDefinition definition = new ToolNarrationDefinition(
                                item.getTitle(),
                                item.isDelegation(),
                                item.isAppendToolNameToTitle()
                        );
                        if (item.getTools() != null) {
                            for (String tool : item.getTools()) {
                                newDefinitions.put(tool, definition);
                            }
                        }
                    }
                }
            }
        }
        definitions = Map.copyOf(newDefinitions);
    }

    static ToolNarrationDefinition definitionFor(String tool) {
        String baseTool = ToolNarrator.normalizeToolName(tool);
        ToolNarrationDefinition definition = definitions.get(baseTool);
        if (definition != null) {
            return definition;
        }
        return new ToolNarrationDefinition(
                "执行 " + baseTool,
                false,
                false);
    }
}
