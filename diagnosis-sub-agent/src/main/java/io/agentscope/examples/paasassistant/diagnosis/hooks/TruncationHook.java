package io.agentscope.examples.paasassistant.diagnosis.hooks;

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
 * This prevents the model's context length from being exceeded by massive logs or YAML files.
 */
public class TruncationHook implements Hook {

    private static final Logger logger = LoggerFactory.getLogger(TruncationHook.class);

    // Limit output to ~10000 characters
    private static final int MAX_OUTPUT_LENGTH = 10000;

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
                        .output(newOutput)
                        .build()
                        .withIdAndName(result.getId(), result.getName());

                // Update the event directly via setters to ensure consistency
                postActing.setToolResult(truncatedBlock);
                
                // Also update the associated Msg to avoid NPE in downstream formatters (like DashScope)
                Msg originalMsg = postActing.getToolResultMsg();
                if (originalMsg != null) {
                    Msg newMsg = Msg.builder()
                            .role(originalMsg.getRole())
                            .name(originalMsg.getName())
                            .id(originalMsg.getId())
                            .content(truncatedBlock)
                            .metadata(originalMsg.getMetadata())
                            .build();
                    postActing.setToolResultMsg(newMsg);
                }
                
                return Mono.just(event);
            }
        }

        return Mono.just(event);
    }
}
