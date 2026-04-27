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

package io.agentscope.examples.paasassistant.platform.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.examples.paasassistant.platform.model.MutationActionType;
import io.agentscope.examples.paasassistant.platform.model.MutationPlan;
import io.agentscope.examples.paasassistant.platform.model.ResourceRef;
import io.agentscope.examples.paasassistant.platform.service.KubernetesMutationService;
import io.agentscope.examples.paasassistant.platform.service.OperationApprovalService;
import io.agentscope.examples.paasassistant.platform.service.PatchValidationService;
import io.agentscope.examples.paasassistant.platform.service.ResourceValidationService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Platform MCP tools exposed via AgentScope tool annotations.
 */
@Service
public class PlatformMcpTools {

    private final ResourceValidationService validationService;
    private final OperationApprovalService approvalService;
    private final KubernetesMutationService mutationService;
    private final PatchValidationService patchValidationService;
    private final ObjectMapper objectMapper;

    public PlatformMcpTools(
            ResourceValidationService validationService,
            OperationApprovalService approvalService,
            KubernetesMutationService mutationService,
            PatchValidationService patchValidationService,
            ObjectMapper objectMapper) {
        this.validationService = validationService;
        this.approvalService = approvalService;
        this.mutationService = mutationService;
        this.patchValidationService = patchValidationService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "change-plan-restart",
            description =
                    "Create a restart execution plan for a workload."
                            + " This does not execute the change.")
    public String changePlanRestart(
            @ToolParam(name = "chatId", description = "Conversation ID used for approval tracking.")
                    String chatId,
            @ToolParam(name = "userId", description = "User ID requesting the change.")
                    String userId,
            @ToolParam(
                            name = "kind",
                            description = "Workload kind: Deployment, StatefulSet or DaemonSet.")
                    String kind,
            @ToolParam(name = "namespace", description = "Workload namespace.") String namespace,
            @ToolParam(name = "name", description = "Workload name.") String name) {
        try {
            ResourceRef resourceRef =
                    validationService.buildResourceRef(kind, namespace, name);
            validationService.validateRestartTarget(resourceRef.kind());
            MutationPlan plan =
                    approvalService.createPlan(
                            chatId,
                            userId,
                            MutationActionType.RESTART_WORKLOAD,
                            resourceRef,
                            Map.of("kind", resourceRef.kind()),
                            "MEDIUM",
                            "Restart workload " + resourceRef.format(),
                            "If this increases impact, pause and inspect rollout history"
                                    + " before retrying.");
            return toJson(planResponse(plan));
        } catch (Exception exception) {
            return errorJson(exception);
        }
    }

    @Tool(
            name = "change-plan-scale",
            description =
                    "Create a scale execution plan for a workload."
                            + " This does not execute the change.")
    public String changePlanScale(
            @ToolParam(name = "chatId", description = "Conversation ID used for approval tracking.")
                    String chatId,
            @ToolParam(name = "userId", description = "User ID requesting the change.")
                    String userId,
            @ToolParam(
                            name = "kind",
                            description = "Scalable workload kind: Deployment or StatefulSet.")
                    String kind,
            @ToolParam(name = "namespace", description = "Workload namespace.") String namespace,
            @ToolParam(name = "name", description = "Workload name.") String name,
            @ToolParam(name = "targetReplicas", description = "Target replica count.")
                    int targetReplicas) {
        try {
            ResourceRef resourceRef =
                    validationService.buildResourceRef(kind, namespace, name);
            validationService.validateScaleTarget(resourceRef.kind());
            MutationPlan plan =
                    approvalService.createPlan(
                            chatId,
                            userId,
                            MutationActionType.SCALE_WORKLOAD,
                            resourceRef,
                            Map.of("targetReplicas", targetReplicas),
                            targetReplicas == 0 ? "HIGH" : "MEDIUM",
                            "Scale "
                                    + resourceRef.format()
                                    + " to "
                                    + targetReplicas
                                    + " replicas",
                            "Scale back to the previous replica count if the change degrades"
                                    + " availability.");
            return toJson(planResponse(plan));
        } catch (Exception exception) {
            return errorJson(exception);
        }
    }

