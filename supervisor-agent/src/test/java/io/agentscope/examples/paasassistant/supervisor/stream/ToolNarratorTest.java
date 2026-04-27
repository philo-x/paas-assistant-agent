package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolNarratorTest {

    @Test
    void summarizesChildReasoningIntoShortReadableSentence() {
        String summarized =
                ToolNarrator.summarizeReasoningText(
                        "Thinking: 我将帮您找出 default 命名空间中有问题的 Pod。\n\n首先，让我列出 default 命名空间中的所有 Pod。");

        assertThat(summarized).contains("default 命名空间中有问题的 Pod");
        assertThat(summarized).doesNotContain("Thinking:");
        assertThat(summarized.length()).isLessThanOrEqualTo(240);
    }

    @Test
    void flattensMarkdownHeavyReasoningIntoShortSummary() {
        String summarized =
                ToolNarrator.summarizeReasoningText(
                        """
                        ### Kubernetes CRD 详解
                        | 核心概念 | 说明 |
                        | --- | --- |
                        | Custom Resource Definition | 定义扩展资源 |
                        kubectl get crd
                        apiVersion: apiextensions.k8s.io/v1
                        """);

        assertThat(summarized).contains("Kubernetes CRD 详解");
        assertThat(summarized).doesNotContain("| --- | --- |");
        assertThat(summarized.length()).isLessThanOrEqualTo(240);
    }

    @Test
    void summarizesResourceEventsIntoFocusedToolDescription() {
        String summarized =
                ToolNarrator.summarizeToolResult(
                        "diagnosis_agent",
                        "resource-events",
                        "Back-off pulling image \"test-job:latest\" Error: ImagePullBackOff",
                        "{namespace=default, name=test-678d5f9ff9-885m5, kind=Pod}");

        assertThat(summarized).isEqualTo("已收集 Pod test-678d5f9ff9-885m5 的事件，确认存在镜像拉取失败。");
    }

    @Test
    void usesCatalogFallbackForUnknownTools() {
        assertThat(ToolNarrator.titleForTool("unknown-tool")).isEqualTo("执行 unknown-tool");
        assertThat(ToolNarrator.summarizeToolStart("agent", "unknown-tool", ""))
                .isEqualTo("正在调用 unknown-tool。");
        assertThat(ToolNarrator.summarizeToolResult("agent", "unknown-tool", "", ""))
                .isEqualTo("已完成 unknown-tool。");
    }
}
