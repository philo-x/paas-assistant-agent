package io.agentscope.examples.paasassistant.common.config;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.examples.paasassistant.common.utils.SanitizingMcpClient;

import java.time.Duration;
import java.util.List;

/**
 * Centralised MCP tool registration for K8s-related tool groups.
 *
 * <p>Both the <em>analyze-sub-agent</em> and <em>diagnosis-sub-agent</em> share the
 * same k8sgpt + KOM MCP tool groups.  This utility class eliminates the duplication
 * by providing a single {@link #registerK8sToolGroups} entry point.
 *
 * <p>The only known difference between the two callers is whether the
 * {@code kubectl_management} tool group is enabled by default, which is
 * controlled via the {@code kubectlEnabledByDefault} parameter.
 */
public final class McpToolRegistrar {

    private McpToolRegistrar() {
        // Utility class – do not instantiate
    }

    // -------------------------------------------------------------------------
    // MCP Server Names
    // -------------------------------------------------------------------------

    public static final String MCP_SERVER_K8SGPT = "k8sgpt-mcp-server";
    public static final String MCP_SERVER_KOM = "kom-mcp-server";

    // -------------------------------------------------------------------------
    // k8sgpt MCP Tool Names
    // -------------------------------------------------------------------------

    public static final String TOOL_ANALYZE = "analyze";
    public static final String TOOL_LIST_FILTERS = "list-filters";
    public static final String TOOL_ADD_FILTERS = "add-filters";

    // -------------------------------------------------------------------------
    // Tool Group Names
    // -------------------------------------------------------------------------

    public static final String GROUP_K8S_RESOURCE_ANALYZE = "k8s_resource_analyze";
    public static final String GROUP_CLUSTER_MANAGEMENT = "cluster_management";
    public static final String GROUP_NODE_MANAGEMENT = "node_management";
    public static final String GROUP_DEPLOYMENT_MANAGEMENT = "deployment_management";
    public static final String GROUP_POD_MANAGEMENT = "pod_management";
    public static final String GROUP_EVENT_MANAGEMENT = "event_management";
    public static final String GROUP_DYNAMIC_RESOURCE_MANAGEMENT = "dynamic_resource_management";
    public static final String GROUP_STORAGE_MANAGEMENT = "storage_management";
    public static final String GROUP_ALB2_MANAGEMENT = "alb2_management";
    public static final String GROUP_KUBECTL_MANAGEMENT = "kubectl_management";

    // -------------------------------------------------------------------------
    // Tool Group Descriptions
    // -------------------------------------------------------------------------

    public static final String DESC_K8S_RESOURCE_ANALYZE = "k8s资源问题快速分析";
    public static final String DESC_CLUSTER_MANAGEMENT = "Kubernetes多集群管理，支持列出/查询可用K8s集群列表";
    public static final String DESC_NODE_MANAGEMENT = "Kubernetes节点管理，包含节点IP/资源占用/运行Pod数量查询，支持系统日志检索和内核dmesg OOM排查";
    public static final String DESC_DEPLOYMENT_MANAGEMENT = "工作负载与部署管理，包含Deployment的Rollout历史与状态查询，HPA配置列表及资源历史指标(PromQL历史曲线)";
    public static final String DESC_POD_MANAGEMENT = "Pod生命周期与容器内诊断管理，支持Pod日志拉取、环境变量与关联Services/Endpoints查询、容器内命令执行与文件列表检索";
    public static final String DESC_EVENT_MANAGEMENT = "Kubernetes事件诊断管理，支持通过命名空间和涉及对象名称/类型过滤检索集群事件";
    public static final String DESC_DYNAMIC_RESOURCE_MANAGEMENT = "Kubernetes任意资源与CRD动态诊断管理，支持命名空间列表查询以及任意特定类型K8s资源(如Quota等)的Get/List/Describe";
    public static final String DESC_STORAGE_MANAGEMENT = "持久化存储与PV/PVC管理，支持Pod关联存储卷绑定状态、StorageClass的PV/PVC数量统计及CSI驱动挂载卡死故障诊断";
    public static final String DESC_ALB2_MANAGEMENT = "网络连通性与路由管理，支持Pod内网络连通性探测(nc/curl/wget/临时诊断Pod测试)、CoreDNS域名解析测试及ALB2负载分流与路由规则诊断";
    public static final String DESC_KUBECTL_MANAGEMENT = "Kubectl只读兜底命令行管理，支持在专用MCP工具受限时执行只读命令(如get/describe/logs -p等)";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Cluster Management
    // -------------------------------------------------------------------------

