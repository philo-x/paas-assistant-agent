package io.agentscope.examples.paasassistant.platform.model;

/**
 * Canonical Kubernetes resource reference.
 */
public record ResourceRef(
        String apiVersion,
        String kind,
        String namespace,
        String name,
        boolean namespaced) {

    public String format() {
        if (namespaced && namespace != null && !namespace.isBlank()) {
            return kind + "/" + namespace + "/" + name;
        }
        return kind + "/" + name;
    }
}
