package io.agentscope.examples.paasassistant.platform.model;

/**
 * Approval lifecycle for controlled operations.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    EXECUTED,
    EXPIRED,
    FAILED,
    REJECTED
}
