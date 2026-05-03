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

package io.agentscope.examples.paasassistant.guide.hooks;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Hook that captures tool lifecycle events (PreActingEvent / PostActingEvent)
 * and buffers them as synthetic {@link Event} objects so they can be injected
 * into the A2A response stream.
 */
public class A2aStreamingHook implements Hook {

    private static final String BLOCK_TYPE_KEY = "_agentscope_block_type";
    private static final String TOOL_CALL_ID_KEY = "_agentscope_tool_call_id";
    private static final String TOOL_NAME_KEY = "_agentscope_tool_name";
    private static final String TOOL_OUTPUT_KEY = "_agentscope_tool_output";

    private final Sinks.Many<Event> sink = Sinks.many().unicast().onBackpressureBuffer();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent preActing) {
            // Capture tool start and input arguments
            ToolUseBlock toolUse = preActing.getToolUse();
            
            // Enrich metadata so Supervisor's DataPartParser can recognize it
            Map<String, Object> metadata = new HashMap<>(toolUse.getMetadata());
            metadata.put(BLOCK_TYPE_KEY, "tool_use");
            metadata.put(TOOL_CALL_ID_KEY, toolUse.getId());
            metadata.put(TOOL_NAME_KEY, toolUse.getName());
            
            ToolUseBlock enriched = ToolUseBlock.builder()
                    .id(toolUse.getId())
                    .name(toolUse.getName())
                    .input(toolUse.getInput())
                    .metadata(metadata)
                    .build();

            Msg msg = Msg.builder().content(enriched).build();
            sink.tryEmitNext(new Event(EventType.REASONING, msg, false));
        } else if (event instanceof PostActingEvent postActing) {
            ToolResultBlock result = postActing.getToolResult();
            if (result != null) {
                // Enrich metadata so Supervisor's DataPartParser can recognize it
                Map<String, Object> metadata = new HashMap<>(result.getMetadata());
                metadata.put(BLOCK_TYPE_KEY, "tool_result");
                metadata.put(TOOL_CALL_ID_KEY, result.getId());
                metadata.put(TOOL_NAME_KEY, result.getName());
                
                ToolResultBlock enriched = ToolResultBlock.builder()
                        .id(result.getId())
                        .name(result.getName())
                        .output(result.getOutput())
                        .metadata(metadata)
                        .build();

                Msg msg = Msg.builder().content(enriched).build();
                sink.tryEmitNext(new Event(EventType.TOOL_RESULT, msg, false));
            }
        }
        return Mono.just(event);
    }

    public Flux<Event> asFlux() {
        return sink.asFlux();
    }

    public void complete() {
        sink.tryEmitComplete();
    }
}
