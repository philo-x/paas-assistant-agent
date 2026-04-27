package io.agentscope.examples.paasassistant.supervisor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.session.Session;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupervisorSessionHistoryStoreTest {

    @Test
    void loadsVisibleHistoryWithoutUsingReactAgent() {
        InMemorySession session = new InMemorySession();
        SupervisorConversationHistorySanitizer sanitizer =
                new SupervisorConversationHistorySanitizer();
        TestStore store = new TestStore(session, sanitizer);

        AutoContextMemory memory = store.createMemory();
        memory.addMessage(user("请找出 default 命名空间中有问题的 Pod"));
        memory.addMessage(toolResult("resource-list", "工具输出"));
        memory.addMessage(assistant("上次诊断结果摘要。"));
        memory.saveTo(session, io.agentscope.core.state.SimpleSessionKey.of("chat-1"));

        List<Msg> visibleHistory = store.loadVisibleHistory("chat-1");

        assertThat(visibleHistory)
                .extracting(Msg::getTextContent)
                .containsExactly("请找出 default 命名空间中有问题的 Pod", "上次诊断结果摘要。");
    }

    @Test
    void savesSanitizedVisibleHistoryBackToSession() {
        InMemorySession session = new InMemorySession();
        SupervisorConversationHistorySanitizer sanitizer =
                new SupervisorConversationHistorySanitizer();
        TestStore store = new TestStore(session, sanitizer);

        AutoContextMemory memory = store.createMemory();
        memory.addMessage(user("保留这个用户问题<userId>u-1</userId>"));
        memory.addMessage(toolResult("resource-list", "工具输出"));
        memory.addMessage(assistant("保留最终回答。"));

        store.saveSanitizedHistory("chat-2", memory);

        AutoContextMemory reloaded = store.createMemory();
        reloaded.loadFrom(session, io.agentscope.core.state.SimpleSessionKey.of("chat-2"));
        assertThat(reloaded.getMessages())
                .extracting(Msg::getTextContent)
                .containsExactly("保留这个用户问题", "保留最终回答。");
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

    private static final class TestStore extends SupervisorSessionHistoryStore {

        private final Session session;

        private TestStore(Session session, SupervisorConversationHistorySanitizer historySanitizer) {
            super(null, null, "", historySanitizer);
            this.session = session;
        }

        @Override
        Session openSession() {
            return session;
        }
    }
}
