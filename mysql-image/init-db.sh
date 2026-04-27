#!/bin/bash
# =============================================
# MySQL initialization script
# Uses envsubst for environment variable substitution
# =============================================

set -e

# Set default values (if environment variables are not set)
export DB_NAME="${DB_NAME:-paas_agent_assistant}"
export DB_USERNAME="${DB_USERNAME:-paas_agent_assistant}"
export DB_PASSWORD="${DB_PASSWORD:-paas_agent_assistant@321}"

echo "Initializing database with:"
echo "  DB_NAME: ${DB_NAME}"
echo "  DB_USERNAME: ${DB_USERNAME}"
echo "  DB_PASSWORD: ********"

# Use envsubst to process template and execute SQL
envsubst '${DB_NAME} ${DB_USERNAME} ${DB_PASSWORD}' < /docker-entrypoint-initdb.d/init.sql.template > /tmp/init.sql

# Execute generated SQL file
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" < /tmp/init.sql

# Clean up temporary file
rm -f /tmp/init.sql

echo "Database initialization completed successfully!"

