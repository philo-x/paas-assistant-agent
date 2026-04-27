package io.agentscope.examples.paasassistant.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.examples.paasassistant.platform.model.MutationPlan;
import io.agentscope.examples.paasassistant.platform.model.OperationExecutionRecord;
import io.agentscope.examples.paasassistant.platform.model.ResourceRef;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Executes approved Kubernetes mutations with the Fabric8 client.
 */
@Service
public class KubernetesMutationService {

    private final KubernetesClient kubernetesClient;
    private final OperationApprovalService approvalService;
    private final ObjectMapper objectMapper;

    public KubernetesMutationService(
            KubernetesClient kubernetesClient,
            OperationApprovalService approvalService,
            ObjectMapper objectMapper) {
        this.kubernetesClient = kubernetesClient;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> execute(MutationPlan plan, String executorUserId) {
        String executionId =
                approvalService.createExecution(
                        plan.approvalId(),
                        executorUserId,
                        Map.of(
                                "approvalId", plan.approvalId(),
                                "action", plan.actionType().toolAction(),
                                "resourceRef", plan.resourceRef().format()));
        try {
            String resultSummary =
                    switch (plan.actionType()) {
                        case RESTART_WORKLOAD -> restartWorkload(plan.resourceRef());
                        case SCALE_WORKLOAD ->
                                scaleWorkload(plan.resourceRef(), plan.planPayload());
                        case DELETE_POD -> deletePod(plan.resourceRef());
                        case PATCH_RESOURCE ->
                                patchResource(plan.resourceRef(), plan.planPayload());
                    };
            approvalService.markApprovalExecuted(plan.approvalId());
            OperationExecutionRecord execution =
                    approvalService.finishExecution(
                            executionId, true, resultSummary, null);
            return buildResponse(plan, execution, "SUCCEEDED");
        } catch (Exception exception) {
            approvalService.markApprovalFailed(plan.approvalId());
            OperationExecutionRecord execution =
                    approvalService.finishExecution(
                            executionId, false, null, exception.getMessage());
            return buildResponse(plan, execution, "FAILED");
        }
    }

    private String restartWorkload(ResourceRef ref) {
        if (!List.of("Deployment", "StatefulSet", "DaemonSet").contains(ref.kind())) {
            throw new IllegalArgumentException(
                    "Restart is not supported for kind: " + ref.kind());
        }
        String restartTimestamp = Instant.now().toString();
        String patchJson =
                """
                {
                  "spec": {
                    "template": {
                      "metadata": {
                        "annotations": {
                          "kubectl.kubernetes.io/restartedAt": "%s"
                        }
                      }
                    }
                  }
                }
                """
                        .formatted(restartTimestamp);
        kubernetesClient.genericKubernetesResources(ref.apiVersion(), ref.kind())
                .inNamespace(ref.namespace())
                .withName(ref.name())
                .patch(PatchContext.of(PatchType.JSON_MERGE), patchJson);
        return "Restarted " + ref.kind() + " " + ref.format();
    }

    private String scaleWorkload(ResourceRef ref, Map<String, Object> payload) {
        int replicas = Integer.parseInt(String.valueOf(payload.get("targetReplicas")));
        return switch (ref.kind()) {
            case "Deployment" -> {
                kubernetesClient.apps()
                        .deployments()
                        .inNamespace(ref.namespace())
                        .withName(ref.name())
                        .scale(replicas, true);
                yield "Scaled Deployment " + ref.format() + " to " + replicas + " replicas";
            }
            case "StatefulSet" -> {
                kubernetesClient.apps()
                        .statefulSets()
                        .inNamespace(ref.namespace())
                        .withName(ref.name())
                        .scale(replicas, true);
                yield "Scaled StatefulSet " + ref.format() + " to " + replicas + " replicas";
            }
            default ->
                    throw new IllegalArgumentException(
                            "Scale is not supported for kind: " + ref.kind());
        };
    }

    private String deletePod(ResourceRef ref) {
        List<?> deleted =
                kubernetesClient.pods().inNamespace(ref.namespace()).withName(ref.name()).delete();
        if (deleted == null || deleted.isEmpty()) {
            throw new IllegalStateException(
                    "Pod was not deleted, it may not exist: " + ref.format());
        }
        return "Deleted Pod " + ref.format();
    }

    @SuppressWarnings("unchecked")
    private String patchResource(ResourceRef ref, Map<String, Object> payload) {
        Object patch = payload.get("patch");
        if (!(patch instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Patch payload is missing or invalid");
        }
        String patchJson = writeJson((Map<String, Object>) patch);
        PatchContext patchContext = PatchContext.of(PatchType.JSON_MERGE);
        if (ref.namespaced()) {
            kubernetesClient.genericKubernetesResources(ref.apiVersion(), ref.kind())
                    .inNamespace(ref.namespace())
                    .withName(ref.name())
                    .patch(patchContext, patchJson);
        } else {
            kubernetesClient.genericKubernetesResources(ref.apiVersion(), ref.kind())
                    .withName(ref.name())
                    .patch(patchContext, patchJson);
        }
        return "Patched " + ref.format() + " with validated JSON merge patch";
    }

    private Map<String, Object> buildResponse(
            MutationPlan plan, OperationExecutionRecord execution, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approval_id", plan.approvalId());
        response.put("execution_id", execution.executionId());
        response.put("resource_ref", plan.resourceRef().format());
        response.put("action", plan.actionType().toolAction());
        response.put("status", status);
        response.put(
                "summary",
                execution.resultSummary() == null
                        ? execution.errorMessage()
                        : execution.resultSummary());
        response.put("rollback_hint", plan.rollbackHint());
        response.put("success", execution.success());
        response.put("error_message", execution.errorMessage());
        return response;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize patch payload", exception);
        }
    }
}
