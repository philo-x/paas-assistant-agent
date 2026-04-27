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

import java.util.HashMap;
import java.util.Map;

final class ToolNarrationCatalog {

    private static final Map<String, ToolNarrationDefinition> DEFINITIONS = definitions();

    private ToolNarrationCatalog() {}

    static ToolNarrationDefinition definitionFor(String tool) {
        ToolNarrationDefinition definition = DEFINITIONS.get(tool);
        if (definition != null) {
            return definition;
        }
        return new ToolNarrationDefinition(
                "执行 " + tool,
                false,
                "正在调用 " + tool + "。",
                "已完成 " + tool + "。",
                false);
    }

    private static Map<String, ToolNarrationDefinition> definitions() {
        Map<String, ToolNarrationDefinition> definitions = new HashMap<>();
        register(
                definitions,
                ToolNarrationDefinition.delegation(
                        "转交 Diagnosis Agent",
                        "已将请求交给 diagnosis_agent 做 Kubernetes 诊断，下面继续展示它的检查步骤。",
                        "diagnosis_agent 已返回诊断结论。"),
                "callDiagnosisAgent");
        register(
                definitions,
                ToolNarrationDefinition.delegation(
                        "转交 Guide Agent",
                        "已将请求交给 guide_agent 做解释与命令建议，下面继续展示它的分析步骤。",
                        "guide_agent 已返回解释与建议。"),
                "callGuideAgent");
        register(definitions, regular("读取集群信息", "正在读取集群基础信息。", "已读取集群基础信息，用于确认当前环境。"),
                "cluster-get-info", "cluster-info");
        register(definitions, regular("查询资源列表", "正在查询{namespace}{kind:资源}列表。", "已查询目标资源列表，用于筛查异常对象。"),
                "resource-list", "list-resources");
        register(definitions, regular("读取资源详情", "正在读取{namespace}{kind:资源}{name}的详细状态。", "已读取目标资源详情，用于判断当前状态。"),
                "resource-get", "get-resource");
        register(definitions, regular("查询资源事件", "正在查询{namespace}{kind:资源}{name}的事件。", "已收集相关事件，用于定位异常原因。"),
                "resource-events", "list-events");
        register(definitions, regular("读取资源日志", "正在读取{namespace}{kind:资源}{name}的日志。", "已读取相关日志，用于补充故障线索。"),
                "resource-logs", "get-logs");
        register(definitions, regular("执行诊断分析", "正在执行 Kubernetes 诊断分析。", "已完成 Kubernetes 诊断分析。"),
                "diagnose-analyze", "analyze");
        register(definitions, regular("整理诊断结论", "正在整理诊断结果说明。", "已整理诊断结果并提炼重点。"),
                "diagnose-explain-result");
        register(definitions, regular("查询命名空间列表", "正在查询命名空间列表。", "已查询命名空间列表。"),
                "list-namespaces");
        register(definitions, regular("读取 K8sGPT 配置", "正在读取 K8sGPT 配置。", "已读取 K8sGPT 配置信息。"),
                "config");
        register(definitions, regular("查询集成列表", "正在查询集成列表。", "已查询可用集成列表。"),
                "list-integrations");
        register(definitions, regular("管理诊断过滤器", "正在管理诊断过滤器。", "已完成诊断过滤器操作。"),
                "list-filters", "add-filters", "remove-filters");
        register(definitions, regular("生成重启计划", "正在生成重启计划。", "已生成重启计划，等待确认。"),
                "change-plan-restart");
        register(definitions, regular("生成扩缩容计划", "正在生成扩缩容计划。", "已生成扩缩容计划，等待确认。"),
                "change-plan-scale");
        register(definitions, regular("生成删除 Pod 计划", "正在生成删除 Pod 计划。", "已生成删除 Pod 计划，等待确认。"),
                "change-plan-delete-pod");
        register(definitions, regular("生成 Patch 计划", "正在生成 Patch 计划。", "已生成 Patch 计划，等待确认。"),
                "change-plan-patch");
        register(definitions, regular("执行变更", "正在执行已审批的变更。", "已执行审批通过的变更操作。"),
                "change-execute");
        register(definitions, regular("查询变更状态", "正在查询变更执行状态。", "已获取变更执行状态。"),
                "change-get-status");
        register(definitions, regular("解释 YAML 字段", "正在解释 YAML 字段含义。", "已整理 YAML 字段的含义说明。"),
                "guide-translate-yaml-field");
        register(definitions, regular("解读 Describe 输出", "正在解读 describe 输出重点。", "已提炼 describe 输出中的关键信息。"),
                "guide-interpret-describe");
        register(definitions, regular("整理建议命令", "正在整理建议命令。", "已整理可直接使用的建议命令。"),
                "guide-recommend-command");
        return Map.copyOf(definitions);
    }

    private static ToolNarrationDefinition regular(
            String title, String startTemplate, String successTemplate) {
        return ToolNarrationDefinition.regular(title, startTemplate, successTemplate);
    }

    private static void register(
            Map<String, ToolNarrationDefinition> definitions,
            ToolNarrationDefinition definition,
            String... tools) {
        for (String tool : tools) {
            definitions.put(tool, definition);
        }
    }
}
