# 보안 정책 (AI 작업 원칙)

이 문서는 AI Agent가 GETI-Server에서 작업할 때 지켜야 하는 보안 원칙을 다룬다. 실제 Spring Security, 인증/인가 구현은 이 단계의 범위가 아니며, 여기서는 AI 작업 과정 자체의 보안 원칙만 다룬다.

## Secret 관리

- Secret, Token, API Key, Password, 인증서, Private Key를 코드나 설정 파일에 하드코딩하지 않는다.
- 예시나 문서에도 실제로 동작 가능한 Secret 값을 사용하지 않는다. 필요하면 `YOUR_SECRET`, `<token>`처럼 명백한 placeholder를 사용한다.
- `.env`, 인증서(`*.pem`, `*.key`, `*.p12`), 기타 Secret 파일을 Commit하지 않는다. `.gitignore`에 이미 등록된 항목을 임의로 제거하지 않는다.
- 새로운 종류의 로컬 전용/Secret 파일이 생기면 `.gitignore`에 추가할지 먼저 검토한다.

## 환경변수

- 환경별로 달라지는 값(연결 정보, Key 등)은 하드코딩 대신 Profile(`local`/`test`/`prod`) 또는 환경변수로 다룬다. Profile 전략, 환경 변수 Naming Convention, `.env.example` 사용 방식은 [`docs/development/configuration.md`](../development/configuration.md)를 따른다.
- `.env`는 Spring Boot가 자동으로 읽는 파일이 아니다. 자동 로딩된다고 문서화하거나 가정하지 않는다.
- Secret에는 안전하지 않은 기본값(`${SECRET:change-me}` 등)을 제공하지 않는다. 필수 Secret이 없으면 명확하게 실패하도록 둔다.

## Docker

- 로컬 인프라(`compose.yaml`)는 개발 전용이며, PostgreSQL/Redis/MinIO Image는 공식 Image와 고정 Version(Patch/Release Tag)만 사용한다. `latest`나 Major-only Tag를 사용하지 않는다.
- Compose 파일에 실제 운영 Secret을 작성하지 않는다. Local 전용 기본 Credential은 운영에서 재사용할 수 없다는 점을 문서에 명시한다.
- `privileged`, Docker Socket Mount, Host Network를 사용하지 않는다.
- `docker compose down -v`는 Local 데이터를 삭제하는 파괴적 명령이다. 사용자의 명시적 요청 없이 실행하지 않는다([`docs/development/docker.md`](../development/docker.md) 참고).

## 로그와 출력

- 로그, 커밋 메시지, PR 본문, 완료 보고에 Secret이나 개인정보(실제 사용자 이메일, 전화번호 등)를 출력하지 않는다.
- Secret이 포함되어 있을 가능성이 있는 파일(`.env`, 인증서 등)의 전체 내용을 읽어서 대화에 그대로 출력하지 않는다.

## 인증과 인가

- 테스트나 개발 편의를 위해 인증/인가 로직을 임의로 제거하거나 우회하지 않는다.
- 인증/인가가 아직 구현되지 않은 현재 단계에서, 임시로 우회 코드를 추가하고 완료된 것처럼 남겨두지 않는다.

## 외부 입력과 Dependency

- 사용자 입력이나 외부 데이터를 다루는 코드를 작성할 때는 검증 없이 신뢰하지 않는다.
- 새 Dependency를 추가하기 전에 출처와 필요성을 확인한다. 근거 없이 여러 Dependency를 한 번에 대량으로 추가하지 않는다.
- 검증되지 않은 외부 Script를 다운로드해서 실행하지 않는다.

## Shell 및 Git 명령

- 파일을 삭제하거나 되돌리는 명령을 실행하기 전에 영향 범위를 확인한다.
- [`AGENTS.md`](../../AGENTS.md)에 명시된 파괴적 명령(`git reset --hard`, `git clean -fd`, `git push --force` 등)은 사용자의 명시적 요청 없이 실행하지 않는다.
- Shell Injection이 발생할 수 있는 방식으로 외부 입력을 명령어에 그대로 삽입하지 않는다.

## 운영 데이터와 사용자 데이터

- 운영 환경의 데이터베이스나 서비스에 직접 접근하거나 수정하지 않는다.
- 테스트 데이터로 실제 사용자 정보를 사용하지 않는다. 필요하면 명백히 가짜임을 알 수 있는 값을 사용한다.

## Persistence (PostgreSQL / Redis)

