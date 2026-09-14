# Testing (Claude Code)

Rules Claude Code follows when writing and running tests in GETI-Server. Follow [`docs/ai/testing-policy.md`](../../docs/ai/testing-policy.md) for the policy's background and detailed principles; this document covers the actions Claude Code should actually take.

## Test Exploration

Check the following before implementing.

- Related existing Tests (`src/test/kotlin/...`)
- Test Naming and Test Package structure
- Test Frameworks in use (JUnit 5, Spring Boot Test)
- How Mocks are used
- Whether Spring Context Tests are used
- Existing Fixtures or Helpers

## Writing Tests

- Write or update tests that verify the changed behavior.
- Review the happy path and meaningful exception paths.
- Prefer verifying externally observable behavior over implementation details.
- Do not create meaningless Getter/Setter Tests.
- Do not overuse simple `not null`-level Tests that do not verify actual behavior.
- Do not distort Production Code behavior to make tests pass.
- Prefer the Naming and language (Korean/English) conventions of existing Tests.

## No Test Bypassing

The following are forbidden.

```text
Deleting existing Tests
Adding @Disabled
Removing failing Assertions
Unconditionally catching exceptions
Bypassing only the Test environment with conditionals
Depending on Test order
Excluding failing Tests from execution
```

## Running Tests

Run the Tests matching the change scope first.

```bash
./gradlew test
```

If Kotlin code changed, also check formatting and static analysis.

```bash
./gradlew spotlessApply   # auto-apply if formatting is off
./gradlew spotlessCheck
./gradlew detekt
```

Run full verification last. `check` (included in `clean test build`) automatically runs `spotlessCheck`, `detekt`, and `koverVerify`, so there is no need to run them again separately (`koverVerify` currently always passes because no minimum threshold is configured).

```bash
./gradlew clean test build
```

On Windows, use `.\gradlew.bat`. Follow [`docs/development/code-quality.md`](../../docs/development/code-quality.md) and [`docs/development/testing.md`](../../docs/development/testing.md) for tool-specific configuration.

If you need a coverage Report, run it separately (it is not included in `check`).

```bash
./gradlew koverHtmlReport
./gradlew koverXmlReport
```

## Failure Classification

When a test or Build fails, classify and report the cause as follows.

```text
Compilation failure
Test failure
Spring Context failure
Configuration failure
Dependency failure
External service failure
Local environment failure
Permission failure
```

Do not fix environment problems as if they were code problems. Do not blame code problems on the environment.

## Result Report

Record the following.

- Commands run
- Passed Tests
- Failed Tests
- Build result
- Verification that could not be run
- Cause of failure
- Remaining risks

## Persistence Integration Test (Testcontainers)

Write PostgreSQL/Redis Persistence Integration Tests that require Docker (Testcontainers) in `src/integrationTest` (`./gradlew integrationTest`), a separate Gradle Source Set/Task, not in `src/test`. `test`/`check`/`build` must pass without Docker, so this Task is not included in them. Do not add Tests that assume a real PostgreSQL/Redis connection to `src/test` (Unit/Slice Tests). Follow [`docs/development/persistence.md`](../../docs/development/persistence.md) for the detailed structure and examples.

## Web Slice Test (`@WebMvcTest`)

Verify the Web layer, such as Controllers and global exception handling (`GlobalExceptionHandler`), with `@WebMvcTest` + `MockMvc`. Do not use `@SpringBootTest` unless the full Context is needed. Keep Test-only Controllers only in `src/test/kotlin`, and do not add example Controllers to Production Source. When implementing a new API, write error Contract Tests that together verify the success response, error responses (Field names, HTTP Status, Error Code), and non-exposure of internal information. Follow [`docs/development/web-api.md`](../../docs/development/web-api.md) for details.

## Tools Not Yet Introduced

The following test tools have not yet been introduced in this repository. Unless the work is related, do not require them or introduce them arbitrarily.

```text
ArchUnit
Mutation Testing
Contract Test
```
