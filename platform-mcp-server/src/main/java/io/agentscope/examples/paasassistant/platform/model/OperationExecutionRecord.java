package io.agentscope.examples.paasassistant.platform.model;

import java.time.Instant;
import java.util.Map;

/**
 * Persisted execution result.
 */
public record OperationExecutionRecord(
        String executionId,
        String approvalId,
        String executorUserId,
        Map<String, Object> requestPayload,
        String resultSummary,
        Boolean success,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage) {}