- `spring.jpa.hibernate.ddl-auto`를 `create`/`create-drop`/`update`로 설정하지 않는다. Schema 변경은 Flyway Migration만으로 수행한다([`docs/development/persistence.md`](../development/persistence.md)).
- `spring.flyway.clean-disabled=true`(모든 환경에서 `flyway clean` 차단)를 임의로 되돌리지 않는다.
- 실제 운영 PostgreSQL/Redis에 연결하거나 실제 값으로 마이그레이션을 실행하지 않는다. 로컬 검증은 `docker compose`(Local 전용 Credential) 또는 `integrationTest`의 Testcontainers만 사용한다.
- Datasource URL, Redis 접속 정보를 Log로 출력할 때 Password 등 Secret 성격 값이 함께 노출되지 않도록 주의한다.
- Testcontainers는 각 Test 종료 시 자동으로 정리된다. 이 PC에 다른 프로젝트의 기존 Container/Volume이 있을 수 있으므로, Persistence 검증 목적으로 `docker compose down -v`나 임의의 Container 정리 명령을 실행할 때는 대상이 이 저장소가 생성한 리소스인지 먼저 확인한다.

## GitHub Actions / CI

- Workflow의 기본 권한은 `permissions: contents: read`를 유지한다. `contents: write`, `pull-requests: write`, `actions: write`, `id-token: write` 등은 실제로 필요한 경우에만, 최소 범위(Job 단위)로 부여한다.
- `pull_request_target`, `workflow_run` 등 Fork PR에서 상승된 권한이나 Secret에 접근할 수 있는 Trigger는 명확한 보안 검토와 사용자 승인 없이 추가하지 않는다.
- Secret 값을 Workflow YAML에 직접 작성하지 않는다. GitHub Secret을 새로 생성하거나 조회·출력하지 않는다.
- Third-party Action은 공식/검증된 Vendor Action만 사용하고 `@main`/`@master`/`@latest`가 아닌 고정된 Version(Major Tag 또는 Commit SHA)을 사용한다([`docs/development/ci.md`](../development/ci.md) 참고).
- CI를 통과시키기 위해 Test, Spotless, detekt를 비활성화하거나 `continue-on-error: true`를 필수 Job에 추가하지 않는다.
- Repository Ruleset, Branch Protection, Required Status Check는 사용자(Repository 관리자)의 명시적 승인 없이 파괴적으로 변경하지 않는다. 특히 아직 한 번도 성공하지 않은 Workflow의 Check를 Required로 설정해 이후 모든 PR이 막히는 상황을 만들지 않는다.
- Webhook URL과 API Token은 Source, Workflow, Issue, PR, Commit Message, 대화 응답 어디에도 평문으로 작성하지 않는다. Discord CI 알림은 `DISCORD_CI_WEBHOOK_URL` Repository Secret으로만 참조한다([`docs/development/ci.md`](../development/ci.md) 참고).
- 사용자가 대화나 Issue/PR 등 외부 채널에 노출한 Webhook URL이나 Token은 이미 노출된 것으로 취급하고 재사용하지 않는다. 값을 조회하거나 다시 출력하지 않고, 사용자에게 즉시 재발급/재생성을 안내한다.
- 알림(Discord 등) Job이 실패해도 코드 품질/테스트 결과 자체가 실패로 처리되지 않게 하고, 알림 Job을 Required Status Check로 지정하지 않는다.
- Fork PR/Dependabot PR처럼 Secret이 전달되지 않는 상황에서는 알림 전송을 안전하게 Skip하고, 이를 위해 `pull_request_target`을 추가하지 않는다.
- Discord 등 외부 서비스로 보내는 Payload에 Branch/Actor/PR 제목 등 외부 입력을 포함할 때는 Shell 문자열 결합이 아니라 `jq --arg` 등 안전한 Escape 방식을 사용하고, Mention Injection을 막기 위해 `allowed_mentions` 등 제공되는 제한 옵션을 적용한다.

## Web / API

- 오류 응답에 Exception Message, Stack Trace, Exception Class 이름, SQL, Database Connection 정보, Request Body 전체, Authorization Header, 내부 Package 경로를 포함하지 않는다([`docs/development/web-api.md`](../development/web-api.md)의 `GlobalExceptionHandler` 정책 참고).
- CORS는 `allowedOrigins`에 Wildcard(`*`)와 `allowCredentials=true`를 함께 설정하지 않는다. 실제 Client Origin이 확정되지 않았다면 가짜 운영 Domain을 기본값으로 넣지 않고 빈 상태(CORS 비활성)로 둔다.
- Actuator는 `health`만 웹에 노출하고 `env`/`beans`/`configprops`/`heapdump`/`threaddump`/`loggers`/`metrics` 등은 노출하지 않는다. Health Detail은 `show-details=never`를 유지한다(인증이 아직 없는 현재 단계에서는 특히 중요하다).
- 4xx 오류는 Stack Trace 없이 WARN 이하로, 5xx 오류만 Stack Trace와 함께 ERROR로 기록한다.

## 보고 원칙

- 보안과 관련된 가정(예: "이 값은 아직 실제 Secret 관리 체계가 없어 평문으로 두었다")을 발견하면 임의로 판단해 조용히 넘어가지 않고 완료 보고에 명시한다.
- 작업 범위 밖에서 보안 문제를 발견하면 임의로 수정하지 않고 후속 Issue 후보로 보고한다.
