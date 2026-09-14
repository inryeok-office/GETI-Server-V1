---
name: test-and-verify
description: Covers how to select tests and verification appropriate to the change type (docs, configuration, logic, API, DB) and how to judge the results accurately.
---

# Test and Verify

Detailed criteria to consult when verifying changes in GETI-Server. The [`implement`](../../commands/implement.md), [`fix-bug`](../../commands/fix-bug.md), [`verify`](../../commands/verify.md), and [`prepare-pr`](../../commands/prepare-pr.md) Commands reference this Skill.

## Verification by Change Type

### Documentation Changes

- Markdown syntax is valid and Code Fences are closed
- Relative link targets actually exist
- File name casing matches
- There is no impact on the Build (run `clean test build` even if only docs changed)

### Configuration Changes (`build.gradle.kts`, `application.yaml`, etc.)

- Parsing succeeds
- Whether Profiles are affected (see [`docs/development/configuration.md`](../../../docs/development/configuration.md) for the `local`/`test`/`prod` Profile strategy)
- Related Beans are created correctly
- The Spring Context loads correctly
- Whether the Build succeeds

### Business Logic Changes

- Happy path
- Boundary values
- Exception paths
- Authorization branches, if authorization is involved
- Whether data integrity is affected

This repository has no business logic yet, so apply these criteria once a related Issue arises.

### API Changes

- Request handling
- Response format (`ApiResponse`/`PageResponse`, see [`docs/development/web-api.md`](../../../docs/development/web-api.md))
- Validation and Field Error format
- Status Code and Error Code
- Error responses do not expose internal information (Exception Message, Stack Trace, etc.)
- Compatibility with existing callers
- Whether `@WebMvcTest`-based Web Slice Tests and error Contract Tests are written

### DB Changes

- Migration (Flyway. Do not modify merged Migration files; add a new version instead)
- Schema (use only `ddl-auto=validate`/`none`; `create`/`create-drop`/`update` are forbidden)
- Constraint
- Rollback feasibility
- Database used in Tests: `src/test` (Unit/Slice) runs without external infrastructure using `testRuntimeOnly("com.h2database:h2")`, and real PostgreSQL/Redis connection verification is done with Testcontainers in `src/integrationTest` (`./gradlew integrationTest`, requires Docker)

Follow [`docs/development/persistence.md`](../../../docs/development/persistence.md) for detailed criteria. This repository has no actual GETI Domain Migrations/Entities yet, so apply these criteria once a related Issue arises.

## Test Selection

- Start with the smallest relevant Test (`./gradlew test`).
- If the impact is broad, run the full Test suite.
- If Kotlin code changed, also check `./gradlew spotlessCheck` (formatting) and `./gradlew detekt` (static analysis). Fix formatting violations with `./gradlew spotlessApply`.
- For Persistence (JPA/Flyway/Redis) changes, also run `./gradlew integrationTest` in an environment with Docker. If Docker is unavailable, state explicitly that it could not be run (`test`/`check`/`build` must pass without Docker).
- For Web changes (Controller, global exception handling, CORS, etc.), run the `@WebMvcTest`-based Tests, and if you moved Packages or touched a new Module (e.g., `web`), also run `./gradlew test --tests "*ModularityTest"`.
- Always finish with `./gradlew clean test build`. `check` already includes `spotlessCheck`, `detekt`, and `koverVerify`, so there is no need to run them again separately.
- If you need coverage numbers, run `./gradlew koverHtmlReport` or `./gradlew koverXmlReport` separately (they are not included in `check`).
- Do not needlessly re-run scopes that have already been confirmed to pass.
- Do not hide verification you could not run; state it explicitly.

## Failure Analysis

- Identify the first actual cause (Root Cause) in the log. Distinguish the root failure from cascading follow-on failures.
- Distinguish environment problems (missing tools, permissions, etc.) from code problems.
- Do not jump to conclusions from only part of a Stack Trace.

Failure type classification:

```text
Compilation failure
Test failure
Spring Context failure
Configuration failure
Dependency failure
Formatting violation (spotlessCheck)
Static analysis violation (detekt)
Documentation or path failure
External service failure
Local environment failure
Permission failure
```

## Completion Criteria

Describe work as "complete" only when all of the following are satisfied (see [`docs/ai/completion-policy.md`](../../../docs/ai/completion-policy.md)).

- Requirements are met
- Related Tests pass
- The full Build succeeds
- The Diff has been reviewed directly
- No Secrets are included
- If GitHub Actions CI ([`docs/development/ci.md`](../../../docs/development/ci.md)) is configured and a PR was created, confirm the actual Workflow Run succeeded with `gh pr checks`, not just local verification
- Verification that could not be run is stated explicitly
