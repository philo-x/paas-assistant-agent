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
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Hook that captures tool lifecycle events (PreActingEvent / PostActingEvent)
 * and buffers them as synthetic {@link Event} objects so they can be injected
 * into the A2A response stream.
 *
 * <p>This is needed because the A2A protocol may not transparently relay
 * TOOL_RESULT events from the child agent's ReActAgent. By converting tool
 * events into {@link EventType#TOOL_RESULT} Event objects in a queue, the
 * {@code CustomAgentRunner.stream()} method can merge them into the outbound
 * Flux, ensuring the supervisor receives full tool-level visibility.
 *
 * <p>Usage: create one instance per request, pass it to {@code ReActAgent.builder().hooks(...)},
 * and call {@link #drainBufferedEvents()} between each natural stream event.
 */
public class A2aStreamingHook implements Hook {

    private final Sinks.Many<Event> sink = Sinks.many().unicast().onBackpressureBuffer();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostActingEvent postActing) {
            ToolResultBlock result = postActing.getToolResult();
            if (result != null) {
                // Build a synthetic TOOL_RESULT event containing the ToolResultBlock.
                // Use ToolResultBlock.of(id, name, output...) factory method.
                // The supervisor's handleChildEvent / emitToolResultBlocks() extracts
                // the name and TextBlock output from the ToolResultBlock.
                String toolName = postActing.getToolUse().getName();
                List<io.agentscope.core.message.ContentBlock> output = result.getOutput() != null ? result.getOutput() : List.of();
                // Convert output to a single summary TextBlock for the supervisor
                String outputText = output.stream()
                        .filter(TextBlock.class::isInstance)
                        .map(TextBlock.class::cast)
                        .map(TextBlock::getText)
                        .reduce("", String::concat);
                // To bypass AgentScope A2A client deserialization bugs (NullPointerException)
                // with ToolResultBlock over the network, we wrap it in a special TextBlock.
                String syntheticText = "[SYNTHETIC_TOOL_RESULT] " + toolName + "\n" + outputText;
                TextBlock syntheticBlock = TextBlock.builder().text(syntheticText).build();
                Msg msg = Msg.builder().content(syntheticBlock).build();
                sink.tryEmitNext(new Event(EventType.TOOL_RESULT, msg, false));
            }
        }
        return Mono.just(event);
    }

    /**
     * Get the reactive flux of intercepted events.
     * Use this to merge the hook's synthetic events into the main agent stream.
     */
    public Flux<Event> asFlux() {
        return sink.asFlux();
    }

    /**
     * Completes the hook stream so merged fluxes can terminate.
     */
    public void complete() {
        sink.tryEmitComplete();
    }
}
