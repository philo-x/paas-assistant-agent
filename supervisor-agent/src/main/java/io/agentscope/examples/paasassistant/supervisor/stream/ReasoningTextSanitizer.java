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

final class ReasoningTextSanitizer {

    static final int DEFAULT_MAX_LENGTH = 240;

    private ReasoningTextSanitizer() {}

    static String extractThinkingText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?m)^Thinking:\\s*", "")
                .trim();
    }

    /**
     * Normalizes an incremental streaming chunk without trimming leading/trailing whitespace.
     * Streaming tokens like " diagnosis" carry meaningful leading spaces that join words
     * across chunk boundaries — trimming them causes word concatenation ("Thediagnosis").
     */
    static String extractIncrementalChunk(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?m)^Thinking:\\s*", "");
    }

    static String summarize(String raw) {
        String normalized = extractThinkingText(raw);
        if (normalized.isBlank()) {
            return "";
        }

        String summarized = normalized;

        if (summarized.length() <= DEFAULT_MAX_LENGTH) {
            return summarized;
        }
        return summarized.substring(0, DEFAULT_MAX_LENGTH - 3) + "...";
    }

    private static boolean looksLikeFormattedAnswer(String text) {
        return text.contains("```")
                || text.contains("\n|")
                || text.contains("###")
                || text.contains("apiVersion:")
                || text.contains("kubectl ")
                || text.contains("# 查看")
                || text.contains("|---");
    }

    private static String summarizeFormattedAnswer(String text) {
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\\n")) {
            String cleaned = stripMarkdown(line);
            if (cleaned.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(cleaned);
            if (builder.length() >= DEFAULT_MAX_LENGTH) {
                break;
            }
            if (builder.toString().split("\\n").length >= 3) {
                break;
            }
        }
        return builder.toString().trim();
    }

    private static String stripMarkdown(String line) {
        if (line == null) {
            return "";
        }
        String cleaned = line.trim();
        if (cleaned.isEmpty()
                || cleaned.equals("```")
                || cleaned.matches("^[-|: ]+$")) {
            return "";
        }
        cleaned = cleaned.replaceAll("^#{1,6}\\s*", "");
        cleaned = cleaned.replaceAll("^[-*+]\\s+", "");
        cleaned = cleaned.replaceAll("^\\d+\\.\\s+", "");
        cleaned = cleaned.replace('|', ' ');
        cleaned = cleaned.replace("`", "");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        return cleaned;
    }
}
