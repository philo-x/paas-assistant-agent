# Copyright 2024-2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Final runtime image
FROM eclipse-temurin:17-jre

ARG MODULE
ARG PORT=8080

LABEL maintainer="AgentScope Team"
LABEL description="PaaS Assistant Agent - ${MODULE}"

# Create non-root user with home directory
RUN groupadd -r appgroup && useradd -m -r -g appgroup appuser
WORKDIR /app

# Copy the specific module's JAR from local target directory
# Make sure you have run 'mvn clean package' locally before building the image
COPY ${MODULE}/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE ${PORT}

# Common Environment Variables Defaults
ENV SERVER_PORT=${PORT} \
    JAVA_OPTS="-Xms512m -Xmx1024m -Dnacos.logging.path=/app/logs -DJM.LOG.PATH=/app/logs"

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