    public static final String TOOL_LIST_K8S_CLUSTERS = "list_k8s_clusters";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Node Management
    // -------------------------------------------------------------------------

    public static final String TOOL_LIST_K8S_NODE = "list_k8s_node";
    public static final String TOOL_GET_K8S_NODE_IP_USAGE = "get_k8s_node_ip_usage";
    public static final String TOOL_GET_K8S_TOP_NODE = "get_k8s_top_node";
    public static final String TOOL_GET_K8S_NODE_RESOURCE_USAGE = "get_k8s_node_resource_usage";
    public static final String TOOL_GET_K8S_POD_COUNT_RUNNING_ON_NODE = "get_k8s_pod_count_running_on_node";
    public static final String TOOL_GET_K8S_NODE_SYSTEM_LOGS = "get_k8s_node_system_logs";
    public static final String TOOL_GET_K8S_NODE_DMESG_OOM = "get_k8s_node_dmesg_oom";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Deployment Management
    // -------------------------------------------------------------------------

    public static final String TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_HISTORY = "get_k8s_deployment_rollout_history";
    public static final String TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_STATUS = "get_k8s_deployment_rollout_status";
    public static final String TOOL_GET_K8S_DEPLOYMENT_HPA_LIST = "get_k8s_deployment_hpa_list";
    public static final String TOOL_GET_K8S_RESOURCE_METRICS_HISTORY = "get_k8s_resource_metrics_history";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Pod Management
    // -------------------------------------------------------------------------

    public static final String TOOL_DESCRIBE_K8S_POD = "describe_k8s_pod";
    public static final String TOOL_LIST_K8S_POD = "list_k8s_pod";
    public static final String TOOL_LIST_K8S_POD_EVENT = "list_k8s_pod_event";
    public static final String TOOL_GET_K8S_POD_LOGS = "get_k8s_pod_logs";
    public static final String TOOL_GET_K8S_POD_LINKED_ENV = "get_k8s_pod_linked_env";
    public static final String TOOL_GET_POD_LINKED_ENV_FROM_YAML = "get_pod_linked_env_from_yaml";
    public static final String TOOL_GET_K8S_POD_LINKED_SERVICES = "get_k8s_pod_linked_services";
    public static final String TOOL_GET_POD_LINKED_ENDPOINTS = "get_pod_linked_endpoints";
    public static final String TOOL_GET_K8S_POD_RESOURCE_USAGE = "get_k8s_pod_resource_usage";
    public static final String TOOL_GET_K8S_TOP_POD = "get_k8s_top_pod";
    public static final String TOOL_LIST_FILES_IN_K8S_POD = "list_files_in_k8s_pod";
    public static final String TOOL_LIST_POD_ALL_FILES = "list_pod_all_files";
    public static final String TOOL_RUN_COMMAND_IN_K8S_POD = "run_command_in_k8s_pod";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Event Management
    // -------------------------------------------------------------------------

    public static final String TOOL_LIST_K8S_EVENT = "list_k8s_event";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Dynamic Resource Management
    // -------------------------------------------------------------------------

    public static final String TOOL_DESCRIBE_K8S_RESOURCE = "describe_k8s_resource";
    public static final String TOOL_GET_K8S_RESOURCE = "get_k8s_resource";
    public static final String TOOL_LIST_K8S_RESOURCE = "list_k8s_resource";
    public static final String TOOL_LIST_K8S_NAMESPACE = "list_k8s_namespace";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Storage Management
    // -------------------------------------------------------------------------

