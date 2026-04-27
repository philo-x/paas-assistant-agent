package io.agentscope.examples.paasassistant.guide.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Lightweight read-only helper tools for the guide agent.
 */
@Service
public class GuideTools {

    @Tool(
            name = "guide-translate-yaml-field",
            description =
                    "Explain the meaning, impact and common cautions of a Kubernetes YAML field. This tool is read-only.")
    public String translateYamlField(
            @ToolParam(name = "kind", description = "Resource kind, such as Deployment or Service")
                    String kind,
            @ToolParam(name = "fieldPath", description = "YAML field path, such as spec.template.spec.containers[].resources")
                    String fieldPath,
            @ToolParam(name = "currentValue", description = "Optional current value") String currentValue) {
        String valueText =
                currentValue == null || currentValue.isBlank()
                        ? "当前值未提供。"
                        : "当前值: " + currentValue + "。";
        return "字段说明: "
                + kind
                + " 的 "
                + fieldPath
                + " 用于描述该资源在 API 中的配置意图。"
                + valueText
                + "回答时应重点关注默认行为、是否会触发滚动更新、与哪些相邻字段协同，以及常见误配风险。";
    }

    @Tool(
            name = "guide-interpret-describe",
            description =
                    "Summarize how to read kubectl describe output for a resource. This tool is read-only.")
    public String interpretDescribe(
            @ToolParam(name = "kind", description = "Resource kind") String kind,
            @ToolParam(name = "name", description = "Resource name") String name,
            @ToolParam(name = "namespace", description = "Optional namespace") String namespace,
            @ToolParam(name = "describeText", description = "Raw describe output") String describeText) {
        return "请围绕以下顺序解释 describe 输出: 基础元数据、状态字段、条件 Conditions、事件 Events、容器与探针、卷与网络依赖。"
                + "目标资源: "
                + kind
                + "/"
                + (namespace == null || namespace.isBlank() ? "" : namespace + "/")
                + name
                + "。原始 describe 内容如下:\n"
                + describeText;
    }

    @Tool(
            name = "guide-recommend-command",
            description =
                    "Recommend safe kubectl commands for inspection or explanation. This tool never executes commands.")
    public String recommendCommand(
            @ToolParam(name = "goal", description = "The user's goal, such as inspect crashloop or explain service routing")
                    String goal,
            @ToolParam(name = "kind", description = "Optional resource kind") String kind,
            @ToolParam(name = "namespace", description = "Optional namespace") String namespace,
            @ToolParam(name = "name", description = "Optional resource name") String name) {
        String ns = namespace == null || namespace.isBlank() ? "" : " -n " + namespace;
        String lowerGoal = goal.toLowerCase(Locale.ROOT);
        if (lowerGoal.contains("log") || lowerGoal.contains("crash") || lowerGoal.contains("错误")) {
            return "推荐先用:\n"
                    + "kubectl get "
                    + safeKind(kind)
                    + " "
                    + safeName(name)
                    + ns
                    + "\n"
                    + "kubectl describe "
                    + safeKind(kind)
                    + " "
                    + safeName(name)
                    + ns
                    + "\n"
                    + "kubectl logs "
                    + safeName(name)
                    + ns
                    + " --tail=100";
        }
        return "推荐先用:\n"
                + "kubectl get "
                + safeKind(kind)
                + " "
                + safeName(name)
                + ns
                + " -o yaml\n"
                + "kubectl describe "
                + safeKind(kind)
                + " "
                + safeName(name)
                + ns
                + "\n"
                + "kubectl get events"
                + ns
                + " --sort-by=.metadata.creationTimestamp";
    }

    private String safeKind(String kind) {
        return kind == null || kind.isBlank() ? "resource" : kind.toLowerCase(Locale.ROOT);
    }

    private String safeName(String name) {
        return name == null || name.isBlank() ? "<name>" : name;
    }
}
