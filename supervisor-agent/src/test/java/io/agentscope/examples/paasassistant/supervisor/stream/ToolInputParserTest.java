package io.agentscope.examples.paasassistant.supervisor.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolInputParserTest {

    @Test
    void parsesNamespaceKindAndName() {
        ToolInputSummary summary =
                ToolInputParser.parse("{namespace=default, kind=Pod, name=test-pod}");

        assertThat(summary.namespaceLabel()).isEqualTo("default 命名空间中的");
        assertThat(summary.kindOr("资源")).isEqualTo("Pod");
        assertThat(summary.nameLabel()).isEqualTo(" test-pod");
    }

    @Test
    void fallsBackForBlankInput() {
        ToolInputSummary summary = ToolInputParser.parse("");

        assertThat(summary.namespaceLabel()).isEmpty();
        assertThat(summary.kindOr("资源")).isEqualTo("资源");
        assertThat(summary.nameLabel()).isEmpty();
    }
}
