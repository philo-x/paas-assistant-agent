# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PaaS Agent Assistant — a multi-agent Kubernetes operations assistant built on **AgentScope Java** + **Spring Boot 4 / Spring 7 / Java 17**. The chat entry is `POST /api/assistant/chat/structured` (structured SSE, JSON body `{chat_id, user_id, user_query}`) served by `supervisor-agent` on port **10008**, which also serves the Vue 3 frontend as static resources.

## Module Layout

The root POM (`io.agentscope.examples:paas-assistant-agent`, version managed via `${revision}`) declares four Maven modules; each is an independently deployable Spring Boot service.

| Module | Port | Role |
|---|---|---|
| `supervisor-agent` | 10008 | SSE chat entry, A2A router to sub-agents, MySQL session persistence, serves frontend |
| `diagnosis-sub-agent` | 10006 | K8s diagnosis + two-step controlled change orchestration; consumes k8sgpt MCP + platform MCP |
| `guide-sub-agent` | 10005 | Read-only guide / RAG / YAML & describe explanations; never executes mutations |
| `platform-mcp-server` | 10002 | MCP server exposing `change-plan-*` / `change-execute` / `change-get-status`; Fabric8 K8s client; writes approval + execution audit to MySQL |
| `frontend/` | (Vite dev) | Vue 3 + TS + Pinia + Ant Design Vue; built into `supervisor-agent/src/main/resources/static/` |

There is **no root `build.sh`** despite what `IMAGE_BUILD_GUIDE.md` describes — only per-module `build.sh` scripts exist today.

## Build & Run

### Maven (whole tree)

```bash
mvn clean package -DskipTests        # build all four modules
mvn -pl supervisor-agent -am package # build one module + its dependencies
mvn -pl supervisor-agent test        # run one module's tests
mvn -pl supervisor-agent test -Dtest=A2aAgentToolsTest#methodName  # single test
```

