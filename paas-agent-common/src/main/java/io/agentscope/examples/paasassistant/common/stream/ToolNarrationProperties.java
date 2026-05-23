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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.narration")
public class ToolNarrationProperties {

    private List<Group> groups = new ArrayList<>();

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public static class Group {
        private String name;
        private List<Item> items = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }
    }

    public static class Item {
        private List<String> tools = new ArrayList<>();
        private String title;
        private boolean delegation = false;
        private boolean appendToolNameToTitle = true;

        public List<String> getTools() {
            return tools;
        }

        public void setTools(List<String> tools) {
            this.tools = tools;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isDelegation() {
            return delegation;
        }

        public void setDelegation(boolean delegation) {
            this.delegation = delegation;
        }

        public boolean isAppendToolNameToTitle() {
            return appendToolNameToTitle;
        }

        public void setAppendToolNameToTitle(boolean appendToolNameToTitle) {
            this.appendToolNameToTitle = appendToolNameToTitle;
        }
    }
}