    public static final String TOOL_GET_K8S_POD_LINKED_PV = "get_k8s_pod_linked_pv";
    public static final String TOOL_GET_K8S_POD_LINKED_PVC = "get_k8s_pod_linked_pvc";
    public static final String TOOL_GET_K8S_STORAGECLASS_PV_COUNT = "get_k8s_storageclass_pv_count";
    public static final String TOOL_GET_K8S_STORAGECLASS_PVC_COUNT = "get_k8s_storageclass_pvc_count";
    public static final String TOOL_DIAGNOSE_K8S_CSI_DRIVER = "diagnose_k8s_csi_driver";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – ALB2 & Network Management
    // -------------------------------------------------------------------------

    public static final String TOOL_DIAGNOSE_K8S_POD_NETWORK = "diagnose_k8s_pod_network";
    public static final String TOOL_TEST_K8S_DNS_RESOLVE = "test_k8s_dns_resolve";

    // -------------------------------------------------------------------------
    // KOM MCP Tool Names – Kubectl
    // -------------------------------------------------------------------------

    public static final String TOOL_KUBECTL = "kubectl";

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Registers all shared k8sgpt + KOM MCP tool groups on the given toolkit.
     *
     * <p>This method creates two MCP clients (k8sgpt and KOM), wraps the KOM
     * client in a {@link SanitizingMcpClient}, and registers the following
     * 10 tool groups:
     * <ol>
     *   <li>k8s_resource_analyze (k8sgpt)</li>
     *   <li>cluster_management</li>
     *   <li>node_management</li>
     *   <li>deployment_management</li>
     *   <li>pod_management</li>
     *   <li>event_management</li>
     *   <li>dynamic_resource_management</li>
     *   <li>storage_management</li>
     *   <li>alb2_management</li>
     *   <li>kubectl_management</li>
     * </ol>
     *
     * <p>Finally, it registers the Meta Tool to allow the LLM to dynamically
     * activate/deactivate tool groups at runtime.
     *
     * @param toolkit                 the toolkit to register tools on
     * @param k8sgptMcpUrl            URL for the k8sgpt MCP server
     * @param k8sMcpUrl               URL for the KOM MCP server
     * @param mcpToolTimeout          timeout for MCP tool calls
     */
    public static void registerK8sToolGroups(
            Toolkit toolkit,
            String k8sgptMcpUrl,
            String k8sMcpUrl,
            Duration mcpToolTimeout) {

        // k8sgpt MCP 工具注册
        McpClientWrapper k8sgptMcpClient = McpClientBuilder.create(MCP_SERVER_K8SGPT)
                .streamableHttpTransport(k8sgptMcpUrl)
                .timeout(mcpToolTimeout)
                .buildSync();

        // 1. 资源问题快速分析
        toolkit.createToolGroup(GROUP_K8S_RESOURCE_ANALYZE, DESC_K8S_RESOURCE_ANALYZE, true);
        toolkit.registration().mcpClient(k8sgptMcpClient)
                .enableTools(List.of(TOOL_ANALYZE, TOOL_LIST_FILTERS, TOOL_ADD_FILTERS))
                .group(GROUP_K8S_RESOURCE_ANALYZE)
                .apply();

        // KOM MCP client (with SanitizingMcpClient wrapper)
        McpClientWrapper komMcpClient = McpClientBuilder.create(MCP_SERVER_KOM)
                .streamableHttpTransport(k8sMcpUrl)
                .timeout(mcpToolTimeout)
                .buildSync();
        McpClientWrapper sanitizedK8sClient = new SanitizingMcpClient(komMcpClient);

        // 1. 集群管理 (Cluster Management)
        toolkit.createToolGroup(GROUP_CLUSTER_MANAGEMENT, DESC_CLUSTER_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(TOOL_LIST_K8S_CLUSTERS))
                .group(GROUP_CLUSTER_MANAGEMENT).apply();

        // 2. 节点管理 (Node)
        toolkit.createToolGroup(GROUP_NODE_MANAGEMENT, DESC_NODE_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        TOOL_LIST_K8S_NODE, TOOL_GET_K8S_NODE_IP_USAGE,
                        TOOL_GET_K8S_TOP_NODE, TOOL_GET_K8S_NODE_RESOURCE_USAGE,
                        TOOL_GET_K8S_POD_COUNT_RUNNING_ON_NODE,
                        TOOL_GET_K8S_NODE_SYSTEM_LOGS, TOOL_GET_K8S_NODE_DMESG_OOM))
                .group(GROUP_NODE_MANAGEMENT).apply();

