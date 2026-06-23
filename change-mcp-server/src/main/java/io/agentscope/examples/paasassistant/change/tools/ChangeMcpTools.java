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

package io.agentscope.examples.paasassistant.change.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.InputStream;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Change MCP tools exposed via AgentScope tool annotations.
 */
@Service
public class ChangeMcpTools {

    private final ObjectMapper objectMapper;

    @Value("${agentscope.mcp.ssh.username:root}")
    private String defaultUsername;

    @Value("${agentscope.mcp.ssh.password:Cebbank2@13}")
    private String defaultPassword;

    public ChangeMcpTools(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "docker-system-prune-vm",
            description = "Reclaims host disk space by cleaning up unused Docker resources (stopped containers, dangling images, build cache).")
    public String dockerSystemPrune(
            @ToolParam(name = "host", description = "Target Host (IP or hostname)") String host,
            @ToolParam(name = "port", description = "Connection Port", required = false) Integer port,
            @ToolParam(name = "username", description = "Connection Username (optional, defaults to system configured user)", required = false) String username,
            @ToolParam(name = "password", description = "Connection Password (optional, defaults to system configured password)", required = false) String password) {

        int sshPort = (port != null) ? port : 22;
        String sshUser = (username != null && !username.isEmpty()) ? username : defaultUsername;
        String sshPass = (password != null && !password.isEmpty()) ? password : defaultPassword;
        StringBuilder output = new StringBuilder();
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(sshUser, host, sshPort);
            if (sshPass != null && !sshPass.isEmpty()) {
                session.setPassword(sshPass);
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("docker system prune -f -a");

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(tmp, 0, i));
                }
                while (err.available() > 0) {
                    int i = err.read(tmp, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0 || err.available() > 0) continue;
                    output.append("\nExit status: ").append(channel.getExitStatus());
                    break;
                }
                Thread.sleep(100);
            }
            channel.disconnect();
            session.disconnect();

            Map<String, Object> result = Map.of(
                    "status", "SUCCESS",
                    "output", output.toString()
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            Map<String, Object> error = Map.of(
                    "status", "ERROR",
                    "summary", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "partialOutput", output.toString()
            );
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(error);
            } catch (Exception ignore) {
                return "{\"status\":\"ERROR\",\"summary\":\"" + e.getMessage() + "\"}";
            }
        }
    }
}