    @Tool(
            name = "change-plan-delete-pod",
            description =
                    "Create a pod deletion execution plan."
                            + " This does not execute the change.")
    public String changePlanDeletePod(
            @ToolParam(name = "chatId", description = "Conversation ID used for approval tracking.")
                    String chatId,
            @ToolParam(name = "userId", description = "User ID requesting the change.")
                    String userId,
            @ToolParam(name = "namespace", description = "Pod namespace.") String namespace,
            @ToolParam(name = "name", description = "Pod name.") String name) {
        try {
            ResourceRef resourceRef =
                    validationService.buildResourceRef("Pod", namespace, name);
            MutationPlan plan =
                    approvalService.createPlan(
                            chatId,
                            userId,
                            MutationActionType.DELETE_POD,
                            resourceRef,
                            Map.of("force", false),
                            "MEDIUM",
                            "Delete pod " + resourceRef.format(),
                            "If this is not managed by a workload, you will need to recreate"
                                    + " the pod manually.");
            return toJson(planResponse(plan));
        } catch (Exception exception) {
            return errorJson(exception);
        }
    }

    @Tool(
            name = "change-plan-patch",
            description =
                    "Create a patch execution plan for a supported resource."
                            + " This does not execute the change.")
    public String changePlanPatch(
            @ToolParam(name = "chatId", description = "Conversation ID used for approval tracking.")
                    String chatId,
            @ToolParam(name = "userId", description = "User ID requesting the change.")
                    String userId,
            @ToolParam(name = "kind", description = "Resource kind.") String kind,
            @ToolParam(
                            name = "namespace",
                            description = "Namespace for namespaced resources.",
                            required = false)
                    String namespace,
            @ToolParam(name = "name", description = "Resource name.") String name,
            @ToolParam(
                            name = "patch",
                            description =
                                    "JSON merge patch payload limited by the platform whitelist.")
                    Map<String, Object> patch) {
        try {
            ResourceRef resourceRef =
                    validationService.buildResourceRef(kind, namespace, name);
            List<String> validatedPaths = patchValidationService.validatePatch(patch);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patch", patch);
            payload.put("validatedPaths", validatedPaths);
            MutationPlan plan =
                    approvalService.createPlan(
                            chatId,
                            userId,
                            MutationActionType.PATCH_RESOURCE,
                            resourceRef,
                            payload,
                            "HIGH",
                            "Patch "
                                    + resourceRef.format()
                                    + " with validated fields only",
                            "Restore the previous field values with another approved patch"
                                    + " if the outcome is not expected.");
            return toJson(planResponse(plan));
        } catch (Exception exception) {
            return errorJson(exception);
        }
    }

    @Tool(
            name = "change-execute",
            description =
                    "Execute a previously planned change by approval_id."
                            + " Requires explicit user confirmation or a pre-approved record.")
    public String changeExecute(
            @ToolParam(
                            name = "approvalId",
                            description = "Approval ID returned by a change-plan tool.")
                    String approvalId,
            @ToolParam(
                            name = "executorUserId",
                            description = "User executing the approved plan.")
                    String executorUserId,
            @ToolParam(
                            name = "confirmed",
                            description =
                                    "Whether the user explicitly confirmed this execution"
                                            + " in the current turn.",
                            required = false)
                    Boolean confirmed) {
        try {
            boolean isConfirmed = confirmed != null && confirmed;
            MutationPlan plan =
                    approvalService.prepareForExecution(approvalId, executorUserId, isConfirmed);
            Map<String, Object> execution = mutationService.execute(plan, executorUserId);
            return toJson(execution);
        } catch (Exception exception) {
            return errorJson(exception);
        }
    }

    @Tool(
            name = "change-get-status",
            description =
                    "Read approval and execution status for a planned or executed change.")
    public String changeGetStatus(
            @ToolParam(
                            name = "approvalId",
                            description = "Approval ID to inspect.",
                            required = false)
                    String approvalId,
            @ToolParam(
                            name = "executionId",
                            description = "Optional execution ID to inspect directly.",
                            required = false)
                    String executionId) {
        try {
            return toJson(approvalService.getStatus(approvalId, executionId));
        } catch (Exception exception) {
            return errorJson(exception);
        }
    }

    // ---- internal helpers ----

    private Map<String, Object> planResponse(MutationPlan plan) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approval_id", plan.approvalId());
        response.put("execution_id", null);
        response.put("resource_ref", plan.resourceRef().format());
        response.put("action", plan.actionType().toolAction());
        response.put("status", plan.status().name());
        response.put("summary", plan.summary());
        response.put("rollback_hint", plan.rollbackHint());
        response.put("risk_level", plan.riskLevel());
        response.put("expires_at", plan.expiresAt().toString());
        response.put("plan_payload", plan.planPayload());
        return response;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return errorJson(exception);
        }
    }

    private String errorJson(Exception exception) {
        Map<String, Object> payload =
                Map.of(
                        "status", "ERROR",
                        "summary", exception.getMessage() != null ? exception.getMessage() : "Unknown error");
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException jsonException) {
            return "{\"status\":\"ERROR\",\"summary\":\""
                    + exception.getMessage()
                    + "\"}";
        }
    }
}
