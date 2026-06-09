package io.agentscope.examples.paasassistant.common.config;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpSyncClientWrapper;
import io.agentscope.examples.paasassistant.common.utils.SanitizingMcpClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

import java.time.Duration;
import java.util.List;

/**
 * Centralised MCP tool registration for K8s-related tool groups.
 *
 * <p>Both the <em>analyze-sub-agent</em> and <em>diagnosis-sub-agent</em> share the
 * same k8sgpt + KOM MCP tool groups.  This utility class eliminates the duplication
 * by providing a single {@link #registerK8sToolGroups} entry point.
 */
public final class McpToolRegistrar {

    private McpToolRegistrar() {
        // Utility class – do not instantiate
    }

    // =========================================================================
    // 1. k8sgpt MCP Server Configuration & Tools
    // =========================================================================
    public static final class K8sGpt {
        public static final String SERVER_NAME = "k8sgpt-mcp-server";

        public static final String TOOL_ANALYZE = "analyze";
        public static final String TOOL_LIST_FILTERS = "list-filters";
        public static final String TOOL_ADD_FILTERS = "add-filters";

        private K8sGpt() {}
    }

    // =========================================================================
    // 2. KOM MCP Server Configuration & Tools
    // =========================================================================
    public static final class Kom {
        public static final String SERVER_NAME = "kom-mcp-server";

        // Cluster Management
        public static final String TOOL_LIST_K8S_CLUSTERS = "list_k8s_clusters";

        // Node Management
        public static final String TOOL_LIST_K8S_NODE = "list_k8s_node";
        public static final String TOOL_GET_K8S_NODE_IP_USAGE = "get_k8s_node_ip_usage";
        public static final String TOOL_GET_K8S_TOP_NODE = "get_k8s_top_node";
        public static final String TOOL_GET_K8S_NODE_RESOURCE_USAGE = "get_k8s_node_resource_usage";
        public static final String TOOL_GET_K8S_POD_COUNT_RUNNING_ON_NODE = "get_k8s_pod_count_running_on_node";
        public static final String TOOL_GET_K8S_NODE_SYSTEM_LOGS = "get_k8s_node_system_logs";
        public static final String TOOL_GET_K8S_NODE_DMESG_OOM = "get_k8s_node_dmesg_oom";

        // Deployment Management
        public static final String TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_HISTORY = "get_k8s_deployment_rollout_history";
        public static final String TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_STATUS = "get_k8s_deployment_rollout_status";
        public static final String TOOL_GET_K8S_DEPLOYMENT_HPA_LIST = "get_k8s_deployment_hpa_list";
        public static final String TOOL_GET_K8S_RESOURCE_METRICS_HISTORY = "get_k8s_resource_metrics_history";

        // Pod Management
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

        // Event Management
        public static final String TOOL_LIST_K8S_EVENT = "list_k8s_event";

        // Dynamic Resource Management
        public static final String TOOL_DESCRIBE_K8S_RESOURCE = "describe_k8s_resource";
        public static final String TOOL_GET_K8S_RESOURCE = "get_k8s_resource";
        public static final String TOOL_LIST_K8S_RESOURCE = "list_k8s_resource";
        public static final String TOOL_LIST_K8S_NAMESPACE = "list_k8s_namespace";

        // Storage Management
        public static final String TOOL_GET_K8S_POD_LINKED_PV = "get_k8s_pod_linked_pv";
        public static final String TOOL_GET_K8S_POD_LINKED_PVC = "get_k8s_pod_linked_pvc";
        public static final String TOOL_GET_K8S_STORAGECLASS_PV_COUNT = "get_k8s_storageclass_pv_count";
        public static final String TOOL_GET_K8S_STORAGECLASS_PVC_COUNT = "get_k8s_storageclass_pvc_count";
        public static final String TOOL_DIAGNOSE_K8S_CSI_DRIVER = "diagnose_k8s_csi_driver";

