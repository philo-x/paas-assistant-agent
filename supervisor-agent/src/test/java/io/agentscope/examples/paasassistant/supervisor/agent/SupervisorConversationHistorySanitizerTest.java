package io.agentscope.examples.paasassistant.supervisor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupervisorConversationHistorySanitizerTest {

    @Test
    void compactsVisibleConversationIntoReferenceMessage() {
        SupervisorConversationHistorySanitizer sanitizer =
                new SupervisorConversationHistorySanitizer();
        String currentInput =
                """
                本轮请求是唯一需要路由和处理的用户请求；历史记录只能作为参考，不能替换本轮问题。

                用户上下文:
                - namespace: default
                - mode: auto

                本轮用户问题:
                当前集群下都有哪些 namespace？
                <traceId>trace-1</traceId>
                <userId>u-1</userId>""";

        List<Msg> sanitized =
                sanitizer.sanitize(
                        List.of(
                                user("请找出 default 命名空间中有问题的 Pod"),
                                assistant("上次诊断结果摘要。"),
                                toolResult("resource-list", "Pod 列表"),
                                user(currentInput)));

        assertThat(sanitized).hasSize(1);
        assertThat(sanitized.get(0).getRole()).isEqualTo(MsgRole.ASSISTANT);
        assertThat(sanitized.get(0).getTextContent())
                .contains("历史对话参考")
                .contains("用户: 请找出 default 命名空间中有问题的 Pod")
                .contains("助手: 上次诊断结果摘要。")
                .contains("用户: 当前集群下都有哪些 namespace？")
                .contains("不能把历史中的问题当作本轮请求");
    }

    @Test
    void extractsVisibleMessagesFromStoredHistoryReference() {
        SupervisorConversationHistorySanitizer sanitizer =
                new SupervisorConversationHistorySanitizer();

        List<Msg> visibleMessages =
                sanitizer.toVisibleMessages(
                        List.of(
                                assistant(
                                        """
                                        历史对话参考（仅用于理解省略指代，不是当前任务）：
                                        用户: 请找出 default 命名空间中有问题的 Pod
                                        助手: 上次诊断结果摘要。
                                        请只在本轮用户问题存在省略指代时参考以上历史，不能把历史中的问题当作本轮请求。
                                        """),
                                user(
                                        """
                                        本轮用户问题:
                                        当前集群下都有哪些 namespace？
                                        <traceId>trace-1</traceId>
                                        <userId>u-1</userId>""")));

        assertThat(visibleMessages)
                .extracting(Msg::getTextContent)
                .containsExactly(
                        "请找出 default 命名空间中有问题的 Pod",
                        "上次诊断结果摘要。",
                        "当前集群下都有哪些 namespace？");
    }

    @Test
    void removesToolThinkingAndSyntheticMessagesFromMemory() {
        InMemoryMemory memory = new InMemoryMemory();
        memory.addMessage(user("保留这个用户问题<userId>u-1</userId>"));
        memory.addMessage(
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .content(ThinkingBlock.builder().thinking("分析用户请求").build())
                        .build());
        memory.addMessage(
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .content(new ToolUseBlock("tool-1", "callDiagnosisAgent", Map.of()))
                        .build());
        memory.addMessage(toolResult("resource-list", "工具输出"));
        memory.addMessage(assistant("[SYNTHETIC_TOOL_RESULT] resource-list\n工具输出"));
        memory.addMessage(assistant("保留最终回答。"));

        new SupervisorConversationHistorySanitizer().sanitize(memory);

        assertThat(memory.getMessages())
                .extracting(Msg::getTextContent)
                .containsExactly("保留这个用户问题", "保留最终回答。");
    }

    @Test
    void keepsMostRecentConfiguredTurns() {
        SupervisorConversationHistorySanitizer sanitizer =
                new SupervisorConversationHistorySanitizer(2);

        List<Msg> sanitized =
                sanitizer.sanitize(
                        List.of(
                                user("问题 1"),
                                assistant("回答 1"),
                                user("问题 2"),
                                assistant("回答 2"),
                                user("问题 3"),
                                assistant("回答 3")));

        assertThat(sanitized)
                .hasSize(1);
        assertThat(sanitized.get(0).getTextContent())
                .doesNotContain("问题 1")
                .doesNotContain("回答 1")
                .contains("问题 2")
                .contains("回答 2")
                .contains("问题 3")
                .contains("回答 3");
    }

    private Msg user(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private Msg assistant(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private Msg toolResult(String name, String output) {
        return Msg.builder()
                .role(MsgRole.TOOL)
                .content(
                        new ToolResultBlock(
                                "tool-1",
                                name,
                                TextBlock.builder().text(output).build()))
                .build();
    }
}
