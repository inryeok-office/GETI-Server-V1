---
name: spring-boot-change
description: Covers environment analysis, exploration of existing implementations, change decisions, and implementation principles for safely changing a Spring Boot/Kotlin project while preserving its existing structure and versions.
---

# Spring Boot Change

Detailed decision criteria to consult when changing GETI-Server's Spring Boot/Kotlin code. The [`implement` Command](../../commands/implement.md) and [`fix-bug` Command](../../commands/fix-bug.md) reference this Skill.

## Environment Analysis

Check the actual environment before making changes. Do not assume without checking.

- Java version (Toolchain in `build.gradle.kts`)
- Spring Boot version
- Gradle version and DSL (Kotlin DSL)
- Root Package (`team.inreok.getiserver`)
- Applied Plugins
- Declared Dependencies
- Profile setup (`local`/`test`/`prod`, see [`docs/development/configuration.md`](../../../docs/development/configuration.md))
- Test Framework (JUnit 5, Spring Boot Test)

## Exploring Existing Implementations

Search for the following before implementing.

- Similar Controllers
- Similar Services
- Similar Repositories
- Configuration Classes
- Exception handling approach
- Response format
- Test-writing patterns

This repository is still at an early stage, so many of the items above may not exist. Do not assume and fabricate structures that do not exist.

## Change Decisions

Decide on the implementation approach after checking the following.

- Whether a new Dependency is actually needed, or whether official Spring features alone suffice
- The impact of configuration changes on other parts
- Possibility of Bean conflicts
- Impact on Component Scan
- Impact on Profiles (separate environment-specific values into `local`/`test`/`prod` Profiles or environment variables rather than common configuration, see [`docs/development/configuration.md`](../../../docs/development/configuration.md))
- Compatibility of public APIs (Controller Signatures, etc.)
- Whether a Migration is needed (Flyway manages the Schema. Use only `validate`/`none` for `ddl-auto`, and do not modify merged Migration files; add a new version instead. See [`docs/development/persistence.md`](../../../docs/development/persistence.md))

## Implementation Principles

- Meet the requirements with minimal changes.
- Prefer existing Patterns.
- Do not create unnecessary abstractions.
- Do not Hard Code configuration values in Source Code.
- Do not leave core requirements as Placeholders or TODOs.
- Do not work around problems temporarily (e.g., overusing `@Lazy`, unconditionally catching Exceptions).
- Do not create meaningless Interface separations.
- Do not bundle unrelated Package moves into the change.
- Create a new domain Package as an independent Application Module directly under the Root Package, and do not reference other Modules' internal implementations directly (see [`docs/architecture/modularity.md`](../../../docs/architecture/modularity.md)).
- Place Entities/Repositories inside the relevant Domain Module, not in shared `entity`/`repository` Packages directly under the Root Package. Do not change `spring.jpa.hibernate.ddl-auto` to `create`/`create-drop`/`update`. Write Persistence Tests that require Docker (Testcontainers) in `src/integrationTest`, not `src/test` (see [`docs/development/persistence.md`](../../../docs/development/persistence.md)).
- Place Controllers and request/response DTOs inside the relevant Domain Module, not in shared `controller`/`dto` Packages directly under the Root Package. Use `ApiResponse`/`PageResponse` (`team.inreok.getiserver.global.web`) and `ErrorResponse` (`team.inreok.getiserver.global.error`) for responses, and do not return JPA Entities, `Map<String, Any>`, or `Page<T>` directly from APIs. Add new Error Codes only for errors actually handled. Define Domain exceptions inside the relevant Domain Module by extending `global.error.BusinessException` (see [`docs/development/web-api.md`](../../../docs/development/web-api.md)).
- When adding or changing a Controller Endpoint, apply Swagger Annotations as well and make `OpenApiDocumentationTest` pass (see [`docs/ai/openapi-documentation.md`](../../../docs/ai/openapi-documentation.md)).

## Architecture Restrictions

The items below are not yet finalized in this repository. Do not enforce them as global rules or introduce them arbitrarily without a dedicated related Issue.

```text
Detailed Package structure inside Modules (api/internal, etc.)
Fixed Controller-Service-Repository structure
Hexagonal Architecture / Clean Architecture
Common JPA Entity Base Class
QueryDSL structure
Security Filter Chain structure
OAuth and JWT structure
```

OpenAPI (springdoc) was introduced and finalized as of PR #52. Swagger Annotation rules follow [`docs/ai/openapi-documentation.md`](../../../docs/ai/openapi-documentation.md).

(See [`docs/ai/coding-conventions.md`](../../../docs/ai/coding-conventions.md) and [`docs/architecture/modularity.md`](../../../docs/architecture/modularity.md))

## Verification

- Compile
- Related Tests
- Spring Context loads correctly
- If you moved Packages or added a new Module, verify module boundaries with `./gradlew test --tests "*ModularityTest"`
- Full Test suite
- Build
- Review changed configuration files (`application.yaml`, etc.)
- Diff review
