package io.agentscope.examples.paasassistant.platform.model;

/**
 * Supported mutation types in V1.
 */
public enum MutationActionType {
    RESTART_WORKLOAD("restart_workload"),
    SCALE_WORKLOAD("scale_workload"),
    DELETE_POD("delete_pod"),
    PATCH_RESOURCE("patch_resource");

    private final String toolAction;

    MutationActionType(String toolAction) {
        this.toolAction = toolAction;
    }

    public String toolAction() {
        return toolAction;
    }
}
