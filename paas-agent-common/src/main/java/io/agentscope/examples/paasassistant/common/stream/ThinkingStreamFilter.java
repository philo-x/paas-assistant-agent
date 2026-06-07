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
 * A stateful stream filter that safely removes <think>...</think> tags from 
 * fragmented text chunks in a reactive stream, while KEEPING the text inside.
 */
public class ThinkingStreamFilter {
    private final StringBuilder buffer = new StringBuilder();

    public String filterChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        buffer.append(chunk);
        StringBuilder output = new StringBuilder();

        while (buffer.length() > 0) {
            int lessThanIdx = buffer.indexOf("<");
            if (lessThanIdx == -1) {
                output.append(buffer.toString());
                buffer.setLength(0);
                break;
            }

            if (lessThanIdx > 0) {
                output.append(buffer.substring(0, lessThanIdx));
                buffer.delete(0, lessThanIdx);
            }

            if (buffer.toString().startsWith("<think>")) {
                buffer.delete(0, "<think>".length());
            } else if (buffer.toString().startsWith("</think>")) {
                buffer.delete(0, "</think>".length());
            } else {
                String bStr = buffer.toString();
                if ("<think>".startsWith(bStr) || "</think>".startsWith(bStr)) {
                    break;
                } else {
                    output.append("<");
                    buffer.delete(0, 1);
                }
            }
        }
        return output.toString();
    }
}
