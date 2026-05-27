package io.agentscope.examples.paasassistant.common.hooks;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Hook to truncate large tool outputs before they are stored in memory.
 * This prevents the model's context length from being exceeded.
 */
public class TruncationHook implements Hook {

    private static final Logger logger = LoggerFactory.getLogger(TruncationHook.class);

    // Limit output to ~100000 characters
    private static final int MAX_OUTPUT_LENGTH = 200000;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostActingEvent postActing) {
            ToolResultBlock result = postActing.getToolResult();
            if (result == null || result.getOutput() == null || result.getOutput().isEmpty()) {
                return Mono.just(event);
            }

            boolean truncated = false;
            List<ContentBlock> originalOutput = result.getOutput();
            List<ContentBlock> newOutput = new ArrayList<>(originalOutput.size());

            for (ContentBlock block : originalOutput) {
                if (block instanceof TextBlock textBlock) {
                    String s = textBlock.getText();
                    if (s != null && s.length() > MAX_OUTPUT_LENGTH) {
                        String truncatedStr = s.substring(0, MAX_OUTPUT_LENGTH)
                                + "\n\n[... Output truncated due to length. Total size: " + s.length() + " chars ...]";
                        newOutput.add(TextBlock.builder().text(truncatedStr).build());
                        truncated = true;
                        logger.info("Truncated tool output for tool '{}' ({} -> {} chars)",
                                postActing.getToolUse().getName(), s.length(), truncatedStr.length());
                    } else {
                        newOutput.add(block);
                    }
                } else {
                    newOutput.add(block);
                }
            }

            if (truncated) {
                ToolResultBlock truncatedBlock = ToolResultBlock.builder()
                        .id(result.getId())
                        .name(result.getName())
                        .output(newOutput)
                        .metadata(result.getMetadata())
                        .build();

                PostActingEvent newEvent = new PostActingEvent(
                        postActing.getAgent(),
                        postActing.getToolkit(),
                        postActing.getToolUse(),
                        truncatedBlock);

                Msg origMsg = postActing.getToolResultMsg();
                if (origMsg != null) {
                    Msg newMsg = Msg.builder()
                            .id(origMsg.getId())
                            .name(origMsg.getName())
                            .role(origMsg.getRole())
                            .content(truncatedBlock)
                            .timestamp(origMsg.getTimestamp())
                            .metadata(origMsg.getMetadata())
                            .build();
                    newEvent.setToolResultMsg(newMsg);
                }

                return Mono.just((T) newEvent);
            }
        }

        return Mono.just(event);
    }
}