        // 3. 部署管理 (Deployment)
        toolkit.createToolGroup(GROUP_DEPLOYMENT_MANAGEMENT, DESC_DEPLOYMENT_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_HISTORY, TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_STATUS,
                        TOOL_GET_K8S_DEPLOYMENT_HPA_LIST, TOOL_GET_K8S_RESOURCE_METRICS_HISTORY))
                .group(GROUP_DEPLOYMENT_MANAGEMENT).apply();

        // 4. Pod管理
        toolkit.createToolGroup(GROUP_POD_MANAGEMENT, DESC_POD_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        TOOL_DESCRIBE_K8S_POD, TOOL_LIST_K8S_POD, TOOL_LIST_K8S_POD_EVENT,
                        TOOL_GET_K8S_POD_LOGS, TOOL_GET_K8S_POD_LINKED_ENV, TOOL_GET_POD_LINKED_ENV_FROM_YAML,
                        TOOL_GET_K8S_POD_LINKED_SERVICES, TOOL_GET_POD_LINKED_ENDPOINTS,
                        TOOL_GET_K8S_POD_RESOURCE_USAGE, TOOL_GET_K8S_TOP_POD,
                        TOOL_LIST_FILES_IN_K8S_POD, TOOL_LIST_POD_ALL_FILES,
                        TOOL_RUN_COMMAND_IN_K8S_POD))
                .group(GROUP_POD_MANAGEMENT).apply();

        // 5. 事件管理 (Event)
        toolkit.createToolGroup(GROUP_EVENT_MANAGEMENT, DESC_EVENT_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(TOOL_LIST_K8S_EVENT))
                .group(GROUP_EVENT_MANAGEMENT).apply();

        // 6. 资源管理 (Dynamic Resource/CRD)
        toolkit.createToolGroup(GROUP_DYNAMIC_RESOURCE_MANAGEMENT, DESC_DYNAMIC_RESOURCE_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        TOOL_DESCRIBE_K8S_RESOURCE, TOOL_GET_K8S_RESOURCE,
                        TOOL_LIST_K8S_RESOURCE, TOOL_LIST_K8S_NAMESPACE))
                .group(GROUP_DYNAMIC_RESOURCE_MANAGEMENT).apply();

        // 7. 存储管理 (Storage)
        toolkit.createToolGroup(GROUP_STORAGE_MANAGEMENT, DESC_STORAGE_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        TOOL_GET_K8S_POD_LINKED_PV, TOOL_GET_K8S_POD_LINKED_PVC,
                        TOOL_GET_K8S_STORAGECLASS_PV_COUNT, TOOL_GET_K8S_STORAGECLASS_PVC_COUNT,
                        TOOL_DIAGNOSE_K8S_CSI_DRIVER))
                .group(GROUP_STORAGE_MANAGEMENT).apply();

        // 8. ALB2与网络路由管理
        toolkit.createToolGroup(GROUP_ALB2_MANAGEMENT, DESC_ALB2_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        TOOL_DIAGNOSE_K8S_POD_NETWORK, TOOL_TEST_K8S_DNS_RESOLVE))
                .group(GROUP_ALB2_MANAGEMENT).apply();

        // 9. Kubectl 兜底管理
        toolkit.createToolGroup(GROUP_KUBECTL_MANAGEMENT, DESC_KUBECTL_MANAGEMENT,true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(TOOL_KUBECTL))
                .group(GROUP_KUBECTL_MANAGEMENT).apply();

        // 注册 Meta Tool，允许 LLM 在运行时动态激活或停用上述工具组
        toolkit.registerMetaTool();
    }
}
