package io.agentscope.examples.paasassistant.change.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initializes the MySQL schema used by the change assistant.
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
}
