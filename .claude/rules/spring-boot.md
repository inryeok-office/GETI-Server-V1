# Spring Boot / Kotlin Working Principles (Claude Code)

Claude Code-specific working rules based on GETI-Server's actual environment (Spring Boot 4.1.0, Kotlin 2.3.21, Gradle 9.5.1 Kotlin DSL, Java Toolchain 25, Root Package `team.inreok.getiserver`). Do not enforce Architecture that has not yet been introduced as if it were a finalized rule (see [`docs/ai/coding-conventions.md`](../../docs/ai/coding-conventions.md)).

## Versions and Build

- Do not change the current Java, Spring Boot, or Kotlin versions without justification.
- Keep the current Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).
- Check existing Plugins and Dependencies before working.
- When adding a new Dependency, review its necessity and impact (Runtime, whether Test Scope suffices, maintenance status).

## Existing Structure First

- Check the existing Root Package (`team.inreok.getiserver`) and its sub-Package structure.
- Check existing Naming and Component/Configuration structure.
- Check the existing Test location (`src/test/kotlin/...`).
- Do not arbitrarily move `GetiServerApplication` (the Main Application Class).

## Spring Configuration Principles

- Do not arbitrarily change the Component Scan scope.
- Do not Hard Code configuration values in Source Code.
- Separate values that differ by environment into Profiles (`local`/`test`/`prod`) or environment variables, not common configuration (`application.yaml`). Follow [`docs/development/configuration.md`](../../docs/development/configuration.md) for the Profile strategy, environment variable Naming, and Secret management criteria.
- Reference Secrets only through environment variables, and do not provide unsafe defaults (`${SECRET:change-me}`, etc.).
- Do not hide Bean conflicts with temporary renaming alone.
- Do not unconditionally work around Circular Dependencies with `@Lazy`.
- Do not hide Spring Context failures by disabling Tests.
- Before adding a new Starter, check whether existing Dependencies can do the job.
- Use only `validate` or `none` for `spring.jpa.hibernate.ddl-auto` (`create`/`create-drop`/`update` are forbidden). Manage the Schema only through Flyway Migrations, and do not modify already-merged Migration files; add a new version instead.
- Keep `spring.jpa.open-in-view=false` and do not start Transactions in Controllers.
- Do not perform slow or failure-prone I/O, such as external API calls or file uploads/downloads, inside a DB Transaction (`@Transactional`). Keep Transactions minimally scoped in the Application/Service layer (finalized in the GETI Notion BE convention "17. Transaction Convention").
- Do not arbitrarily revert `spring.flyway.clean-disabled=true`.
- For Redis, use the `StringRedisTemplate` that Spring Boot provides by default first. Do not create general-purpose Beans such as a Java serialization-based `RedisTemplate<String, Any>` in advance, before actual object serialization is needed.
- Write PostgreSQL/Redis Persistence Integration Tests that require Docker (Testcontainers) in `src/integrationTest` (`./gradlew integrationTest`), not `src/test`. Follow [`docs/development/persistence.md`](../../docs/development/persistence.md) for details.
- Place new Controllers and request/response DTOs inside the relevant Domain Package (`team.inreok.getiserver.domain.{domain-name}`), not the common Packages (`team.inreok.getiserver.global.web`, `team.inreok.getiserver.global.error`). Do not put business logic, direct Repository calls, or Transaction starts in Controllers.
- Use `ApiResponse`/`PageResponse` (`team.inreok.getiserver.global.web`) and `ErrorResponse` (`team.inreok.getiserver.global.error`) for API responses. Do not return JPA Entities directly as responses, use `Map<String, Any>` as a response, or return `Page<T>` as-is.
- Add new Error Codes only for errors actually handled. Define Domain Error Codes inside the relevant Domain Module. Define Domain exceptions by extending `global.error.BusinessException`, and do not create specific Domain exceptions in advance inside the `global` Package.
- When adding a new Controller Endpoint or changing an existing one, apply the Swagger Annotation rules in [`docs/ai/openapi-documentation.md`](../../docs/ai/openapi-documentation.md) in the same work and make `OpenApiDocumentationTest` pass.
- Do not expose Exception Messages or Stack Traces as-is in error responses (the exception is `BusinessException` Messages, which are safe text written directly by our code). Follow [`docs/development/web-api.md`](../../docs/development/web-api.md) for details.

## Module Boundaries (Spring Modulith)

A Spring Modulith foundation is in place (`spring-modulith-starter-test`, `ModularityTest`, `PackageArchitectureTest`). Use only two kinds of top-level Production Packages, `domain` and `global`, and create new domain Packages as independent Modules directly under `domain` (`domain.{domain-name}`). Inside a Domain, create only the Packages actually needed among `entity` (+`entity/type`), `repository`, `service`, `controller`, `dto`, and `exception`. Do not directly reference another Domain's internal implementation (Packages/types explicitly exposed as Named Interfaces are the exception, e.g., `domain.operation.entity.type`, `domain.company.query`, `domain.job.query`). If you change the Package structure, run structure verification with `./gradlew test --tests "*ModularityTest"` and `./gradlew test --tests "*PackageArchitectureTest"`. Follow [`docs/architecture/modularity.md`](../../docs/architecture/modularity.md) for detailed principles.

## Architecture Restrictions

The internal Domain Package structure (`entity`/`repository`/`service`/`controller`/`dto`/`exception`) and the top-level `domain`/`global` separation have been finalized by the user (see [`docs/architecture/modularity.md`](../../docs/architecture/modularity.md)). The items below are still not finalized in this repository. Do not enforce them as global rules or implement them arbitrarily without a related Issue.

```text
Fixed Controller-Service-Repository structure
Hexagonal Architecture
Clean Architecture
Common JPA Entity Base Class (global.persistence)
QueryDSL structure
Actual introduction of OpenAPI (springdoc)
Security Filter Chain structure
OAuth and JWT structure
```

Reflect the items above once they are finalized in a future dedicated PR or Issue and [`docs/ai/coding-conventions.md`](../../docs/ai/coding-conventions.md) is updated.

## Code Generation Restrictions

- Do not create unused Classes.
- Do not create empty Packages.
- Do not create Placeholders anticipating future features that are not yet needed.
- Do not leave temporary implementations that only make compilation pass.
- Do not create meaningless Interface separations.
- Do not over-abstract simple implementations used only once.

## Adding Dependencies

Check the following when adding a new Dependency.

- Whether existing Dependencies can do the job
- Whether an official Spring Boot Starter exists
- Compatibility with the current Spring Boot/Kotlin versions
- Security and maintenance status
- Impact on Runtime
- Whether Test Scope (`testImplementation`, `testRuntimeOnly`) suffices
- Whether it is actually needed for the Issue scope

In this AI harness setup phase, including this document, do not add actual business Dependencies.
