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

/**
 * A stateful stream filter that safely removes <thinking>...</thinking> tags from 
 * fragmented text chunks in a reactive stream.
 */
public class ThinkingStreamFilter {
    private boolean inThinking = false;
    private final StringBuilder buffer = new StringBuilder();

    public String filterChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        buffer.append(chunk);
        StringBuilder output = new StringBuilder();
        
        while (buffer.length() > 0) {
            if (!inThinking) {
                int startIndex = buffer.indexOf("<thinking>");
                if (startIndex != -1) {
                    output.append(buffer.substring(0, startIndex));
                    inThinking = true;
                    buffer.delete(0, startIndex + "<thinking>".length());
                } else {
                    int partialIndex = findPartialTag(buffer, "<thinking>");
                    if (partialIndex != -1) {
                        output.append(buffer.substring(0, partialIndex));
                        buffer.delete(0, partialIndex);
                        break; 
                    } else {
                        output.append(buffer.toString());
                        buffer.setLength(0);
                    }
                }
            } else {
                int endIndex = buffer.indexOf("</thinking>");
                if (endIndex != -1) {
                    inThinking = false;
                    buffer.delete(0, endIndex + "</thinking>".length());
                } else {
                    int partialIndex = findPartialTag(buffer, "</thinking>");
                    if (partialIndex != -1) {
                        buffer.delete(0, partialIndex);
                        break;
                    } else {
                        buffer.setLength(0);
                    }
                }
            }
        }
        return output.toString();
    }

    private int findPartialTag(StringBuilder sb, String tag) {
        for (int i = 1; i < tag.length(); i++) {
            int startIndex = sb.length() - i;
            if (startIndex < 0) continue;
            String suffix = sb.substring(startIndex);
            if (tag.startsWith(suffix)) {
                return startIndex;
            }
        }
        return -1;
    }
}
