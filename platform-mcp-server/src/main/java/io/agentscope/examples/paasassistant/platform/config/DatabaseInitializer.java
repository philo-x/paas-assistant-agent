package io.agentscope.examples.paasassistant.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initializes the MySQL schema used by the platform assistant.
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;
    private final String datasourceUsername;

    public DatabaseInitializer(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
    }

    @Override
    public void run(ApplicationArguments args) {
        verifyDatabaseConnection();
        dropLegacyDomainTables();
        createApprovalTable();
        createExecutionTable();
    }

    private void verifyDatabaseConnection() {
        Integer health = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (health == null || health != 1) {
            throw new IllegalStateException("Database health check returned unexpected result");
        }
        logger.info(
                "Connected to MySQL successfully. url={}, username={}",
                datasourceUrl,
                datasourceUsername);
    }

    private void dropLegacyDomainTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS feedback");
        jdbcTemplate.execute("DROP TABLE IF EXISTS orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS products");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        logger.info("Dropped legacy demo business tables if they were present.");
    }

    private void createApprovalTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS operation_approval (
                    approval_id VARCHAR(64) PRIMARY KEY,
                    chat_id VARCHAR(128) NOT NULL,
                    user_id VARCHAR(128) NOT NULL,
                    action_type VARCHAR(64) NOT NULL,
                    target_kind VARCHAR(64) NOT NULL,
                    target_namespace VARCHAR(255) NULL,
                    target_name VARCHAR(255) NOT NULL,
                    plan_payload LONGTEXT NOT NULL,
                    risk_level VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    expires_at DATETIME NOT NULL,
                    approved_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_operation_approval_chat_id (chat_id),
                    INDEX idx_operation_approval_status (status),
                    INDEX idx_operation_approval_target (target_kind, target_namespace, target_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        logger.info("Ensured table operation_approval exists.");
    }

    private void createExecutionTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS operation_execution (
                    execution_id VARCHAR(64) PRIMARY KEY,
                    approval_id VARCHAR(64) NOT NULL,
                    executor_user_id VARCHAR(128) NOT NULL,
                    request_payload LONGTEXT NOT NULL,
                    result_summary LONGTEXT NULL,
                    success TINYINT(1) NULL,
                    started_at DATETIME NOT NULL,
                    finished_at DATETIME NULL,
                    error_message LONGTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_operation_execution_approval_id (approval_id),
                    CONSTRAINT fk_operation_execution_approval
                        FOREIGN KEY (approval_id) REFERENCES operation_approval(approval_id)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        logger.info("Ensured table operation_execution exists.");
    }
}
