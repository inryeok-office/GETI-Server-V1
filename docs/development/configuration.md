# Configuration 및 Profile

GETI-Server는 공통 설정과 환경별 설정을 분리하고, Secret을 저장소에 포함하지 않으면서 개발자와 AI Agent가 일관되게 설정을 추가할 수 있도록 다음 기준을 사용한다.

## Profile 구성

| Profile | 설정 파일 | 책임 |
| --- | --- | --- |
| 공통 | `src/main/resources/application.yaml` | 모든 환경에서 동일하고 Secret이 아닌 설정. 현재는 `spring.application.name`만 있다. |
| `local` | `src/main/resources/application-local.yaml` | 개발자 로컬 실행에 필요한 안전한 Override. 현재는 애플리케이션 Package(`team.inreok.getiserver`) Logging 수준을 `DEBUG`로 높이는 설정만 있다. |
| `develop` | `src/main/resources/application-develop.yaml` | `develop` Branch CD 배포(EC2) 전용(`docs/development/cd.md`). Spring Profile Group(`spring.profiles.group.develop: local`, `application.yaml`)으로 `local`의 Infra 기본값을 그대로 물려받고, Collector 개발용 Seed 기본값(`COLLECTOR_SEED_ENABLED=true`)만 재정의한다(Issue #62). `compose.yaml`의 `app` Service 기본 `SPRING_PROFILES_ACTIVE`가 이 Profile이다. |
| `test` | (파일 없음) | 테스트는 `spring-boot-starter-data-jpa-test`/`webmvc-test`가 제공하는 `com.h2database:h2`(`testRuntimeOnly`)를 Spring Boot가 자동으로 감지해 In-memory DB로 JPA Context를 구성한다. 현재 Override가 필요한 설정이 없어 `application-test.yaml`을 만들지 않았다. |
| `prod` | `src/main/resources/application-prod.yaml` | 운영 환경 전용. Secret은 기본값 없이 필수 환경 변수로만 받아 누락 시 기동을 실패시킨다(Fail-Fast). Swagger UI/OpenAPI JSON을 비활성화한다. |

파일이 없는 Profile은 공통 설정만 적용된다. 이는 의도된 상태이며 누락이 아니다.

## Profile 활성화

Spring Boot 표준 환경 변수 `SPRING_PROFILES_ACTIVE`를 사용한다. 저장소의 어떤 설정 파일에도 `spring.profiles.active`를 하드코딩하지 않았다.

Git Bash 또는 Unix:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

CMD:

```cmd
set SPRING_PROFILES_ACTIVE=local
gradlew.bat bootRun
```

Profile을 지정하지 않으면 공통 설정만 적용된다(현재는 `local`의 `DEBUG` Logging도 적용되지 않는 상태이며, 이는 [`ApplicationProfileConfigurationTest`](../../src/test/kotlin/team/inreok/getiserver/ApplicationProfileConfigurationTest.kt)로 검증한다).

## 환경 변수

실제로 사용하는 환경 변수만 정리한다.

| 환경 변수 | Spring Property | 필수 여부 | 사용 Profile | Secret 여부 | 기본값 |
| --- | --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | 선택 | 전체 | 아니오 | 없음(미지정 시 공통 설정만 적용) |
| `APP_VERSION` | `app.deployment.version` | 선택 | 전체 | 아니오 | `0.0.1-SNAPSHOT`(build.gradle.kts의 `version`과 동일) |
| `APP_GIT_SHA` | `app.deployment.git-sha` | 선택 | 전체 | 아니오 | `unknown` |
| `APP_BUILD_TIME` | `app.deployment.build-time` | 선택 | 전체 | 아니오 | `unknown` |
| `APP_ENVIRONMENT` | `app.deployment.environment` | 선택 | 전체 | 아니오 | `local` |
| `COLLECTOR_MMA_ENABLED` | `app.collector.provider.mma.enabled` | 선택 | 전체 | 아니오 | `false` |
| `COLLECTOR_MMA_SERVICE_KEY` | `app.collector.provider.mma.service-key` | 선택 | 전체 | 예 | 없음(비어 있으면 `configured=false`) |
| `COLLECTOR_JOB_ALIO_ENABLED` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 아니오 | `false` |
| `COLLECTOR_JOB_ALIO_SERVICE_KEY` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 예 | 없음 |
| `COLLECTOR_CLEAN_EYE_ENABLED` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 아니오 | `false` |
| `COLLECTOR_CLEAN_EYE_SERVICE_KEY` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 예 | 없음 |
| `COLLECTOR_NARA_ILTEO_ENABLED` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 아니오 | `false` |
| `COLLECTOR_NARA_ILTEO_SERVICE_KEY` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 예 | 없음 |
| `COLLECTOR_SARAMIN_ENABLED` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 아니오 | `false` |
| `COLLECTOR_SARAMIN_ACCESS_KEY` | (아직 미연동, 값 이름만 예약) | 선택 | 전체 | 예 | 없음 |
| `COLLECTOR_EXTERNAL_SCHEDULER_ENABLED` | `app.collector.scheduler.external-enabled` | 선택 | 전체 | 아니오 | `false` |
| `COLLECTOR_SEED_ENABLED` | `app.collector.seed.enabled` | 선택 | 전체 | 아니오 | `false`(`develop`만 `true`) |
| `DISCORD_JOB_NOTIFICATION_ENABLED` | `app.discord.job-notification.enabled` | 선택 | 전체 | 아니오 | `false`(`develop`만 `true`) |
| `DISCORD_JOB_WEBHOOK_URL` | `app.discord.job-notification.webhook-url` | 선택 | 전체 | 예 | 없음(비어 있으면 `isConfigured()=false`) |
| `DISCORD_JOB_NOTIFY_INITIAL_IMPORT` | `app.discord.job-notification.notify-initial-import` | 선택 | 전체 | 아니오 | `false` |
| `DISCORD_CHANNEL_JOB_NOTICE` | `app.discord.channel-policy.channels.job-notice.channel-id` | 선택 | 전체 | 아니오 | 없음(비어 있으면 Delivery 미생성) |
| `DISCORD_CHANNEL_PROGRAM_NOTICE` | `app.discord.channel-policy.channels.program-notice.channel-id` | 선택 | 전체 | 아니오 | 없음(비어 있으면 Delivery 미생성) |
| `DISCORD_CHANNEL_INQUIRY_ALERT` | `app.discord.channel-policy.channels.inquiry-alert.channel-id` | 선택 | 전체 | 아니오 | 없음(비어 있으면 Delivery 미생성) |
| `DISCORD_ROLE_GRADE_1` | `app.discord.channel-policy.grade-roles.1` | 선택 | 전체 | 아니오 | 없음(비어 있으면 Mention 없음) |
| `DISCORD_ROLE_GRADE_2` | `app.discord.channel-policy.grade-roles.2` | 선택 | 전체 | 아니오 | 없음(비어 있으면 Mention 없음) |
| `DISCORD_ROLE_GRADE_3` | `app.discord.channel-policy.grade-roles.3` | 선택 | 전체 | 아니오 | 없음(비어 있으면 Mention 없음) |
| `FILE_STORAGE_BUCKET` | `app.file.storage.bucket` | **prod 필수** | 전체 | 아니오 | `local`은 `geti-local`, `prod`는 없음(미지정 시 기동 실패) |
| `FILE_STORAGE_REGION` | `app.file.storage.region` | **prod 필수** | `prod` | 아니오 | `local`은 `us-east-1` 고정, `prod`는 없음(미지정 시 기동 실패) |
| `OPENAI_API_KEY` | `app.ai.openai.api-key` | 선택 | 전체 | 예 | 없음(비어 있으면 `isConfigured()=false`, 분석 요청은 즉시 FAILED) |
| `OPENAI_MODEL` | `app.ai.openai.model` | 선택 | 전체 | 아니오 | `gpt-4o-mini`(실제 운영 값이 아닌 최소 Fallback) |
| `APP_WEB_CORS_ALLOWED_ORIGINS` | `app.web.cors.allowed-origins` | 선택 | 전체 | 아니오 | 없음(빈 목록이면 CORS Mapping 미등록) |
| `APP_WEB_OAUTH_CALLBACK_REDIRECT_URL` | `app.web.oauth.callback-redirect-url` | 선택 | 전체 | 아니오(공개 Frontend URL) | 없음(비어 있으면 `clientType=WEB` 요청만 `OAUTH_WEB_REDIRECT_NOT_CONFIGURED`로 실패) |

`APP_VERSION`/`APP_GIT_SHA`/`APP_BUILD_TIME`/`APP_ENVIRONMENT`는 `/actuator/info`의 `deployment` Field(`DeploymentInfoContributor`)와 CD의 배포 SHA 검증에 쓰인다. CD가 Docker Build/Runtime 시점에 실제 값을 주입하며([`cd.md`](./cd.md) 참고), 로컬 개발자가 직접 설정할 필요가 없어(안전한 기본값으로 기동됨) `.env.example`에는 포함하지 않았다.

`COLLECTOR_*`는 Issue #62(Collector Provider 연동)에서 추가했다. `COLLECTOR_MMA_*`만 실제 Provider 구현(`MmaCollectorProvider`)이 연결되어 있고, 나머지 세 Provider(`JOB_ALIO`/`CLEAN_EYE`/`NARA_ILTEO`)는 공식 활용신청 상세 페이지가 Base URL·Query Parameter·응답 필드를 공개하지 않아 이번 범위에서 추측 구현을 하지 않았다 — 값 이름만 `.env.example`에 예약해 두고 실제 Adapter는 후속 작업에서 연결한다. `COLLECTOR_SEED_ENABLED=true`를 `prod`/`production` Profile에서 사용하면 `CollectorSeedProdGuard`가 애플리케이션 기동을 거부한다(Fail-Fast).

`DISCORD_JOB_*`는 Issue #62 확장 범위(Collector 실제 Provider 수집으로 새로 등록(CREATED)된 공고에 대한 Discord Webhook 알림)에서 추가했다. CD 배포 알림(`DISCORD_CD_WEBHOOK_URL`)과는 완전히 별개의 Secret이며([`cd.md`](./cd.md#collector-신규-공고-discord-webhook-secret-전달-discord_job_webhook_url) 참고), 세 값 모두 설정하지 않아도 애플리케이션·Collector·공고 등록은 정상 동작하고 알림만 비활성 상태로 남는다. `DISCORD_JOB_NOTIFY_INITIAL_IMPORT=false`(기본값)는 해당 Provider의 최초 성공 수집에서는 개별 알림을 보내지 않는다(수백 건이 한 번에 쌓일 수 있는 최초 전체 수집에서 Discord Rate Limit/스팸을 피하기 위함) — 이후 일일 증분 수집의 신규 공고부터 개별 알림이 발송된다. `CollectorDevSeedRunner`(개발용 Fixture)는 이 알림 경로를 거치지 않아 Seed 데이터로는 알림이 발생하지 않는다.

`DISCORD_CHANNEL_*`/`DISCORD_ROLE_GRADE_*`는 Issue #97(Job·Program·Inquiry Discord Event 연결)에서 추가했다. 위 `DISCORD_JOB_*`(Collector 수집 공고용 Webhook)와는 전달 방식도 값도 완전히 별개이며 절대 같은 값으로 재사용하지 않는다. 논리 채널 Key(`job-notice`, `program-notice`, `inquiry-alert`)와 기본 Key는 Secret이 아니라 `application.yaml`의 `app.discord.channel-policy`에서 확정하고, 환경 변수에는 Snowflake만 주입한다. **값을 비워 두어도 애플리케이션 기동과 공고·프로그램·문의 등록 API는 정상 동작하며**, Discord Delivery를 만들지 않고 경고 로그만 남긴다(Fail-Fast 아님). Role은 게시 알림(CREATE)에서만 Mention에 쓰이고, 비어 있는 학년은 Mention 없이 전달된다. 실제 채널 4개의 이름·용도가 확정되면 환경 변수만 채우면 되고 재배포가 필요 없다.

`DISCORD_BOT_*`(`app.discord.bot.*`, Issue #96에서 추가한 GETI-Bot-V1 Internal API 접속 설정)는 아직 이 표와 `.env.example`에 반영되어 있지 않다 — Issue #97 범위 밖이라 이번에 함께 채우지 않았고, 별도 후속 작업으로 정리한다.

`FILE_STORAGE_*`는 File 도메인(Issue #85)에서 추가했다. 운영은 AWS S3, local은 `compose.yaml`의 MinIO를 쓰지만 Adapter 구현은 하나이며 Endpoint와 Path Style 설정으로만 갈린다. **운영에서는 Access Key를 환경 변수로 주입하지 않는다** — `app.file.storage.access-key`/`secret-key`를 선언하지 않으면 AWS SDK가 `DefaultCredentialsProvider`로 EC2 Instance Profile(IAM Role)에서 자격증명을 받아오기 때문이다. local은 기존 `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`를 그대로 재사용한다(`application-local.yaml`). 자세한 구성과 인프라 선행 조건은 [`persistence.md`](./persistence.md)의 "Object Storage" 절과 [`file-domain-plan.md`](../file/file-domain-plan.md) §14를 따른다.

`OPENAI_*`는 AI Analysis Phase 1(Issue #132)에서 추가했다. `OPENAI_API_KEY`가 비어 있어도 애플리케이션 기동과 공고 게시 API는 정상 동작하며, `OpenAiAnalysisProvider.isConfigured()`가 false를 반환해 실제 분석 요청만 즉시 FAILED로 기록된다(Fail-Fast 아님, Collector/Discord Bot과 동일한 방식). `OPENAI_MODEL`의 `gpt-4o-mini` 기본값은 값이 없을 때도 기동이 깨지지 않게 하는 최소 Fallback일 뿐 실제 운영 값을 의미하지 않는다 — Business Code에는 Model 이름을 Hard Coding하지 않았다.

`APP_WEB_OAUTH_CALLBACK_REDIRECT_URL`은 OAuth Web Callback 결함 수정(Issue #162)에서 추가했다. `GET /api/v1/auth/{provider}/authorize?clientType=WEB`으로 시작한 로그인만 이 값을 쓴다 — `/callback`이 Token/회원 정보 JSON 대신 이 URL로 302 Redirect하고(성공 시 그대로, 실패 시 `?error={ErrorCode}`만 덧붙여), Frontend는 이미 설정된 Refresh Token Cookie로 `POST /api/v1/auth/token/refresh`를 호출해 Access Token을 얻는다. `clientType`을 지정하지 않는 기존 호출자(App 등)는 이 값과 무관하게 기존 JSON 응답을 그대로 받는다(Breaking Change 없음). 값 자체는 공개 Frontend URL이라 Secret은 아니지만, 실제 운영 URL이 아직 확정되지 않아 예시 값도 채우지 않았다(DECISION_REQUIRED).

PostgreSQL/Redis 연결 환경 변수(`DATABASE_URL` 등)는 이 표에 중복하지 않고 [`persistence.md`](./persistence.md)에서 관리한다.

`SERVER_PORT`(→ `server.port`)처럼 Spring Boot가 기본으로 지원하는 환경 변수는 [Relaxed Binding](https://docs.spring.io/spring-boot/reference/features/external-config.html)으로 이미 동작하므로 `application.yaml`에 `${SERVER_PORT:8080}` 형태로 다시 선언하지 않았다. 새 환경 변수가 필요해지면 이 표와 `.env.example`을 함께 갱신한다.

## Web CORS

`APP_WEB_CORS_ALLOWED_ORIGINS`는 브라우저에서 API를 호출하는 Web Client의 Origin(`scheme://host:port`) 목록이다. GETI-Client-V1는 `next dev`를 사용하고 Port를 별도로 지정하지 않으므로 Local 개발 Origin은 `http://localhost:3000`이다.

값이 없으면 `CorsProperties.allowedOrigins`가 빈 목록으로 바인딩되어 CORS Mapping 자체를 등록하지 않는다. 이는 모든 Origin을 허용하는 것이 아니라 CORS가 비활성화되어 브라우저의 기본 Same-Origin 정책만 적용되는 의미다.

여러 Origin은 Spring Boot의 List 바인딩 규칙에 따라 쉼표로 구분한다.

```dotenv
APP_WEB_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3001
```

Local 개발에서는 `.env.example`을 `.env`로 복사하거나 실행 환경에 아래처럼 설정한다. Compose의 `app` Service는 이 값을 전달하며, 값이 없을 때만 `http://localhost:3000`을 기본값으로 사용한다.

```dotenv
APP_WEB_CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Wildcard(`*`)는 사용하지 않는다. 특히 `allowCredentials=true`일 때 Wildcard Origin은 현재 `CorsProperties` 검증에서 애플리케이션 시작 시 거부되므로, Cookie 등 Credential을 허용해야 하는 경우에도 실제 Origin을 명시적으로 나열한다. 기본 Credential 정책(`allowCredentials=false`)은 유지한다.

Staging/Production에서는 실제 배포된 Client Domain(예: `https://example.com`)을 배포 환경의 `APP_WEB_CORS_ALLOWED_ORIGINS`로 주입해야 한다. 해당 환경의 실제 값은 이 Repository에서 확인할 수 없으며, 배포 담당자의 외부 검증이 필요하다. Client Domain 또는 개발 서버 Port가 변경되면 이 환경 변수와 `.env.example` 예시를 함께 갱신한다.

### Naming Convention

- Spring Property `app.external-api.base-url` → 환경 변수 `APP_EXTERNAL_API_BASE_URL`처럼 대문자 Snake Case로 변환한다(Spring Boot Relaxed Binding 규칙).
- `HOST`, `PORT`, `URL`, `TOKEN`, `SECRET`처럼 범용적인 이름 대신 서비스/설정 영역을 포함한 이름을 사용한다. 예: `EXTERNAL_API_BASE_URL`, `OAUTH_GOOGLE_CLIENT_SECRET`.
- `PASSWORD`/`SECRET`/`TOKEN`/`PRIVATE_KEY`가 이름에 포함된 값은 Secret으로 취급한다(아래 참고).
- `SPRING_PROFILES_ACTIVE`, `SERVER_PORT` 등 Spring Boot 표준 환경 변수는 재정의하지 않는다.
- 현재 사용하지 않는 미래 인프라(Kafka, Elasticsearch 등)의 환경 변수를 미리 만들지 않는다. 실제로 연결하는 PR에서 이 문서와 함께 추가한다. DB/Redis는 이미 연결되어 있으며 [`persistence.md`](./persistence.md)를 따른다.

## Secret 관리

- Password, Private Key, Client Secret, API Token, Access/Refresh Token, JWT Signing Secret, Encryption Key, Cloud Access Key, SMTP/DB Credential은 모두 Secret으로 취급한다.
- Secret은 `application*.yaml`, 문서, Source Code, Issue/PR 본문, Commit Message, `.env.example`에 실제 값으로 작성하지 않는다.
- Secret이 필요한 설정은 값 없이 환경 변수 이름만 참조하도록 만들고(`${OAUTH_GOOGLE_CLIENT_SECRET}`), 안전하지 않은 기본값(`${OAUTH_GOOGLE_CLIENT_SECRET:change-me}` 등)을 제공하지 않는다. 현재는 이런 설정이 하나도 없다.
- `.env`는 Spring Boot가 자동으로 읽는 파일이 아니다. 이 저장소 어떤 문서에도 그렇게 작성하지 않는다. `.env`는 개발자가 직접 셸에 export하거나 IDE Run Configuration에 옮겨 사용하는 개인 참고용 파일이며, `.gitignore`(`.env`, `.env.*`, `!.env.example`)로 Commit되지 않는다.
- `.env.example`([Repository Root](../../.env.example))은 실제 운영 Secret이 없는 Template이며, 현재 사용하는 환경 변수만 포함한다. `DATABASE_PASSWORD` 등 일부 값은 Local 전용 기본값을 그대로 보여주지만(Docker Compose 기본값과 동일), 운영 환경에서 재사용 가능한 실제 Secret이 아니다.
- 운영 Secret은 향후 DevOps 담당자가 배포 환경(GitHub Environment, Secret Manager 등)에서 관리한다. 이번 PR은 그 대상이 될 환경 변수 이름 규칙만 제공한다.

## Type-safe Configuration (`@ConfigurationProperties`)

현재 이 프로젝트에는 Custom Application Property가 하나도 없다(공통 설정의 `spring.application.name`은 Spring Boot 내장 Property다). 그래서 `@ConfigurationProperties` Class를 미리 만들지 않았다. 아래처럼 기본값만 있는 의미 없는 Class를 만드는 것을 피했다.

```kotlin
// 이렇게 만들지 않는다 — 검증할 실제 설정이 없다.
@ConfigurationProperties("app")
data class AppProperties(val name: String = "app")
```

### 실제 설정이 생기면 적용할 기준

- Kotlin `data class` + Constructor Binding을 사용한다(Spring Boot가 Kotlin Constructor Binding을 기본 지원).
- `val`을 사용하고, 누락을 숨기기 위해 모든 값을 Nullable이나 기본값으로 만들지 않는다.
- 등록은 `@ConfigurationPropertiesScan`(Package 전체 자동 등록)과 `@EnableConfigurationProperties`(명시적 등록) 중 하나를 선택한다. 현재 등록 대상 Class가 없어 두 Annotation 모두 아직 추가하지 않았다. Class 수가 늘어나기 전까지는 명시적인 `@EnableConfigurationProperties`를 우선 검토한다.
- Bean Validation(`@NotBlank`, `@NotNull`, `@Positive` 등)이 필요하면 그 시점에 `spring-boot-starter-validation` 추가 여부를 판단한다(현재 미도입).
- Secret 성격의 Property는 `toString()`이나 Log에 노출되지 않도록 주의한다.
- **Package 배치 주의**: 이 프로젝트는 Spring Modulith로 Root Package(`team.inreok.getiserver`) 바로 아래 Package를 Application Module로 자동 탐지한다([`modularity.md`](../architecture/modularity.md)). `team.inreok.getiserver.config`처럼 새 Sub-package를 만들면 그 자체로 `config`라는 이름의 Application Module이 생기는 것과 같다. 여러 Module이 공유하는 순수 기술 Configuration이라면 이를 의도한 것인지 먼저 판단하고, 의도한 것이 아니라면 Root Package에 그대로 두거나 실제 소유 Module 내부에 배치한다.

## Configuration Validation / Metadata

- 현재 필수로 검증해야 할 Custom 설정이 없어 시작 시점 Validation을 구성하지 않았다.
- `spring-boot-configuration-processor`(Configuration Metadata 자동 생성)도 등록된 `@ConfigurationProperties`가 없어 추가하지 않았다.
- 두 가지 모두 실제 `@ConfigurationProperties` Class가 추가되는 시점에 함께 재검토한다.

## 설정 테스트

[`ApplicationProfileConfigurationTest`](../../src/test/kotlin/team/inreok/getiserver/ApplicationProfileConfigurationTest.kt)는 `ApplicationContextRunner` + `ConfigDataApplicationContextInitializer`로 전체 Spring Context(Bean, DataSource 등)를 띄우지 않고 Profile별 Property Binding만 검증한다.

```bash
./gradlew test --tests "*ApplicationProfileConfigurationTest*"
```

- `spring.profiles.active=local`일 때 `logging.level.team.inreok.getiserver`가 `DEBUG`로 해석되는지
- Profile을 지정하지 않았을 때 위 값이 설정되지 않는지(운영에 `DEBUG` Logging이 새어 나가지 않는지)

새 Profile Override나 `@ConfigurationProperties`를 추가하면 이 Test 파일에 검증을 추가하거나 유사한 방식의 별도 Test를 작성한다. Property Binding 검증에는 `ApplicationContextRunner`를 우선 사용하고, 여러 Bean의 실제 협력이 검증 대상일 때만 `@SpringBootTest`를 사용한다.

## 설정 추가 기준 요약

- **공통 설정**: 모든 환경에서 동일하고 Secret이 아닌 값만. `application.yaml`에 추가한다.
- **Profile Override**: 특정 환경에서만 달라지는 값. 해당 Profile 파일에 추가하고, 파일이 없다면 이 문서의 표부터 갱신한 뒤 만든다.
- **환경 변수**: 새 변수를 도입하면 이 문서의 환경 변수 표와 `.env.example`을 함께 갱신한다.
- **`@ConfigurationProperties`**: 실제로 그룹화할 설정이 2개 이상 생기거나 Type-safe 접근이 필요해지는 시점에 도입한다. 위 "Package 배치 주의"를 먼저 확인한다.
- **Validation**: 실제 필수 설정이 생기면 함께 도입한다.

## 검증 명령

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew test --tests "*ModularityTest"
./gradlew koverHtmlReport
./gradlew koverXmlReport
./gradlew check
./gradlew clean test build
```

Windows에서는 `.\gradlew.bat`를 사용한다. 도구별 세부 내용은 [`code-quality.md`](./code-quality.md), [`testing.md`](./testing.md)를 따른다.
