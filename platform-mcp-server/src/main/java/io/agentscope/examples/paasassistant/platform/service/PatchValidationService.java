package io.agentscope.examples.paasassistant.platform.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Validates patch payloads against a small V1 whitelist.
 */
@Service
public class PatchValidationService {

    public List<String> validatePatch(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new IllegalArgumentException("Patch payload must not be empty");
        }
        List<String> collectedPaths = new ArrayList<>();
        collectPaths("", patch, collectedPaths);
        for (String path : collectedPaths) {
            if (!isAllowed(path)) {
                throw new IllegalArgumentException(
                        "Patch path is not allowed in V1: " + path);
            }
        }
        return collectedPaths;
    }

    private void collectPaths(String prefix, Object node, List<String> collectedPaths) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String next =
                        prefix.isEmpty()
                                ? String.valueOf(entry.getKey())
                                : prefix + "." + entry.getKey();
                collectPaths(next, entry.getValue(), collectedPaths);
            }
            return;
        }
        if (node instanceof List<?>) {
            throw new IllegalArgumentException("Patch arrays are not supported in V1");
        }
        collectedPaths.add(prefix);
    }

    private boolean isAllowed(String path) {
        return path.equals("spec.replicas")
                || path.startsWith("metadata.annotations")
                || path.startsWith("metadata.labels")
                || path.startsWith("spec.template.metadata.annotations")
                || path.startsWith("spec.template.metadata.labels");
    }
}
