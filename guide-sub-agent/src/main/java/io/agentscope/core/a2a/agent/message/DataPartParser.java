package io.agentscope.core.a2a.agent.message;

import com.fasterxml.jackson.core.type.TypeReference;
import io.a2a.spec.DataPart;
import io.a2a.spec.Part;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimized shadow implementation of DataPartParser leveraging AgentScope's native JsonUtils.
 * Provides deep null-sanitization of metadata and robust polymorphic ContentBlock deserialization.
 */
public class DataPartParser implements PartParser<DataPart> {

    private static final String BLOCK_TYPE_KEY = "_agentscope_block_type";
    private static final String TOOL_CALL_ID_KEY = "_agentscope_tool_call_id";
    private static final String TOOL_NAME_KEY = "_agentscope_tool_name";
    private static final String TOOL_OUTPUT_KEY = "_agentscope_tool_output";

    @Override
    public ContentBlock parse(DataPart part) {
        if (isCommonDataPart(part)) {
            return parseToTextBlock(part);
        }
        return parseToToolBlock(part);
    }


    private boolean isCommonDataPart(DataPart part) {
        Map<String, Object> metadata = part.getMetadata();
        if (metadata == null) {
            return true;
        }
        Object type = metadata.get(BLOCK_TYPE_KEY);
        return "text".equals(type) || type == null;
    }

    private ContentBlock parseToTextBlock(DataPart part) {
        Map<String, Object> data = part.getData();
        String text = (data != null && data.get("text") != null) ? data.get("text").toString() : "";
        return TextBlock.builder().text(text).build();
    }

    private ContentBlock parseToToolBlock(DataPart part) {
        Map<String, Object> metadata = part.getMetadata();
        if (metadata != null && "tool_use".equals(metadata.get(BLOCK_TYPE_KEY))) {
            return parseToToolUseBlock(part);
        }
        return parseToToolResultBlock(part);
    }

    private ContentBlock parseToToolUseBlock(DataPart part) {
        ToolUseBlock.Builder builder = ToolUseBlock.builder()
                .id(getToolCallId(part))
                .name(getToolName(part))
                .metadata(getOriginalMetadata(part));

        String arguments = getToolArguments(part);
        if (arguments != null) {
            try {
                Map<String, Object> input = JsonUtils.getJsonCodec().fromJson(arguments, Map.class);
                builder.input(input);
            } catch (Exception e) {
                // Fallback to raw content if JSON parsing fails
                builder.content(arguments);
            }
        }
        return builder.build();
    }

    private String getToolArguments(DataPart part) {
        Map<String, Object> data = part.getData();
        Object args = (data != null) ? data.get("arguments") : null;
        return args != null ? args.toString() : null;
    }

    private ContentBlock parseToToolResultBlock(DataPart part) {
        ToolResultBlock.Builder builder = ToolResultBlock.builder();
        builder.id(getToolCallId(part));
        builder.name(getToolName(part));
        builder.metadata(getOriginalMetadata(part));

        Map<String, Object> data = part.getData();
        if (data != null && data.containsKey(TOOL_OUTPUT_KEY)) {
            Object rawOutput = data.get(TOOL_OUTPUT_KEY);
            List<ContentBlock> output = null;
            try {
                // Leverages AgentScope's configured Jackson for polymorphic ContentBlock deserialization
                output = JsonUtils.getJsonCodec().convertValue(
                        rawOutput, new TypeReference<List<ContentBlock>>() {});
            } catch (Exception e) {
                // If polymorphic conversion fails, try to handle it as a raw string or single block
                if (rawOutput != null) {
                    output = List.of(TextBlock.builder().text(rawOutput.toString()).build());
                }
            }

            // Defensive check: ensure no LinkedHashMaps leaked into the list
            // This happens if the ObjectMapper doesn't have subtype info registered
            if (output != null) {
                List<ContentBlock> sanitizedOutput = new java.util.ArrayList<>();
                for (Object item : output) {
                    if (item instanceof ContentBlock) {
                        sanitizedOutput.add((ContentBlock) item);
                    } else if (item instanceof Map) {
                        // Manual fallback for the specific case where subtypes are lost
                        try {
                            String json = JsonUtils.getJsonCodec().toJson(item);
                            sanitizedOutput.add(JsonUtils.getJsonCodec().fromJson(json, TextBlock.class));
                        } catch (Exception ignored) {}
                    }
                }
                builder.output(sanitizedOutput);
            } else {
                builder.output(List.of());
            }
        } else {
            builder.output(List.of());
        }

        return builder.build();
    }

    private String getToolCallId(DataPart part) {
        Map<String, Object> metadata = part.getMetadata();
        Object val = (metadata != null) ? metadata.get(TOOL_CALL_ID_KEY) : null;
        return val != null ? val.toString() : null;
    }

    private String getToolName(DataPart part) {
        Map<String, Object> metadata = part.getMetadata();
        Object val = (metadata != null) ? metadata.get(TOOL_NAME_KEY) : null;
        return val != null ? val.toString() : null;
    }

    private Map<String, Object> getOriginalMetadata(DataPart part) {
        Map<String, Object> metadata = part.getMetadata();
        Map<String, Object> result = new HashMap<>();
        if (metadata != null) {
            metadata.forEach((k, v) -> {
                if (k != null && v != null) { // Sanitize nulls to avoid Map.copyOf NPE
                    result.put(k, v);
                }
            });
        }
        result.remove(TOOL_CALL_ID_KEY);
        result.remove(TOOL_NAME_KEY);
        result.remove(BLOCK_TYPE_KEY);
        return result;
    }
}