        // ALB2 & Network Management
        public static final String TOOL_DIAGNOSE_K8S_POD_NETWORK = "diagnose_k8s_pod_network";
        public static final String TOOL_TEST_K8S_DNS_RESOLVE = "test_k8s_dns_resolve";
        public static final String TOOL_LIST_ALB2_RESOURCES = "list_alb2_resources";
        public static final String TOOL_FIND_ALB2_RESOURCES_BY_SERVICE = "find_alb2_resources_by_service";
        public static final String TOOL_LIST_ALB2_ROUTING_RULES = "list_alb2_routing_rules";
        public static final String TOOL_GET_ALB2_CONTROLLER_LOGS = "get_alb2_controller_logs";
        public static final String TOOL_DIAGNOSE_ALB2_RULE_CONFLICT = "diagnose_alb2_rule_conflict";

        // Kubectl
        public static final String TOOL_KUBECTL = "kubectl";

        private Kom() {}
    }

    // =========================================================================
    // 3. AgentScope Tool Groups Config
    // =========================================================================
    public static final class Groups {
        public static final String K8S_RESOURCE_ANALYZE = "k8s_resource_analyze";
        public static final String CLUSTER_MANAGEMENT = "cluster_management";
        public static final String NODE_MANAGEMENT = "node_management";
        public static final String DEPLOYMENT_MANAGEMENT = "deployment_management";
        public static final String POD_MANAGEMENT = "pod_management";
        public static final String DYNAMIC_RESOURCE_MANAGEMENT = "dynamic_resource_management";
        public static final String STORAGE_MANAGEMENT = "storage_management";
        public static final String ALB2_MANAGEMENT = "alb2_management";
        public static final String KUBECTL_MANAGEMENT = "kubectl_management";
        public static final String CONTAINER_INTERACTIVE = "container_interactive_management";

        public static final String DESC_K8S_RESOURCE_ANALYZE = "k8s资源问题快速分析";
        public static final String DESC_CLUSTER_MANAGEMENT = "Kubernetes多集群管理，支持列出/查询可用K8s集群列表";
        public static final String DESC_NODE_MANAGEMENT = "Kubernetes节点管理，包含节点IP/资源占用/运行Pod数量查询，支持系统日志检索和内核dmesg OOM排查";
        public static final String DESC_DEPLOYMENT_MANAGEMENT = "工作负载与部署管理，包含Deployment的Rollout历史与状态查询，HPA配置列表";
        public static final String DESC_POD_MANAGEMENT = "Pod生命周期与容器内诊断管理，支持Pod日志拉取、环境变量与关联Services/Endpoints查询";
        public static final String DESC_DYNAMIC_RESOURCE_MANAGEMENT = "Kubernetes任意资源动态诊断与历史指标(Metrics)查询管理，支持Namespace/事件列表查询、特定计算资源指标历史以及任意特定K8s资源的Get/List/Describe";
        public static final String DESC_STORAGE_MANAGEMENT = "持久化存储与PV/PVC管理，支持Pod关联存储卷绑定状态、StorageClass的PV/PVC数量统计及CSI驱动挂载卡死故障诊断";
        public static final String DESC_ALB2_MANAGEMENT = "网络连通性与路由管理，支持Pod内网络连通性探测(nc/curl/wget/临时诊断Pod测试)、CoreDNS域名解析测试及ALB2负载分流与路由规则诊断";
        public static final String DESC_KUBECTL_MANAGEMENT = "Kubectl只读兜底命令行管理，支持在专用MCP工具受限时执行只读命令(如get/describe/logs -p等)";
        public static final String DESC_CONTAINER_INTERACTIVE = "容器内文件与命令交互管理，支持容器内文件列表检索与交互式诊断命令执行";

