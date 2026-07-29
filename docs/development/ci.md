# CI 및 Repository Policy

GETI-Server는 Pull Request가 Merge되기 전에 코드 품질, 단위 테스트, 통합 테스트, Spring Modulith 구조, Coverage, Build, Docker 구성을 GitHub Actions로 자동 검증한다. 이 문서는 실제 구성한 CI Workflow와 Repository Policy(PR Template, CODEOWNERS, Dependabot, Branch Protection)를 다룬다. CD(배포)는 이 문서의 범위가 아니다.

## CI와 CD 구분

```text
CI (이 PR의 범위)
→ Wrapper 검증, Compile, Spotless, detekt, Unit Test, Integration Test,
  Spring Modulith 구조 검증, Coverage Report, Gradle Build, Docker Compose/Image 검증

CD (이 PR의 범위가 아님)
→ Container Registry Push, 실제 서버 배포, 운영 Migration 실행, Release 자동화
```

## Workflow

`.github/workflows/ci.yml` (Workflow 이름: `CI`)

### Trigger

| Event | 대상 |
| --- | --- |
| `pull_request` | `develop`, `main` |
| `push` | `develop`, `main` |
| `workflow_dispatch` | 수동 실행 |

`paths-ignore`는 사용하지 않는다. 문서/설정 변경도 Workflow나 정책 문서와의 정합성 확인이 필요할 수 있기 때문이다.

### 권한

```yaml
permissions:
  contents: read
```

모든 Job이 `contents: read` 기본 권한만 사용한다. Artifact 업로드(`actions/upload-artifact`)는 별도 Write 권한 없이 Workflow Run에 종속된 Ephemeral Storage를 사용하므로 추가 권한이 필요 없다. Pull Request Comment, Label, Merge 등 어떤 Write 작업도 수행하지 않는다.

