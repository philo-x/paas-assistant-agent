package io.agentscope.examples.paasassistant.diagnosis.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class K8sDataSanitizer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_RETURN_LENGTH = 30000;

    /**
     * 统一入口：智能判断是List列表还是单个资源，进行对应的JSON精简处理
     */
    public static String processGenericResource(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) return "";
        try {
            JsonNode root = mapper.readTree(rawJson);

            // 1. 如果是列表对象 (List) -> 重塑为只包含核心字段的轻量级 JSON 列表
            if (root.has("items") && root.get("items").isArray()) {
                String summarizedJson = convertToSummarizedJsonList(root);
                return forceTruncate(summarizedJson, "Resource List JSON");
            } 
            // 2. 如果是单个对象 -> 进行通用 JSON 极简清洗 (保留 spec，极简 metadata)
            else if (root.isObject()) {
                cleanUniversalResourceNode((ObjectNode) root);
                String cleanedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                return forceTruncate(cleanedJson, "Resource Detail JSON");
            }

            return forceTruncate(rawJson, "Unknown JSON");
        } catch (Exception e) {
            return forceTruncate(rawJson, "Raw Text");
        }
    }

    /**
     * ==========================================
     * 绝招 1：提取类似 get -o wide 的核心字段，重塑为极简 JSON List
     * ==========================================
     */
    private static String convertToSummarizedJsonList(JsonNode root) throws Exception {
        ArrayNode items = (ArrayNode) root.get("items");
        
        // 创建一个全新的 Root Object 来存放清洗后的结果
        ObjectNode summaryRoot = mapper.createObjectNode();
        
        // 保留原有的 kind 和 apiVersion (如果存在)
        if (root.has("kind")) summaryRoot.put("kind", root.get("kind").asText());
        if (root.has("apiVersion")) summaryRoot.put("apiVersion", root.get("apiVersion").asText());
        
        // 添加系统提示语，引导大模型进行下一步动作
        summaryRoot.put("systemNote", "This is a summarized JSON list containing only key fields. To investigate further, use your tools to GET the specific JSON by resource name.");

        ArrayNode summarizedItems = mapper.createArrayNode();

        for (JsonNode item : items) {
            // 为每个资源创建一个极简的 JSON Object
            ObjectNode summaryItem = mapper.createObjectNode();
            
            // 提取类似 get -o wide 的核心字段
            summaryItem.put("namespace", extractText(item, "/metadata/namespace", "default"));
            summaryItem.put("name", extractText(item, "/metadata/name", "unknown"));
            summaryItem.put("readyOrPhase", extractGenericStatus(item));
            summaryItem.put("keyInfo", extractKeyInfo(item));
            summaryItem.put("creationTimestamp", extractText(item, "/metadata/creationTimestamp", "-"));
            
            // 附加原有的 kind (如果 item 本身包含)
            if (item.has("kind")) {
                summaryItem.put("kind", item.get("kind").asText());
            }

            summarizedItems.add(summaryItem);
        }

        summaryRoot.set("items", summarizedItems);

        // 返回格式化后的 JSON 字符串
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summaryRoot);
    }

    /**
     * ==========================================
     * 绝招 2：针对所有单体资源(Pod/Node/Deployment/CRD)的无差别极简清洗
     * ==========================================
     */
    private static void cleanUniversalResourceNode(ObjectNode node) {
        // 【最狠的一刀】：直接重置 metadata，只保留大模型最关心的四个字段
        if (node.has("metadata") && node.get("metadata").isObject()) {
            ObjectNode metadata = (ObjectNode) node.get("metadata");
            
            JsonNode name = metadata.get("name");
            JsonNode namespace = metadata.get("namespace");
            JsonNode labels = metadata.get("labels");
            JsonNode creationTimestamp = metadata.get("creationTimestamp");

            // 清空所有原有杂乱字段 (managedFields, annotations, ownerReferences, uid 等)
            metadata.removeAll();

            // 重新塞回有用的字段
            if (name != null) metadata.set("name", name);
            if (namespace != null) metadata.set("namespace", namespace);
            if (labels != null) metadata.set("labels", labels);
            if (creationTimestamp != null) metadata.set("creationTimestamp", creationTimestamp);
        }

        // 针对已知极大字段进行定点清除
        if (node.has("status") && node.get("status").isObject()) {
            ObjectNode status = (ObjectNode) node.get("status");
            status.remove("images"); // Node 特有垃圾数据
            // status.remove("conditions"); // 注意：不要删除 conditions，大模型看病主要靠这个
        }
    }

    // --- 辅助提取方法 ---

    private static String extractGenericStatus(JsonNode item) {
        // Pods 通常有 phase
        if (item.at("/status/phase").isTextual()) {
            return item.at("/status/phase").asText();
        }
        // Deployments/StatefulSets 通常有 readyReplicas / replicas
        if (item.at("/status/replicas").isInt()) {
            int ready = item.at("/status/readyReplicas").asInt(0);
            int replicas = item.at("/status/replicas").asInt(0);
            return ready + "/" + replicas;
        }
        // Node / 通用资源的 Conditions 汇总
        if (item.at("/status/conditions").isArray()) {
            ArrayNode conditions = (ArrayNode) item.at("/status/conditions");
            for (JsonNode cond : conditions) {
                if ("Ready".equals(cond.path("type").asText())) {
                    return "Ready=" + cond.path("status").asText();
                }
            }
        }
        return "Unknown";
    }

    private static String extractKeyInfo(JsonNode item) {
        List<String> infos = new ArrayList<>();
        // 尝试提取 IP
        if (item.at("/status/podIP").isTextual()) infos.add("IP: " + item.at("/status/podIP").asText());
        // 尝试提取所在 Node
        if (item.at("/spec/nodeName").isTextual()) infos.add("Node: " + item.at("/spec/nodeName").asText());
        // 尝试提取重启次数 (Pod)
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

    private static String forceTruncate(String content, String dataType) {
        if (content.length() <= MAX_RETURN_LENGTH) return content;
        return content.substring(0, MAX_RETURN_LENGTH) + 
               String.format("\n\n...[SYSTEM WARNING: %s truncated at %d chars. Refine your query.]", dataType, MAX_RETURN_LENGTH);
    }
}