        private Groups() {}
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Registers all shared k8sgpt + KOM MCP tool groups on the given toolkit.
     *
     * <p>This method creates two MCP clients (k8sgpt and KOM), wraps the KOM
     * client in a {@link SanitizingMcpClient}, and registers the tool groups.
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
        McpClientWrapper k8sgptMcpClient = buildMcpClient(K8sGpt.SERVER_NAME, k8sgptMcpUrl, mcpToolTimeout);
        McpClientWrapper sanitizedK8sGptClient = new SanitizingMcpClient(
                k8sgptMcpClient,
                () -> buildMcpClient(K8sGpt.SERVER_NAME, k8sgptMcpUrl, mcpToolTimeout)
        );

        // 1. 资源问题快速分析
        toolkit.createToolGroup(Groups.K8S_RESOURCE_ANALYZE, Groups.DESC_K8S_RESOURCE_ANALYZE, true);
        toolkit.registration().mcpClient(sanitizedK8sGptClient)
                .enableTools(List.of(K8sGpt.TOOL_ANALYZE, K8sGpt.TOOL_LIST_FILTERS, K8sGpt.TOOL_ADD_FILTERS))
                .group(Groups.K8S_RESOURCE_ANALYZE)
                .apply();

        // KOM MCP client (with SanitizingMcpClient wrapper)
        McpClientWrapper komMcpClient = buildMcpClient(Kom.SERVER_NAME, k8sMcpUrl, mcpToolTimeout);
        McpClientWrapper sanitizedK8sClient = new SanitizingMcpClient(
                komMcpClient,
                () -> buildMcpClient(Kom.SERVER_NAME, k8sMcpUrl, mcpToolTimeout)
        );

        // 1. 集群管理 (Cluster Management)
        toolkit.createToolGroup(Groups.CLUSTER_MANAGEMENT, Groups.DESC_CLUSTER_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(Kom.TOOL_LIST_K8S_CLUSTERS))
                .group(Groups.CLUSTER_MANAGEMENT).apply();

        // 2. 节点管理 (Node)
        toolkit.createToolGroup(Groups.NODE_MANAGEMENT, Groups.DESC_NODE_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        Kom.TOOL_LIST_K8S_NODE, Kom.TOOL_GET_K8S_NODE_IP_USAGE,
                        Kom.TOOL_GET_K8S_TOP_NODE, Kom.TOOL_GET_K8S_NODE_RESOURCE_USAGE,
                        Kom.TOOL_GET_K8S_POD_COUNT_RUNNING_ON_NODE,
                        Kom.TOOL_GET_K8S_NODE_SYSTEM_LOGS, Kom.TOOL_GET_K8S_NODE_DMESG_OOM))
                .group(Groups.NODE_MANAGEMENT).apply();

