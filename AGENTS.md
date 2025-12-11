# Repository Guidelines

## Getting Started
- At the start of each chat session with the assistant, read `.claude/` and `docs/` to understand the architecture, features, and conventions before making changes.

## Project Structure & Modules
- Backend services live under `app/backend/` (course-service, schedule-service, user-service, api-gateway). Tests: `app/backend/*/src/test/java`.
- Lambda code is in `app/serverless/canvas-sync-lambda/`.
- System tests (component/integration/scenarios) are in `system-tests/` (Python/pytest).
- Scripts and infra bootstrapping: `localstack-init/`, `docker-compose.*.yml`, `scripts/`.

## Build, Test, and Development Commands
- Java services: `cd app/backend/<service> && ./gradlew test` (unit tests). Use `./gradlew bootRun` for local run if needed.
- Lambda: `cd app/serverless/canvas-sync-lambda && pytest tests/`.
- System tests: `cd system-tests; poetry run pytest component|integration|scenarios|infra -v`.
- Acceptance stack: `docker-compose -f docker-compose.acceptance.yml up -d` (LocalStack, MySQL, services).

## Coding Style & Naming
- Java: follow standard Spring conventions; use 4-space indentation; package structure mirrors domain (assignment, course, sync, etc.).
- Python (tests/lambda): 4-space indentation, snake_case for functions, pytest-style asserts.
- Queue/resource names use `{source}-to-{destination}-{purpose}` (e.g., `lambda-to-courseservice-sync`).
- Config keys keep `kebab-case` or dotted Spring style (e.g., `aws.lambda.canvas-sync-function-name`).

## Testing Guidelines
- Unit: JUnit/Mockito for backend; pytest for Lambda.
- Integration/Scenario: pytest in `system-tests/` with LocalStack/MySQL stack running.
- Naming: test classes end with `Test`, pytest files start with `test_`.
- Treat missing required envs as failures; dueAt-null assignments and disabled enrollments are expected to skip schedule creation.

## Commit & PR Guidelines
- No enforced conventional commits; prefer concise, imperative subjects (e.g., “Add disabled enrollment scenario test”).
- PRs should describe scope, testing performed (`gradlew test`, `poetry run pytest ...`), and note any config changes (.env, docker-compose, LocalStack).

## Security & Configuration Tips
- Keep secrets out of VCS; populate `.env.local` from `.env.local.example`.
- Lambda/LocalStack require `COURSE_SERVICE_URL`, `USER_SERVICE_URL`, and SQS queue names (`SQS_*`) to be set; ensure LocalStack auth token is present when needed.