Tests use JUnit 5 via `spring-boot-starter-test` (already on the classpath in each module's `pom.xml`).

### Per-module build + Docker

Each module ships a `build.sh` that runs `mvn clean package` then `docker build`:

```bash
cd supervisor-agent
./build.sh                                  # local image, tag :test
./build.sh -v 1.0.12 -p linux/amd64         # specific platform
./build.sh -v 1.0.12 -r registry.example.com/paas --push
./build.sh --skip-build                     # reuse existing target/*.jar
./build.sh -t                               # actually run tests (default skips)
```

The Dockerfile is **hybrid**: it expects `target/*.jar` to already exist locally (Maven runs on host, not in Docker), and only the frontend stage builds inside Docker. Run `mvn package` first, or use `build.sh` which does it for you.

### Frontend

```bash
cd frontend
npm ci
npm run dev          # vite dev server
npm run build        # type-check + vite build → dist/
npm run type-check   # vue-tsc
npm run test:unit    # vitest
```

After `npm run build`, copy `frontend/dist/` into `supervisor-agent/src/main/resources/static/` if you want the JAR to embed the new UI. The Dockerfile does this automatically via the frontend build stage.

## Architecture Notes

### Request flow

`User → supervisor-agent (POST /api/assistant/chat/structured, SSE)` → `SupervisorAgent` chooses one of two A2A agents wired in `tools/A2aAgentTools.java`:
- `diagnosis_agent` for diagnose / restart / scale / patch / delete-pod / "execute" intents
- `guide_agent` for YAML/describe/command-recommendation/RAG questions

Routing is enforced by the prompt in `application.yml → agent.prompts.supervisor-agent-instruction` (Chinese). Sub-agent registration/discovery uses **A2A over Nacos**. There is no direct HTTP call between the supervisor and sub-agents; everything goes through the AgentScope A2A client.

### Mandatory two-step mutation contract

Any cluster-mutating tool MUST follow:
1. `change-plan-{restart|scale|delete-pod|patch}` returns an `approval_id`.
2. The agent surfaces `approval_id`, target, impact, rollback hint to the user and waits for explicit confirmation.
3. `change-execute(approval_id, confirmed=true)` performs the action and returns `{approval_id, execution_id, resource_ref, action, status, summary, rollback_hint}`.

This contract is implemented in `platform-mcp-server` (`KubernetesMutationService`, `OperationApprovalService`, `PatchValidationService`) and is also encoded as a hard rule in `diagnosis-sub-agent` and `guide-sub-agent` system prompts. The `guide_agent` is forbidden from calling `change-*` tools at all. **Do not bypass the plan/execute split or expand `change-plan-patch`'s field whitelist without updating `PatchValidationService`.**

Approval TTL is configurable via `EXECUTION_APPROVAL_TTL_SECONDS` (default 600s).

### Persistence

`platform-mcp-server` uses `DatabaseInitializer` (an `ApplicationRunner`) — not Flyway/Liquibase — to drop legacy demo tables (`users`, `products`, `orders`, `feedback`) and create `operation_approval` and `operation_execution` on startup. There are **no `.sql` migration files**; schema changes go in `DatabaseInitializer.java`.

`supervisor-agent` uses AgentScope's `agentscope-extensions-session-mysql` for chat session persistence (separate concern from the audit tables above).

### Streaming / SSE

`SupervisorAgentController` exposes a single `POST /api/assistant/chat/structured` endpoint. The `stream/` package (`StructuredSseEmitter`, `StructuredTraceRegistry`, `ToolNarrationCatalog`, `ToolNarrator`, `StructuredStreamHook`) converts AgentScope `Event`s into rendered narration frames for the UI. `tool-narrations.yml` defines per-tool human-readable phrasing.

### Model providers

Each service reads `MODEL_PROVIDER=openai|dashscope` and uses either `MODEL_API_KEY`/`MODEL_BASE_URL`/`MODEL_NAME` or DashScope-specific keys. Defaults in YAML often differ between modules (supervisor → `openai`, sub-agents → `dashscope`); set env vars rather than relying on defaults.

### K8s access (platform-mcp-server)

`KubernetesClientConfig` switches on `K8S_AUTH_MODE`:
- `incluster` (default in container): uses ServiceAccount
- `kubeconfig`: reads `KUBECONFIG_PATH` and optionally `K8S_CONTEXT`

## Required Environment

Set these before running locally or in Compose/Helm:
- Model: `MODEL_PROVIDER`, `MODEL_API_KEY`, `MODEL_NAME`, `MODEL_BASE_URL`
- Nacos: `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_USERNAME`, `NACOS_PASSWORD`
- MySQL: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- k8sgpt MCP (diagnosis-sub-agent): `K8SGPT_MCP_URL`
- K8s (platform-mcp-server): `K8S_AUTH_MODE`, `KUBECONFIG_PATH`, `K8S_CONTEXT`
- RAG (guide-sub-agent): `DASHSCOPE_ACCESS_KEY_ID`, `DASHSCOPE_ACCESS_KEY_SECRET`, `DASHSCOPE_WORKSPACE_ID`, `DASHSCOPE_INDEX_ID`
- Memory (sub-agents): `MEM0_API_KEY`, `MEM0_BASE_URL`, `MEM0_API_TYPE`

## Conventions

- All four services use Spring WebFlux (`web-application-type: reactive`), not MVC. Use `Flux/Mono` and `ServerSentEvent`, not `SseEmitter`.
- Java package root is `io.agentscope.examples.paasassistant.{supervisor|diagnosis|guide|platform}`. A small set of A2A message-handling classes are intentionally placed under `io.agentscope.core.a2a.agent.message.*` (overriding/extending framework classes); preserve that package when editing them.
- System prompts live in `application.yml` under `agent.prompts.*` and are deliberately written in Chinese — keep responses to platform users in Chinese unless asked otherwise. The Markdown-table formatting rules at the bottom of each prompt are load-bearing for the frontend renderer; don't drop them when editing prompts.
- Logs go to `/app/logs` in containers (`-Dnacos.logging.path=/app/logs -DJM.LOG.PATH=/app/logs`), via `logback-spring.xml` per module.