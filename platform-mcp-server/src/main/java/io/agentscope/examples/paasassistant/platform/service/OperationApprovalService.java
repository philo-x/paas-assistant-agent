package io.agentscope.examples.paasassistant.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.examples.paasassistant.platform.model.ApprovalStatus;
import io.agentscope.examples.paasassistant.platform.model.MutationActionType;
import io.agentscope.examples.paasassistant.platform.model.MutationPlan;
import io.agentscope.examples.paasassistant.platform.model.OperationExecutionRecord;
import io.agentscope.examples.paasassistant.platform.model.ResourceRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Persists approvals and execution audit records in MySQL.
 */
@Service
public class OperationApprovalService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final long approvalTtlSeconds;

    public OperationApprovalService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${platform.execution.approval-ttl-seconds:600}") long approvalTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.approvalTtlSeconds = approvalTtlSeconds;
    }

    public MutationPlan createPlan(
            String chatId,
            String userId,
            MutationActionType actionType,
            ResourceRef resourceRef,
            Map<String, Object> planPayload,
            String riskLevel,
            String summary,
            String rollbackHint) {
        Instant expiresAt = Instant.now().plusSeconds(approvalTtlSeconds);
        String approvalId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>(planPayload);
        Map<String, Object> resourceRefPayload = new LinkedHashMap<>();
        resourceRefPayload.put("apiVersion", resourceRef.apiVersion());
        resourceRefPayload.put("kind", resourceRef.kind());
        resourceRefPayload.put("namespace", resourceRef.namespace());
        resourceRefPayload.put("name", resourceRef.name());
        resourceRefPayload.put("namespaced", resourceRef.namespaced());
        payload.put("resourceRef", resourceRefPayload);
        payload.put("summary", summary);
        payload.put("rollbackHint", rollbackHint);

        jdbcTemplate.update(
                """
                INSERT INTO operation_approval (
                    approval_id, chat_id, user_id, action_type, target_kind, target_namespace,
                    target_name, plan_payload, risk_level, status, expires_at, approved_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                approvalId,
                chatId,
                userId,
                actionType.name(),
                resourceRef.kind(),
                resourceRef.namespace(),
                resourceRef.name(),
                toJson(payload),
                riskLevel,
                ApprovalStatus.PENDING.name(),
                Timestamp.from(expiresAt),
                null);

        return new MutationPlan(
                approvalId,
                chatId,
                userId,
                actionType,
                resourceRef,
                payload,
                riskLevel,
                ApprovalStatus.PENDING,
                expiresAt,
                null,
                summary,
                rollbackHint);
    }

    public MutationPlan prepareForExecution(
            String approvalId, String executorUserId, boolean confirmedInCurrentTurn) {
        MutationPlan plan = requireApproval(approvalId);
        if (isExpired(plan)) {
            updateApprovalStatus(approvalId, ApprovalStatus.EXPIRED, null);
            throw new IllegalStateException("Approval has expired: " + approvalId);
        }
        if (plan.status() == ApprovalStatus.PENDING) {
            if (!confirmedInCurrentTurn) {
                throw new IllegalStateException(
                        "Approval is still pending explicit confirmation: " + approvalId);
            }
            approve(approvalId);
            plan = requireApproval(approvalId);
        }
        if (plan.status() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException(
                    "Approval is not executable in status "
                            + plan.status().name()
                            + ": "
                            + approvalId);
        }
        return plan;
    }

    public void approve(String approvalId) {
        updateApprovalStatus(approvalId, ApprovalStatus.APPROVED, Instant.now());
    }

    public void markApprovalExecuted(String approvalId) {
        updateApprovalStatus(approvalId, ApprovalStatus.EXECUTED, Instant.now());
    }

    public void markApprovalFailed(String approvalId) {
        updateApprovalStatus(approvalId, ApprovalStatus.FAILED, null);
    }

    public String createExecution(
            String approvalId, String executorUserId, Map<String, Object> requestPayload) {
        String executionId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO operation_execution (
                    execution_id, approval_id, executor_user_id, request_payload, started_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
                executionId,
                approvalId,
                executorUserId,
                toJson(requestPayload),
                Timestamp.from(Instant.now()));
        return executionId;
    }

    public OperationExecutionRecord finishExecution(
            String executionId, boolean success, String resultSummary, String errorMessage) {
        jdbcTemplate.update(
                """
                UPDATE operation_execution
                SET result_summary = ?, success = ?, finished_at = ?, error_message = ?
                WHERE execution_id = ?
                """,
                resultSummary,
                success,
                Timestamp.from(Instant.now()),
                errorMessage,
                executionId);
        return requireExecution(executionId);
    }

    public Map<String, Object> getStatus(String approvalId, String executionId) {
        OperationExecutionRecord execution =
                StringUtils.hasText(executionId)
                        ? requireExecution(executionId)
                        : (StringUtils.hasText(approvalId)
                                ? findLatestExecution(approvalId)
                                : null);

        String resolvedApprovalId =
                StringUtils.hasText(approvalId)
                        ? approvalId
                        : (execution != null ? execution.approvalId() : null);
        if (!StringUtils.hasText(resolvedApprovalId)) {
            throw new IllegalArgumentException("approvalId or executionId is required");
        }

        MutationPlan plan = requireApproval(resolvedApprovalId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approval_id", plan.approvalId());
        response.put("execution_id", execution == null ? null : execution.executionId());
        response.put("resource_ref", plan.resourceRef().format());
        response.put("action", plan.actionType().toolAction());
        response.put("status", executionStatus(plan, execution));
        response.put("summary", plan.summary());
        response.put("rollback_hint", plan.rollbackHint());
        response.put("risk_level", plan.riskLevel());
        response.put("expires_at", plan.expiresAt().toString());
        response.put(
                "approved_at",
                plan.approvedAt() == null ? null : plan.approvedAt().toString());
        if (execution != null) {
            response.put("result_summary", execution.resultSummary());
            response.put("error_message", execution.errorMessage());
            response.put("started_at", execution.startedAt().toString());
            response.put(
                    "finished_at",
                    execution.finishedAt() == null
                            ? null
                            : execution.finishedAt().toString());
            response.put("success", execution.success());
        }
        return response;
    }

    public MutationPlan requireApproval(String approvalId) {
        List<MutationPlan> results =
                jdbcTemplate.query(
                        "SELECT * FROM operation_approval WHERE approval_id = ?",
                        approvalMapper(),
                        approvalId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Approval does not exist: " + approvalId);
        }
        return results.get(0);
    }

    private OperationExecutionRecord requireExecution(String executionId) {
        List<OperationExecutionRecord> results =
                jdbcTemplate.query(
                        "SELECT * FROM operation_execution WHERE execution_id = ?",
                        executionMapper(),
                        executionId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Execution does not exist: " + executionId);
        }
        return results.get(0);
    }

    private OperationExecutionRecord findLatestExecution(String approvalId) {
        List<OperationExecutionRecord> results =
                jdbcTemplate.query(
                        """
                        SELECT * FROM operation_execution
                        WHERE approval_id = ?
                        ORDER BY started_at DESC
                        LIMIT 1
                        """,
                        executionMapper(),
                        approvalId);
        return results.isEmpty() ? null : results.get(0);
    }

    private void updateApprovalStatus(
            String approvalId, ApprovalStatus status, Instant approvedAt) {
        jdbcTemplate.update(
                """
                UPDATE operation_approval
                SET status = ?, approved_at = COALESCE(?, approved_at)
                WHERE approval_id = ?
                """,
                status.name(),
                approvedAt == null ? null : Timestamp.from(approvedAt),
                approvalId);
    }

    private boolean isExpired(MutationPlan plan) {
        return Instant.now().isAfter(plan.expiresAt());
    }

    private String executionStatus(
            MutationPlan plan, OperationExecutionRecord execution) {
        if (execution == null) {
            return plan.status().name();
        }
        if (execution.finishedAt() == null) {
            return "RUNNING";
        }
        return Boolean.TRUE.equals(execution.success()) ? "SUCCEEDED" : "FAILED";
    }

    private RowMapper<MutationPlan> approvalMapper() {
        return (resultSet, rowNum) -> mapApproval(resultSet);
    }

    private RowMapper<OperationExecutionRecord> executionMapper() {
        return (resultSet, rowNum) -> mapExecution(resultSet);
    }

    private MutationPlan mapApproval(ResultSet resultSet) throws SQLException {
        Map<String, Object> payload = fromJson(resultSet.getString("plan_payload"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceRefMap =
                (Map<String, Object>) payload.get("resourceRef");
        ResourceRef resourceRef =
                new ResourceRef(
                        String.valueOf(resourceRefMap.get("apiVersion")),
                        String.valueOf(resourceRefMap.get("kind")),
                        resourceRefMap.get("namespace") == null
                                ? null
                                : String.valueOf(resourceRefMap.get("namespace")),
                        String.valueOf(resourceRefMap.get("name")),
                        Boolean.parseBoolean(String.valueOf(resourceRefMap.get("namespaced"))));
        return new MutationPlan(
                resultSet.getString("approval_id"),
                resultSet.getString("chat_id"),
                resultSet.getString("user_id"),
                MutationActionType.valueOf(resultSet.getString("action_type")),
                resourceRef,
                payload,
                resultSet.getString("risk_level"),
                ApprovalStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("approved_at") == null
                        ? null
                        : resultSet.getTimestamp("approved_at").toInstant(),
                String.valueOf(payload.get("summary")),
                String.valueOf(payload.get("rollbackHint")));
    }

    private OperationExecutionRecord mapExecution(ResultSet resultSet)
            throws SQLException {
        return new OperationExecutionRecord(
                resultSet.getString("execution_id"),
                resultSet.getString("approval_id"),
                resultSet.getString("executor_user_id"),
                fromJson(resultSet.getString("request_payload")),
                resultSet.getString("result_summary"),
                resultSet.getObject("success") == null
                        ? null
                        : resultSet.getBoolean("success"),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getTimestamp("finished_at") == null
                        ? null
                        : resultSet.getTimestamp("finished_at").toInstant(),
                resultSet.getString("error_message"));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payload", exception);
        }
    }

    private Map<String, Object> fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize payload", exception);
        }
    }
}
