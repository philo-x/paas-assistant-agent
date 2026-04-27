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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class StructuredTraceRegistry {

    private final Map<String, StructuredSseEmitter> emitters = new ConcurrentHashMap<>();

    private final ConcurrentLinkedDeque<String> activeTraceIds = new ConcurrentLinkedDeque<>();

    public void register(String traceId, StructuredSseEmitter emitter) {
        if (traceId != null && !traceId.isBlank() && emitter != null) {
            emitters.put(traceId, emitter);
            activeTraceIds.remove(traceId);
            activeTraceIds.addLast(traceId);
        }
    }

    public StructuredSseEmitter get(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        return emitters.get(traceId);
    }

    public StructuredSseEmitter getLatest() {
        while (!activeTraceIds.isEmpty()) {
            String latestTraceId = activeTraceIds.peekLast();
            if (latestTraceId == null || latestTraceId.isBlank()) {
                activeTraceIds.pollLast();
                continue;
            }

            StructuredSseEmitter emitter = emitters.get(latestTraceId);
            if (emitter != null) {
                return emitter;
            }

            activeTraceIds.pollLast();
        }
        return null;
    }

    public void unregister(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            emitters.remove(traceId);
            activeTraceIds.remove(traceId);
        }
    }
}
