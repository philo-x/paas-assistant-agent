package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ToolNarrationCatalogTest {

    @BeforeAll
    static void setUp() {
        ToolNarrationTestUtils.initCatalog();
    }

    @Test
    void resolvesAliasesAndDelegationMetadata() {
        assertThat(ToolNarrator.titleForTool("list-resources"))
                .isEqualTo("查询资源列表 (list-resources)");
        assertThat(ToolNarrator.isDelegationTool("callDiagnosisAgent")).isTrue();
        assertThat(ToolNarrator.isDelegationTool("list-resources")).isFalse();
    }

    @Test
    void rendersCatalogTemplatesWithParsedInput() {
        assertThat(
                        ToolNarrator.summarizeToolStart(
                                "diagnosis_agent",
                                "list-resources",
                                "{namespace=default}"))
                .isEqualTo("正在查询资源列表 (list-resources)。");
    }
}
