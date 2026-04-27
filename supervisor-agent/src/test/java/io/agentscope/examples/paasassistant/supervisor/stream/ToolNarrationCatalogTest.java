package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolNarrationCatalogTest {

    @Test
    void resolvesAliasesAndDelegationMetadata() {
        assertThat(ToolNarrator.titleForTool("resource-list"))
                .isEqualTo("查询资源列表 (resource-list)");
        assertThat(ToolNarrator.titleForTool("list-resources"))
                .isEqualTo("查询资源列表 (list-resources)");
        assertThat(ToolNarrator.isDelegationTool("callDiagnosisAgent")).isTrue();
        assertThat(ToolNarrator.isDelegationTool("resource-list")).isFalse();
    }

    @Test
    void rendersCatalogTemplatesWithParsedInput() {
        assertThat(
                        ToolNarrator.summarizeToolStart(
                                "diagnosis_agent",
                                "resource-get",
                                "{namespace=default, kind=Pod, name=test-pod}"))
                .isEqualTo("正在读取default 命名空间中的Pod test-pod的详细状态。");
    }
}
