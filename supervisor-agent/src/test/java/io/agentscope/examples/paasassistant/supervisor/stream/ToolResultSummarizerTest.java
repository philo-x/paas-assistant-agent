package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolResultSummarizerTest {

    @Test
    void summarizesResourceListJsonWithFailingObjects() {
        String output =
                """
                {"items":[
                  {"metadata":{"name":"ok"},"status":{"phase":"Running","ready":true}},
                  {"metadata":{"name":"bad"},"status":{"phase":"Pending","ready":false}}
                ]}
                """;

        String summarized =
                ToolNarrator.summarizeToolResult(
                        "diagnosis_agent",
                        "resource-list",
                        output,
                        "{namespace=default, kind=Pod}");

        assertThat(summarized).isEqualTo("已扫描default 命名空间中的Pod列表，发现 1 个异常对象。");
    }

    @Test
    void fallsBackToCatalogSummaryForNonJsonResourceList() {
        String summarized =
                ToolNarrator.summarizeToolResult(
                        "diagnosis_agent",
                        "resource-list",
                        "not-json",
                        "{namespace=default, kind=Pod}");

        assertThat(summarized).isEqualTo("已查询目标资源列表，用于筛查异常对象。");
    }

    @Test
    void summarizesImagePullBackOffEvents() {
        String summarized =
                ToolNarrator.summarizeToolResult(
                        "diagnosis_agent",
                        "resource-events",
                        "Back-off pulling image \"test-job:latest\" Error: ImagePullBackOff",
                        "{namespace=default, name=test-678d5f9ff9-885m5, kind=Pod}");

        assertThat(summarized).isEqualTo("已收集 Pod test-678d5f9ff9-885m5 的事件，确认存在镜像拉取失败。");
    }
}
