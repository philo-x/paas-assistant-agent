package io.agentscope.examples.paasassistant.platform.service;

import io.agentscope.examples.paasassistant.platform.model.ResourceRef;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Validates and normalizes Kubernetes resource references for mutation operations.
 */
@Service
public class ResourceValidationService {

    private static final Set<String> SUPPORTED_KINDS =
            Set.of(
                    "Pod",
                    "Deployment",
                    "StatefulSet",
                    "DaemonSet",
                    "Job",
                    "Service",
                    "Ingress",
                    "Node",
                    "Event",
                    "PVC",
                    "Namespace");

    private static final Set<String> NAMESPACED_KINDS =
            Set.of(
                    "Pod",
                    "Deployment",
                    "StatefulSet",
                    "DaemonSet",
                    "Job",
                    "Service",
                    "Ingress",
                    "PVC",
                    "Event");

    public ResourceRef buildResourceRef(String kind, String namespace, String name) {
        String normalizedKind = normalizeKind(kind);
        boolean namespaced = NAMESPACED_KINDS.contains(normalizedKind);
        if (namespaced && !StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException(normalizedKind + " requires a namespace");
        }
        return new ResourceRef(
                apiVersion(normalizedKind), normalizedKind, namespace, name, namespaced);
    }

    public void validateRestartTarget(String kind) {
        String normalizedKind = normalizeKind(kind);
        if (!Set.of("Deployment", "StatefulSet", "DaemonSet").contains(normalizedKind)) {
            throw new IllegalArgumentException(
                    "Restart only supports Deployment, StatefulSet and DaemonSet in V1");
        }
    }

    public void validateScaleTarget(String kind) {
        String normalizedKind = normalizeKind(kind);
        if (!Set.of("Deployment", "StatefulSet").contains(normalizedKind)) {
            throw new IllegalArgumentException(
                    "Scale only supports Deployment and StatefulSet in V1");
        }
    }

    public String normalizeKind(String kind) {
        if (!StringUtils.hasText(kind)) {
            throw new IllegalArgumentException("Resource kind is required");
        }
        String lowered = kind.trim().toLowerCase();
        String normalized =
                switch (lowered) {
                    case "pod", "pods" -> "Pod";
                    case "deployment", "deployments" -> "Deployment";
                    case "statefulset", "statefulsets" -> "StatefulSet";
                    case "daemonset", "daemonsets" -> "DaemonSet";
                    case "job", "jobs" -> "Job";
                    case "service", "services", "svc" -> "Service";
                    case "ingress", "ingresses" -> "Ingress";
                    case "node", "nodes" -> "Node";
                    case "event", "events" -> "Event";
                    case "pvc", "persistentvolumeclaim", "persistentvolumeclaims" -> "PVC";
                    case "namespace", "namespaces", "ns" -> "Namespace";
                    default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
                };
        if (!SUPPORTED_KINDS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported kind: " + kind);
        }
        return normalized;
    }

    private String apiVersion(String kind) {
        return switch (kind) {
            case "Deployment", "StatefulSet", "DaemonSet" -> "apps/v1";
            case "Job" -> "batch/v1";
            case "Ingress" -> "networking.k8s.io/v1";
            default -> "v1";
        };
    }
}