        // 3. 部署管理 (Deployment)
        toolkit.createToolGroup(Groups.DEPLOYMENT_MANAGEMENT, Groups.DESC_DEPLOYMENT_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        Kom.TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_HISTORY, Kom.TOOL_GET_K8S_DEPLOYMENT_ROLLOUT_STATUS,
                        Kom.TOOL_GET_K8S_DEPLOYMENT_HPA_LIST))
                .group(Groups.DEPLOYMENT_MANAGEMENT).apply();

        // 4. Pod管理
        toolkit.createToolGroup(Groups.POD_MANAGEMENT, Groups.DESC_POD_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        Kom.TOOL_DESCRIBE_K8S_POD, Kom.TOOL_LIST_K8S_POD, Kom.TOOL_LIST_K8S_POD_EVENT,
                        Kom.TOOL_GET_K8S_POD_LOGS, Kom.TOOL_GET_K8S_POD_LINKED_ENV, Kom.TOOL_GET_POD_LINKED_ENV_FROM_YAML,
                        Kom.TOOL_GET_K8S_POD_LINKED_SERVICES, Kom.TOOL_GET_POD_LINKED_ENDPOINTS,
                        Kom.TOOL_GET_K8S_POD_RESOURCE_USAGE, Kom.TOOL_GET_K8S_TOP_POD))
                .group(Groups.POD_MANAGEMENT).apply();

        // 5. 资源管理 (Dynamic Resource/CRD)
        toolkit.createToolGroup(Groups.DYNAMIC_RESOURCE_MANAGEMENT, Groups.DESC_DYNAMIC_RESOURCE_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        Kom.TOOL_DESCRIBE_K8S_RESOURCE, Kom.TOOL_GET_K8S_RESOURCE,
                        Kom.TOOL_LIST_K8S_RESOURCE, Kom.TOOL_LIST_K8S_NAMESPACE,
                        Kom.TOOL_LIST_K8S_EVENT, Kom.TOOL_GET_K8S_RESOURCE_METRICS_HISTORY))
                .group(Groups.DYNAMIC_RESOURCE_MANAGEMENT).apply();

        // 6. 存储管理 (Storage)
        toolkit.createToolGroup(Groups.STORAGE_MANAGEMENT, Groups.DESC_STORAGE_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        Kom.TOOL_GET_K8S_POD_LINKED_PV, Kom.TOOL_GET_K8S_POD_LINKED_PVC,
                        Kom.TOOL_GET_K8S_STORAGECLASS_PV_COUNT, Kom.TOOL_GET_K8S_STORAGECLASS_PVC_COUNT,
                        Kom.TOOL_DIAGNOSE_K8S_CSI_DRIVER))
                .group(Groups.STORAGE_MANAGEMENT).apply();

        // 7. ALB2与网络路由管理
        toolkit.createToolGroup(Groups.ALB2_MANAGEMENT, Groups.DESC_ALB2_MANAGEMENT, true);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(
                        Kom.TOOL_DIAGNOSE_K8S_POD_NETWORK, Kom.TOOL_TEST_K8S_DNS_RESOLVE,
                        Kom.TOOL_LIST_ALB2_RESOURCES, Kom.TOOL_FIND_ALB2_RESOURCES_BY_SERVICE,
                        Kom.TOOL_LIST_ALB2_ROUTING_RULES, Kom.TOOL_GET_ALB2_CONTROLLER_LOGS,
                        Kom.TOOL_DIAGNOSE_ALB2_RULE_CONFLICT))
                .group(Groups.ALB2_MANAGEMENT).apply();

        // 8. 容器内文件与命令交互管理 (Container Interactive)
        toolkit.createToolGroup(Groups.CONTAINER_INTERACTIVE, Groups.DESC_CONTAINER_INTERACTIVE, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(Kom.TOOL_LIST_FILES_IN_K8S_POD))
                .group(Groups.CONTAINER_INTERACTIVE).apply();

        // 9. Kubectl 兜底管理
        toolkit.createToolGroup(Groups.KUBECTL_MANAGEMENT, Groups.DESC_KUBECTL_MANAGEMENT, false);
        toolkit.registration().mcpClient(sanitizedK8sClient)
                .enableTools(List.of(Kom.TOOL_KUBECTL))
                .group(Groups.KUBECTL_MANAGEMENT).apply();

        // 注册 Meta Tool，允许 LLM 在运行时动态激活或停用上述工具组
        toolkit.registerMetaTool();
    }

    private static McpClientWrapper buildMcpClient(String serverName, String mcpUrl, Duration timeout) {
        String baseUrl = mcpUrl;
        String endpoint = "/mcp";
        if (mcpUrl.endsWith("/mcp")) {
            baseUrl = mcpUrl.substring(0, mcpUrl.length() - 4);
        } else if (mcpUrl.endsWith("/mcp/")) {
            baseUrl = mcpUrl.substring(0, mcpUrl.length() - 5);
        }

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint(endpoint)
                .build();

        io.modelcontextprotocol.spec.McpSchema.Implementation clientInfo =
                new io.modelcontextprotocol.spec.McpSchema.Implementation(
                        "agentscope-java", "AgentScope Java Framework", io.agentscope.core.Version.VERSION);

        McpSyncClient mcpClient = McpClient.sync(transport)
                .requestTimeout(timeout)
                .clientInfo(clientInfo)
                .build();

        return new McpSyncClientWrapper(serverName, mcpClient);
    }
}