### Concurrency

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true
```

같은 PR 또는 Branch에 새 Commit이 Push되면 이전 Run을 취소한다.

### Job 구성

| Job (표시 이름) | 실행 내용 | Docker 필요 | `needs` |
| --- | --- | --- | --- |
| Wrapper Validation | `gradle/actions/wrapper-validation@v6`으로 Gradle Wrapper Checksum 검증 | 아니오 | - |
| Quality | `compileKotlin compileTestKotlin spotlessCheck detekt` | 아니오 | Wrapper Validation |
| Unit Test | `test koverHtmlReport koverXmlReport` | 아니오 | Wrapper Validation |
| Integration Test | `integrationTest`(PostgreSQL/Redis Testcontainers) | 예(GitHub-hosted Runner 기본 제공) | Wrapper Validation |
| Build | `build`(Spotless/detekt/Test/Kover Verify 포함) | 아니오 | Wrapper Validation, Quality, Unit Test |
| Docker Validation | `docker compose config`, `docker build`(Push 없음) | 예 | Wrapper Validation, Quality, Unit Test |

Job을 Wrapper Validation 뒤에 병렬로 배치해 실패 원인을 빠르게 구분할 수 있게 했다. Quality/Unit Test가 실패하면 Build/Docker Validation은 실행되지 않아 불필요한 비용을 줄인다.

### Unit Test에 포함된 검증

일반 `test` Task는 Docker 없이 실행되며 다음을 모두 포함한다([`testing-policy.md`](../ai/testing-policy.md) 참고).

- 일반 Unit Test
- Web Slice Test(`@WebMvcTest`, [`web-api.md`](./web-api.md))
- Configuration Test(`ApplicationProfileConfigurationTest`)
- Spring Modulith 구조 검증(`ModularityTest`, `ModuleDocumentationTest`, [`modularity.md`](../architecture/modularity.md))

별도 Job으로 분리하지 않고 Unit Test Job Summary에 포함된 Test Class 이름으로 실패 시 원인을 구분할 수 있게 했다.

### `build` Job이 Test를 다시 실행하는 이유

Quality/Unit Test Job에서 이미 Spotless/detekt/Unit Test를 실행했지만, `build` Job은 `./gradlew build`(Repository 전체에서 반복 사용하는 최종 검증 명령과 동일)를 그대로 실행한다. `-x test`로 임의 제외하지 않은 이유는 두 가지다.

1. 로컬에서 개발자가 실행하는 명령(`clean test build`)과 CI의 Build Job이 정확히 같은 결과를 내야 재현성이 보장된다.
2. `needs: [wrapper-validation, quality, unit-test]`로 앞선 Job이 실패하면 Build Job 자체가 실행되지 않으므로, 중복 실행에 따른 실질적인 비용은 Quality/Unit Test가 이미 통과한 경우로 제한된다.

프로젝트 규모(단일 Module, 로컬 `clean test build` 약 1분)를 고려할 때 이 중복은 감수할 만하다고 판단했다. 프로젝트가 커지면 재검토한다.

### Integration Test와 Docker

GitHub-hosted `ubuntu-latest` Runner는 Docker Daemon을 기본 제공한다. Testcontainers(PostgreSQL, Redis)가 이를 자동으로 사용하므로 별도 Docker Socket 설정, `TESTCONTAINERS_RYUK_DISABLED` 등의 Workaround를 추가하지 않았다. 별도 Service Container(`services:`)도 구성하지 않았다 — PR 8에서 이미 Testcontainers가 Container 생명주기를 직접 관리하도록 설계했기 때문이다.

### Docker Validation

```bash
docker compose config --quiet
docker build -t geti-server-app:ci .
```

Registry Login과 Image Push는 수행하지 않는다. `docker compose up`으로 PostgreSQL/Redis/MinIO 전체를 띄우지 않는다 — Integration Test가 이미 Testcontainers로 PostgreSQL/Redis 연동을 검증하고, MinIO는 아직 실제 Client 연동이 없어 Compose 전체 기동은 실행 시간 대비 추가로 검증되는 내용이 없기 때문이다.

## Gradle 환경

| 항목 | 값 |
| --- | --- |
| Java Distribution | Temurin(`actions/setup-java`) |
| Java Version | 25(`build.gradle.kts`의 Toolchain과 일치) |
| Gradle | Wrapper(9.5.1) 그대로 사용, `gradle/actions/setup-gradle@v6`으로 실행 |
| Cache | `setup-gradle`의 공식 Cache만 사용(`actions/setup-java`의 `cache: gradle`나 `actions/cache` 직접 구성과 중복 적용하지 않음). `cache-read-only: ${{ github.event_name == 'pull_request' }}`로 PR Run이 공유 Cache에 쓰지 않게 했다(Push Run만 Cache를 갱신) |
| Wrapper Validation | `gradle/actions/setup-gradle@v6`이 실행마다 자동으로도 검증하지만, 다른 Job이 시작되기 전에 가장 먼저 실패를 확인하기 위해 `gradle/actions/wrapper-validation@v6`을 별도 Job으로 분리했다 |

## Third-party Action 및 Version 고정

| Action | Version | 용도 |
| --- | --- | --- |
| `actions/checkout` | `v7` | Checkout |
| `actions/setup-java` | `v5` | JDK 설치 |
| `gradle/actions/setup-gradle` | `v6` | Gradle 실행/Cache/Wrapper 검증 |
| `gradle/actions/wrapper-validation` | `v6` | Gradle Wrapper Checksum 검증 |
| `actions/upload-artifact` | `v7` | Test/Coverage/detekt/Build 산출물 업로드 |

모두 공식 GitHub 또는 Gradle Vendor Action이며, `@main`/`@master`/`@latest`가 아닌 **Major Version Tag**로 고정했다. Major Tag를 선택한 이유는 (1) 이 Repository에 아직 SHA Pinning 정책이 없고, (2) Dependabot(`github-actions` Ecosystem, 아래 참고)이 Major Tag 갱신을 자동으로 추적/PR 생성할 수 있어 유지보수 부담이 낮기 때문이다. 더 강한 보안이 필요해지면 Full Commit SHA Pinning으로 전환하고 이 문서를 갱신한다.

## Artifact

| Artifact | 경로 | 조건 | Retention |
| --- | --- | --- | --- |
| `detekt-report` | `build/reports/detekt/` | 항상(`if: always()`) | 7일 |
| `unit-test-report` | `build/reports/tests/test/` | 항상 | 7일 |
| `coverage-report` | `build/reports/kover/html/`, `build/reports/kover/report.xml` | 항상 | 7일 |
| `integration-test-report` | `build/reports/tests/integrationTest/` | 항상 | 7일 |
| `build-jars` | `build/libs/*.jar` | 항상 | 7일 |

`if-no-files-found: ignore`로 설정해 Report가 생성되지 않은 조기 실패(Compile 실패 등)에서도 Workflow 자체가 Artifact 업로드 실패로 추가로 깨지지 않게 했다. `.env`, Secret, Database Dump, Docker Volume 등은 Artifact에 포함하지 않는다.

## Required Status Check

Ruleset/Branch Protection에 연결할 실제 GitHub Check 이름(Workflow 이름 `CI` + Job 표시 이름)이다.

```text
CI / Wrapper Validation
CI / Quality
CI / Unit Test
CI / Integration Test
CI / Build
CI / Docker Validation
```

Workflow 파일의 `name:`(workflow)과 각 Job의 `name:`을 변경하면 이 Check 이름도 바뀌어 기존 Required Check 설정이 끊어질 수 있다. 이름을 바꾸는 PR에서는 이 표와 실제 Branch Protection 설정을 함께 갱신한다.

## Repository Policy 현재 상태 (2026-07-29 실측)

`gh api repos/inryeok-office/GETI-Server/branches/{develop,main}/protection`로 확인했다.

| 항목 | `develop` | `main` |
| --- | --- | --- |
| Pull Request 필수 | 예(1 Approval, Stale Review 자동 Dismiss) | 예(1 Approval, Stale Review 자동 Dismiss) |
| Code Owner Review | 아니오 | 아니오 |
| Required Status Check | **없음** | **없음** |
| Force Push | 금지 | 금지 |
| Branch 삭제 | 금지 | 금지 |
| Conversation Resolution | 아니오 | 아니오 |
| Admin Bypass | 아니오(Admin도 규칙 적용) | 아니오(Admin도 규칙 적용) |
| Ruleset(신규 Rule 방식) | 없음(`[]`) | 없음(`[]`) |

Default Branch는 `main`이다. 지금까지 실제 PR 1~9는 모두 `develop`을 대상으로 Merge되었고 `main` 대상 PR은 아직 없었다.

## Required Status Check 적용 여부

PR 10(이 작업) 시점에는 **Branch Protection에 Required Status Check를 아직 적용하지 않았다.** 이유:

1. 이 CI Workflow는 이 Repository의 첫 GitHub Actions Workflow다. Required Check로 강제하기 전에 최소 한 번은 실제로 성공하는 것을 확인해야 한다.
2. Required Check 이름이 틀리면 이후 모든 PR이 "Expected — Waiting for status to be reported" 상태로 영구히 멈출 수 있다. 실제 Check 이름을 이 PR의 GitHub Actions Run으로 먼저 확인한다.
3. `enforce_admins: true`가 이미 켜져 있어(관리자도 예외 없음) 설정을 잘못하면 저장소 관리자도 즉시 우회할 수 없다.

이 PR에서 GitHub Actions Run이 실제로 성공하는 것을 확인한 뒤, 다음 조건을 모두 만족하면 별도 승인 하에 적용할 수 있다.

- 사용자(Repository 관리자)의 명시적 승인
- `develop`에 대한 Required Status Check로 위 6개 Check 이름 추가
- 기존 정책(1 Approval, Force Push/삭제 금지, Admin Enforce)은 유지
- `main`은 아직 실제 Release/Merge Flow가 불명확해 이번에는 변경하지 않는다(향후 `main`을 Release Branch로 실제 사용하기 시작하면 재검토)

적용 시 API 예시(참고용, 기존 필드를 덮어쓰지 않도록 현재 설정을 먼저 조회한 뒤 병합해서 반영해야 한다):

```bash
gh api -X PATCH repos/inryeok-office/GETI-Server/branches/develop/protection/required_status_checks \
  -f strict=true \
  -f 'checks[][context]=CI / Wrapper Validation' \
  -f 'checks[][context]=CI / Quality' \
  -f 'checks[][context]=CI / Unit Test' \
  -f 'checks[][context]=CI / Integration Test' \
  -f 'checks[][context]=CI / Build' \
  -f 'checks[][context]=CI / Docker Validation'
```

## Pull Request Template

`.github/pull_request_template.md`(기존 파일)에 다음을 최소 추가했다.

- Integration Test(`./gradlew integrationTest`) 실행 여부, CI 필수 Check 통과 여부
- 새 환경 변수 문서 반영 여부
- Migration을 수정하지 않고 새 파일로 추가했는지
- JPA Entity를 API 응답으로 직접 노출하지 않았는지
- 다른 Module의 내부 구현을 직접 참조하지 않았는지

기존 항목(작업 배경, 변경 내용, 테스트, 체크리스트 등)은 그대로 유지했다.

## CODEOWNERS

**적용하지 않았다(보류).** 이 저장소에는 실제 Collaborator가 16명 있지만(`gh api repos/.../collaborators`로 확인), `.github/`, `build.gradle.kts`, `Dockerfile`, `src/` 등 Path별로 누가 실제 담당자인지 알 수 있는 팀 구조 정보(Team, Role, 기존 CODEOWNERS)가 저장소 어디에도 없다. 추측으로 특정 인원을 Path Owner로 지정하지 않는다. 실제 담당 구조가 정해지면 그 정보를 바탕으로 CODEOWNERS를 추가한다.

## Dependabot

`.github/dependabot.yml`을 추가했다.

| Ecosystem | Directory | Target Branch | Schedule | PR 제한 | Group |
| --- | --- | --- | --- | --- | --- |
| `gradle` | `/` | `develop` | `weekly` | 5 | `spring-kotlin`, `test-and-quality` |
| `github-actions` | `/` | `develop` | `weekly` | 5 | `actions`(전체) |

자동 Merge는 구성하지 않았다. Reviewer/Assignee는 실제 담당자 정보가 없어 지정하지 않았다(CODEOWNERS와 동일한 이유).

## 로컬 사전 검증

CI에서 실행하는 모든 명령은 PR을 올리기 전에 로컬에서 먼저 실행한다.

```bash
./gradlew compileKotlin
./gradlew compileTestKotlin
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew integrationTest        # Docker 필요
./gradlew koverHtmlReport
./gradlew koverXmlReport
./gradlew check
./gradlew clean test build
docker compose config
docker build -t local-ci-validation:test .
```

Windows에서는 `.\gradlew.bat`를 사용한다.

## 실패 시 확인

1. GitHub Actions Run Log(`gh run view {run-id} --log-failed`)
2. 실패한 Job의 Step Summary(무엇을 실행했는지, 어떤 Artifact를 확인해야 하는지)
3. Artifact: `unit-test-report`, `integration-test-report`, `coverage-report`, `detekt-report`, `build-jars`
4. 실패를 다음처럼 분류해 원인을 좁힌다.

```text
Workflow Syntax
Action Version
Permission
Gradle Wrapper
JDK Version
Gradle Cache
Compile
Spotless
detekt
Unit Test
Spring Modulith
Integration Test
Testcontainers
Docker
Flyway
Kover
Artifact Upload
Timeout
Fork Permission
Repository Policy
Required Check
```

실패를 숨기기 위해 `continue-on-error`를 추가하지 않는다.
