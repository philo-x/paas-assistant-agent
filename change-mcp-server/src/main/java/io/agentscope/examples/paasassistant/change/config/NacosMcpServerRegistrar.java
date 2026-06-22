/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.examples.paasassistant.change.config;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NacosMcpServerRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(NacosMcpServerRegistrar.class);

    @Autowired
    private AiService aiService;

    @Autowired
    private McpSyncServer mcpSyncServer;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${server.port}")
    private int serverPort;

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${agentscope.mcp.nacos.namespace:public}")
    private String namespace;

    @Value("${agentscope.mcp.nacos.registry.enabled:true}")
    private boolean registryEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!registryEnabled) {
            logger.info("Nacos MCP Registry is disabled.");
            return;
        }

        try {
            // Convert MCP tools to Nacos tools
            List<McpTool> nacosTools = mcpSyncServer.listTools().stream()
                    .map(tool -> {
                        McpTool nacosTool = new McpTool();
                        nacosTool.setName(tool.name());
                        nacosTool.setDescription(tool.description());
                        // Convert JsonSchema record to Map
                        Map<String, Object> schemaMap = objectMapper.convertValue(tool.inputSchema(), new TypeReference<Map<String, Object>>() {});
                        nacosTool.setInputSchema(schemaMap);
                        return nacosTool;
                    })
                    .collect(Collectors.toList());

            McpToolSpecification toolSpec = new McpToolSpecification();
            toolSpec.setTools(nacosTools);

            McpServerBasicInfo basicInfo = new McpServerBasicInfo();
            basicInfo.setName(serviceName);
            basicInfo.setDescription("Change MCP Server for generic remote operations");
            basicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
            basicInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
            basicInfo.setEnabled(true);
            basicInfo.setVersion("1.0.0");
            ServerVersionDetail versionDetail = new ServerVersionDetail();
            versionDetail.setVersion("1.0.0");
            versionDetail.setRelease_date(LocalDate.now().toString());
            versionDetail.setIs_latest(true);
            basicInfo.setVersionDetail(versionDetail);
            McpServerRemoteServiceConfig remoteServerConfig = new McpServerRemoteServiceConfig();
            remoteServerConfig.setExportPath("/mcp/sse");
            basicInfo.setRemoteServerConfig(remoteServerConfig);

            McpEndpointSpec endpointSpec = new McpEndpointSpec();
            endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
            Map<String, String> endpointData = new HashMap<>();
            endpointData.put("serviceName", serviceName);
            endpointData.put("groupName", "DEFAULT_GROUP");
            endpointData.put("namespaceId", namespace);
            endpointSpec.setData(endpointData);

            try {
                aiService.getMcpServer(serviceName, "1.0.0");
            } catch (NacosException exception) {
                if (exception.getErrCode() == NacosException.NOT_FOUND) {
                    aiService.releaseMcpServer(basicInfo, toolSpec, endpointSpec);
                } else {
                    throw exception;
                }
            }
            String host = NetUtils.localIp();
            aiService.registerMcpServerEndpoint(serviceName, host, serverPort);

            logger.info("Successfully registered MCP Server [{}] to Nacos via {}:{}", serviceName, host, serverPort);
            logger.info("Registered tools: {}", nacosTools.stream().map(McpTool::getName).collect(Collectors.toList()));

        } catch (Exception e) {
            logger.error("Failed to register MCP Server to Nacos", e);
        }
    }
}
