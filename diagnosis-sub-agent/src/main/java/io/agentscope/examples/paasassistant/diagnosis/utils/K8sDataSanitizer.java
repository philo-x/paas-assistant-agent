package io.agentscope.examples.paasassistant.diagnosis.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class K8sDataSanitizer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_CONFIGMAP_DATA_LENGTH = 500; // 安全截断字段级长度

    /**
     * 统一入口
     */
    public static String processGenericResource(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) return "";
        try {
            JsonNode root = mapper.readTree(rawJson);

            if (root.has("items") && root.get("items").isArray()) {
                // 1. 列表降维处理 (吸取了你的 List 摘要优点)
                return convertToSummarizedJsonList(root);
            } else if (root.isObject()) {
                // 2. 单体资源深层清洗 (吸取了深层字段过滤的优点)
                cleanUniversalResourceNode((ObjectNode) root);
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            }
            return rawJson;
        } catch (Exception e) {
            return rawJson; // 解析失败原样返回
        }
    }

    private static String convertToSummarizedJsonList(JsonNode root) throws Exception {
        ArrayNode items = (ArrayNode) root.get("items");
        ObjectNode summaryRoot = mapper.createObjectNode();

        if (root.has("kind")) summaryRoot.put("kind", root.get("kind").asText());

        // 优秀的 Prompt 注入
        summaryRoot.put("systemNote", "This is a summarized list. To investigate further, use tools to GET the specific resource by name.");

        ArrayNode summarizedItems = mapper.createArrayNode();
        // 限制列表长度，避免 Items 太多撑爆，而不是使用暴力的字符串截断
        int limit = Math.min(items.size(), 50);

        for (int i = 0; i < limit; i++) {
            JsonNode item = items.get(i);
            ObjectNode summaryItem = mapper.createObjectNode();

            summaryItem.put("namespace", extractText(item, "/metadata/namespace", "default"));
            summaryItem.put("name", extractText(item, "/metadata/name", "unknown"));
            summaryItem.put("readyOrPhase", extractGenericStatus(item));
            summaryItem.put("keyInfo", extractKeyInfo(item));

            if (item.has("kind")) summaryItem.put("kind", item.get("kind").asText());
            summarizedItems.add(summaryItem);
        }

        if (items.size() > limit) {
            summaryRoot.put("warning", String.format("Output truncated. Only showing first %d items out of %d.", limit, items.size()));
        }

        summaryRoot.set("items", summarizedItems);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summaryRoot);
    }

    private static void cleanUniversalResourceNode(ObjectNode node) {
        String kind = node.has("kind") ? node.get("kind").asText() : "Unknown";

        // 1. 极简 Metadata (采用你的白名单机制，最安全)
        if (node.has("metadata") && node.get("metadata").isObject()) {
            ObjectNode metadata = (ObjectNode) node.get("metadata");
            JsonNode name = metadata.get("name");
            JsonNode namespace = metadata.get("namespace");
            JsonNode labels = metadata.get("labels");

            metadata.removeAll(); // 一刀切

            if (name != null) metadata.set("name", name);
            if (namespace != null) metadata.set("namespace", namespace);
            if (labels != null) metadata.set("labels", labels); // 保留 label 用于关联排查
        }

        // 2. 深层 Status 清洗 (融合深层过滤机制)
        if (node.has("status") && node.get("status").isObject()) {
            ObjectNode status = (ObjectNode) node.get("status");
            status.remove("images");

            // 清理 conditions 中的时间戳噪音
            if (status.has("conditions") && status.get("conditions").isArray()) {
                ArrayNode conditions = (ArrayNode) status.get("conditions");
                for (JsonNode cond : conditions) {
                    if (cond.isObject()) {
                        ObjectNode cObj = (ObjectNode) cond;
                        cObj.remove("lastProbeTime");
                        cObj.remove("lastTransitionTime"); // 删掉时间戳，保留 type, status, reason, message
                    }
                }
            }

            // 针对 Pod 提炼 containerStatuses，排查 CrashLoopBackOff 必备
            if ("Pod".equals(kind) && status.has("containerStatuses") && status.get("containerStatuses").isArray()) {
                ArrayNode cStatuses = (ArrayNode) status.get("containerStatuses");
                for (JsonNode cs : cStatuses) {
                    if (cs.isObject()) {
                        ObjectNode csObj = (ObjectNode) cs;
                        csObj.remove("containerID");
                        csObj.remove("imageID");
                        csObj.remove("image");
                        // 只保留 name, state, restartCount, ready
                    }
                }
            }
        }

        // 3. 安全拦截大文本字段 (替代暴力的字符串截断，防止破坏 JSON 结构)
        if (("ConfigMap".equals(kind) || "Secret".equals(kind)) && node.has("data") && node.get("data").isObject()) {
            ObjectNode data = (ObjectNode) node.get("data");
            Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String val = field.getValue().asText();
                if (val.length() > MAX_CONFIGMAP_DATA_LENGTH) {
                    data.put(field.getKey(), "<TRUNCATED: Length " + val.length() + ". Use tool to read specifics>");
                }
            }
        }
    }

    // --- 辅助提取方法 (保持你的优秀实现不变) ---
    private static String extractGenericStatus(JsonNode item) {
        if (item.at("/status/phase").isTextual()) return item.at("/status/phase").asText();
        if (item.at("/status/replicas").isInt()) {
            return item.at("/status/readyReplicas").asInt(0) + "/" + item.at("/status/replicas").asInt(0);
        }
        if (item.at("/status/conditions").isArray()) {
            for (JsonNode cond : item.at("/status/conditions")) {
                if ("Ready".equals(cond.path("type").asText())) return "Ready=" + cond.path("status").asText();
            }
        }
        return "Unknown";
    }

    private static String extractKeyInfo(JsonNode item) {
        List<String> infos = new ArrayList<>();
        if (item.at("/status/podIP").isTextual()) infos.add("IP: " + item.at("/status/podIP").asText());
        if (item.at("/spec/nodeName").isTextual()) infos.add("Node: " + item.at("/spec/nodeName").asText());
        if (item.at("/status/containerStatuses").isArray()) {
            int restarts = 0;
            for (JsonNode cs : item.at("/status/containerStatuses")) {
                restarts += cs.path("restartCount").asInt(0);
            }
            if (restarts > 0) infos.add("Restarts: " + restarts);
        }
        return infos.isEmpty() ? "-" : String.join(", ", infos);
    }

    private static String extractText(JsonNode node, String jsonPtr, String defaultVal) {
        JsonNode val = node.at(jsonPtr);
        return val.isMissingNode() || val.isNull() ? defaultVal : val.asText();
    }
}