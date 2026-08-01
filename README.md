# GETI-Server

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![CI](https://github.com/inryeok-office/GETI-Server/actions/workflows/ci.yml/badge.svg)](https://github.com/inryeok-office/GETI-Server/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

GETI 서비스의 Backend Server 저장소다. 처음 이 저장소를 Clone했다면 이 문서로 개발 환경을 구성한 뒤 [`docs/development/quick-start.md`](./docs/development/quick-start.md)에서 Clone부터 첫 PR까지의 실제 명령 순서를 확인한다.

## 현재 개발 상태

이 저장소는 Repository 구조, AI 개발 하네스, 코드 품질, 테스트, Spring Modulith, Configuration, Docker, Persistence, 공통 Web/API, CI, 전역 예외 처리까지 갖춘 **기반 구축(Foundation) 완료 단계**를 지나, 최신 최소 19개 Table ERD([`docs/architecture/erd.md`](./docs/architecture/erd.md))를 기준으로 한 **Domain Persistence 기반**(JPA Entity, Spring Data Repository, Flyway Migration)을 구성했다. Use Case(Service), Controller, OAuth Flow, 실제 CRUD API는 아직 구현하지 않았다. 전체 감사 결과는 [`docs/audit/foundation-audit.md`](./docs/audit/foundation-audit.md)를, GETI Notion 요구사항과 저장소 구현의 대조 결과는 [`docs/audit/notion-repository-sync.md`](./docs/audit/notion-repository-sync.md)를 따른다.

## 실제 기술 스택

아래 표는 이 저장소에 실제로 적용된 기술만 담는다. GETI Notion Tech Stack에 계획되어 있지만 아직 저장소에 구현되지 않은 항목(Spring Security/OAuth/JWT, QueryDSL, Elasticsearch, Kafka, Observability 스택 등)은 [`docs/audit/notion-repository-sync.md`](./docs/audit/notion-repository-sync.md)를 따르며, 이 표에는 포함하지 않는다.

| 구분 | 기술 | 비고 |
| --- | --- | --- |
| 언어 | Kotlin 2.3.21 | [`build.gradle.kts`](./build.gradle.kts) |
| Runtime | Java Toolchain 25 | Gradle Wrapper가 자동으로 Toolchain을 내려받는다 |
| Framework | Spring Boot 4.1.0 (Spring Framework 7.0.8) | |
| Build | Gradle 9.5.1 (Kotlin DSL, Wrapper 포함) | |
| Web | Spring MVC(Servlet), Bean Validation, Spring Boot Actuator(`health`만 노출) | [`docs/development/web-api.md`](./docs/development/web-api.md) |
| API 문서 | Springdoc OpenAPI 3.0.3(Swagger UI, `local`만 활성화) | [`docs/ai/openapi-documentation.md`](./docs/ai/openapi-documentation.md) |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL 18, Flyway | [`docs/development/persistence.md`](./docs/development/persistence.md) |
| Cache | Redis(Lettuce) | [`docs/development/persistence.md`](./docs/development/persistence.md) |
| Object Storage | MinIO(Docker 인프라만 구성, Application Client 연동 없음) | [`docs/development/docker.md`](./docs/development/docker.md) |
| 모듈 구조 | Spring Modulith 1.4.1 (Application Module 경계 검증) | [`docs/architecture/modularity.md`](./docs/architecture/modularity.md) |
| Test | JUnit 5, AssertJ, Mockito, Testcontainers, Spring Modulith Test | [`docs/development/testing.md`](./docs/development/testing.md) |
| Coverage | Kover | [`docs/development/testing.md`](./docs/development/testing.md) |
| 코드 품질 | Spotless(ktlint), detekt, EditorConfig | [`docs/development/code-quality.md`](./docs/development/code-quality.md) |
| 로컬 인프라 | Docker Compose(PostgreSQL, Redis, MinIO) | [`docs/development/docker.md`](./docs/development/docker.md) |
| CI | GitHub Actions(`CI` Workflow) | [`docs/development/ci.md`](./docs/development/ci.md) |

## Architecture

- Root Package: `team.inreok.getiserver`. 최상위 Production Package는 `domain`과 `global` 두 종류만 사용한다.
- 실제 비즈니스 기능을 담는 15개 Domain Package(`domain.member`, `auth`, `file`, `company`, `job`, `ai`, `recommendation`, `application`, `program`, `portfolio`, `notification`, `inquiry`, `collector`, `operation`, `audit`)가 `domain` 아래 독립된 Spring Modulith Application Module로 구성되어 있다(최신 19개 Table ERD, [`docs/architecture/erd.md`](./docs/architecture/erd.md)).
- `global`: 여러 Domain이 공유하는 기술 기반(`global.web`의 공통 성공 응답/Pagination/CORS/requestId, `global.error`의 오류 응답/전역 예외 처리)만 담는다. 특정 Domain 로직을 두지 않는다.
- Domain Package 내부는 필요한 책임만 만든다(`entity`(+`entity/type`), `repository`, `service`, `controller`, `dto`, `exception`). 현재는 Service/Controller가 없어 각 Domain이 `entity`/`repository`만 채운 상태다.

세부 Package Tree, Module 탐지 전략, 만들지 않는 Package 목록은 [`docs/architecture/modularity.md`](./docs/architecture/modularity.md)를 따른다. Domain 기능을 새로 개발하는 절차는 [`CONTRIBUTING.md`](./CONTRIBUTING.md)의 "기능 개발 절차"를 따른다.

## 시작하기

### 필수 설치 프로그램

| 프로그램 | 용도 | 비고 |
| --- | --- | --- |
| Git | 저장소 Clone과 버전 관리 | |
| Docker Desktop(Windows/macOS) 또는 Docker Engine(Linux) | PostgreSQL/Redis/MinIO 로컬 인프라 실행 | Docker Compose v2(`docker compose`) 필요 |
| GitHub CLI(`gh`) | Issue/PR 생성과 확인 | 선택이지만 [`docs/development/quick-start.md`](./docs/development/quick-start.md)의 Workflow에 필요 |
| Java 25 | Spring Boot 실행 | 선택. Gradle Wrapper가 Toolchain을 자동으로 내려받는다 |

### 1. Clone

```bash
git clone https://github.com/inryeok-office/GETI-Server.git
cd GETI-Server
```

### 2. 환경 변수

```bash
cp .env.example .env      # PowerShell: Copy-Item .env.example .env
```

`.env`가 없어도 `compose.yaml`과 Spring Boot `local` Profile에 동일한 기본값이 있어 바로 실행할 수 있다. `.env`는 Spring Boot가 자동으로 읽는 파일이 아니며 Git에 추적되지 않는다. 실제 사용하는 환경 변수 전체 목록과 Secret 관리 기준은 [`docs/development/configuration.md`](./docs/development/configuration.md)를 따른다.

### 3. Docker 인프라 실행

```bash
docker compose up -d
docker compose ps
```

PostgreSQL, Redis, MinIO 3개 Service만 실행된다(Spring Boot는 포함하지 않는다). 세부 사용법과 접속 정보, 문제 해결은 [`docs/development/docker.md`](./docs/development/docker.md)를 따른다.

### 4. Spring Boot 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

정상 기동 확인:

```bash
curl http://localhost:8080/actuator/health
```

### 5. 테스트 및 검증

```bash
./gradlew test              # Docker 불필요
./gradlew integrationTest   # Docker(Testcontainers) 필요
./gradlew spotlessApply     # 포맷이 흐트러졌다면 자동 적용
./gradlew check             # spotlessCheck + detekt + test + koverVerify
./gradlew clean test build  # 전체 검증
```

Windows에서는 `.\gradlew.bat`를 사용한다. 테스트 유형별 정책과 커버리지 Report는 [`docs/development/testing.md`](./docs/development/testing.md)를 따른다.

## API 문서

공통 성공/오류 응답, Pagination, CORS, requestId, Health Endpoint 등 모든 HTTP API가 따르는 공통 기반은 [`docs/development/web-api.md`](./docs/development/web-api.md)에 문서화되어 있다.

현재 구현된 모든 API(Auth, Member 도메인)는 Springdoc OpenAPI + Swagger UI로 문서화되어 있다. `local` Profile로 실행한 뒤 아래 주소에서 확인한다(Production에서는 기본 비활성화).

```text
Swagger UI:    http://localhost:8080/swagger-ui/index.html
OpenAPI JSON:  http://localhost:8080/v3/api-docs
```

JWT 인증이 필요한 API는 Swagger UI 우측 상단 **Authorize** 버튼에 `POST /api/v1/auth/{provider}/callback` 또는 `POST /api/v1/auth/token/refresh`로 발급받은 Access Token 값(`Bearer` 없이 Token 문자열만)을 입력하면 Swagger UI에서 바로 호출할 수 있다.

새 API를 추가할 때 지켜야 하는 Swagger Annotation 규칙과 `OpenApiDocumentationTest`(누락 시 Build 실패) 검증 기준은 [`docs/ai/openapi-documentation.md`](./docs/ai/openapi-documentation.md)를 따른다.

## 개발 Workflow

Issue 생성부터 Draft Pull Request까지의 전체 협업 절차, Branch/Commit Convention, Label 체계는 [`CONTRIBUTING.md`](./CONTRIBUTING.md)를 따른다. AI 개발 도구(Claude Code, Codex)를 사용할 때는 [`AGENTS.md`](./AGENTS.md)와 [`docs/ai/README.md`](./docs/ai/README.md)의 규칙을 따른다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [`CONTRIBUTING.md`](./CONTRIBUTING.md) | Issue/Branch/Commit/PR 협업 절차, Domain 기능 개발 절차 |
| [`docs/development/quick-start.md`](./docs/development/quick-start.md) | Clone부터 첫 PR까지 실제 명령 순서 |
| [`docs/development/configuration.md`](./docs/development/configuration.md) | Profile, 환경 변수, Secret 관리 |
| [`docs/development/docker.md`](./docs/development/docker.md) | Docker Compose 실행법, 접속 정보, 문제 해결 |
| [`docs/development/persistence.md`](./docs/development/persistence.md) | PostgreSQL/Redis 연결, Flyway, Testcontainers |
| [`docs/development/web-api.md`](./docs/development/web-api.md) | 공통 응답, ErrorCode, 전역 예외 처리, requestId |
| [`docs/ai/openapi-documentation.md`](./docs/ai/openapi-documentation.md) | Swagger/OpenAPI 문서화 필수 규칙과 자동 검증 |
| [`docs/development/testing.md`](./docs/development/testing.md) | 테스트 유형, Kover 커버리지 |
| [`docs/development/code-quality.md`](./docs/development/code-quality.md) | Spotless/ktlint/detekt |
| [`docs/development/ci.md`](./docs/development/ci.md) | GitHub Actions CI, Repository Policy |
| [`docs/architecture/modularity.md`](./docs/architecture/modularity.md) | Spring Modulith, Package Architecture, Domain Module 내부 구조 |
| [`docs/architecture/erd.md`](./docs/architecture/erd.md) | 최신 최소 19개 Table ERD, Enum, FK 삭제 정책, 다형적 참조 |
| [`docs/audit/foundation-audit.md`](./docs/audit/foundation-audit.md) | PR 1~11 기반 구축 전체 감사 결과 |
| [`docs/audit/notion-repository-sync.md`](./docs/audit/notion-repository-sync.md) | GETI Notion과 저장소 대조, 결정 필요 항목 |
| [`AGENTS.md`](./AGENTS.md), [`docs/ai/README.md`](./docs/ai/README.md) | AI 개발 도구 공통 규칙 |
| [`CLAUDE.md`](./CLAUDE.md), `.claude/` | Claude Code 전용 규칙, Command, Skill |
| [`docs/automation/claude-daily-audit-routine.md`](./docs/automation/claude-daily-audit-routine.md) | Claude 일일 자동 점검 Routine 실행 기준과 최종 프롬프트 |
| `.codex/` | Codex 전용 정책, Prompt Template |

## Troubleshooting

| 문제 | 확인할 문서 |
| --- | --- |
| Docker가 실행되지 않음, Port 충돌 | [`docs/development/docker.md`](./docs/development/docker.md)의 "문제 해결" |
| 환경 변수, Profile, Secret | [`docs/development/configuration.md`](./docs/development/configuration.md) |
| PostgreSQL/Redis/Flyway 연결 문제 | [`docs/development/persistence.md`](./docs/development/persistence.md) |
| API 응답 형식, 공통 오류 처리 | [`docs/development/web-api.md`](./docs/development/web-api.md) |
| CI 실패 원인 분석 | [`docs/development/ci.md`](./docs/development/ci.md)의 "실패 시 확인" |
| Package를 어디에 만들어야 하는지 | [`docs/architecture/modularity.md`](./docs/architecture/modularity.md) |
| AI 도구(Claude Code, Codex) 사용 규칙 | [`AGENTS.md`](./AGENTS.md), [`docs/ai/README.md`](./docs/ai/README.md) |
| Notion 요구사항과 저장소 구현이 다른 것 같을 때 | [`docs/audit/notion-repository-sync.md`](./docs/audit/notion-repository-sync.md) |
| Claude 일일 자동 점검 Routine 설정/동작 기준 | [`docs/automation/claude-daily-audit-routine.md`](./docs/automation/claude-daily-audit-routine.md) |

`docker compose down -v`는 로컬 PostgreSQL/Redis/MinIO 데이터를 모두 삭제하는 파괴적 명령이다. 의도적으로 로컬 환경을 초기화할 때만 실행한다.

## License

이 저장소는 [MIT License](./LICENSE)를 따른다.
