package io.agentscope.examples.paasassistant.platform.model;

import java.time.Instant;
import java.util.Map;

/**
 * Approval-backed change plan returned by change-plan tools.
 */
public record MutationPlan(
        String approvalId,
        String chatId,
        String userId,
        MutationActionType actionType,
        ResourceRef resourceRef,
        Map<String, Object> planPayload,
        String riskLevel,
        ApprovalStatus status,
        Instant expiresAt,
        Instant approvedAt,
        String summary,
        String rollbackHint) {}
