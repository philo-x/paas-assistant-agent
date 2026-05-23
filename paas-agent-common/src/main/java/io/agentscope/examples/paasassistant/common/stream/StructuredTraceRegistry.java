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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Maps a structured chat traceId to the per-request SSE emitter.
 *
 * <p>Lookup is exact-match only. There is no "latest" fallback: under concurrent multi-user load,
 * a fallback would route one user's child-agent events to another user's SSE connection. Callers
 * must handle a {@code null} return from {@link #get(String)} as "no emitter available, drop or
 * log only".
 */
@Component
public class StructuredTraceRegistry {

    private final Map<String, StructuredSseEmitter> emitters = new ConcurrentHashMap<>();

    public void register(String traceId, StructuredSseEmitter emitter) {
        if (traceId != null && !traceId.isBlank() && emitter != null) {
            emitters.put(traceId, emitter);
        }
    }

    public StructuredSseEmitter get(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        return emitters.get(traceId);
    }

    public void unregister(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            emitters.remove(traceId);
        }
    }
}
