# Configuration 및 Profile

GETI-Server는 공통 설정과 환경별 설정을 분리하고, Secret을 저장소에 포함하지 않으면서 개발자와 AI Agent가 일관되게 설정을 추가할 수 있도록 다음 기준을 사용한다.

## Profile 구성

| Profile | 설정 파일 | 책임 |
| --- | --- | --- |
| 공통 | `src/main/resources/application.yaml` | 모든 환경에서 동일하고 Secret이 아닌 설정. 현재는 `spring.application.name`만 있다. |
| `local` | `src/main/resources/application-local.yaml` | 개발자 로컬 실행에 필요한 안전한 Override. 현재는 애플리케이션 Package(`team.inreok.getiserver`) Logging 수준을 `DEBUG`로 높이는 설정만 있다. |
| `test` | (파일 없음) | 테스트는 `spring-boot-starter-data-jpa-test`/`webmvc-test`가 제공하는 `com.h2database:h2`(`testRuntimeOnly`)를 Spring Boot가 자동으로 감지해 In-memory DB로 JPA Context를 구성한다. 현재 Override가 필요한 설정이 없어 `application-test.yaml`을 만들지 않았다. |
| `prod` | (파일 없음) | 현재 운영 환경에 필요한 실제 설정이 없다. 빈 파일을 미리 만들지 않았다. 운영 전용 값이 실제로 생기면 이 표와 함께 `application-prod.yaml`을 추가한다. |

파일이 없는 Profile은 공통 설정만 적용된다. 이는 의도된 상태이며 누락이 아니다(예: `prod`가 별도 Override 없이 공통 설정을 그대로 쓰는 것은 `local`의 `DEBUG` Logging이 운영에 새어 나가지 않는다는 뜻이기도 하다).

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

`APP_VERSION`/`APP_GIT_SHA`/`APP_BUILD_TIME`/`APP_ENVIRONMENT`는 `/actuator/info`의 `deployment` Field(`DeploymentInfoContributor`)와 CD의 배포 SHA 검증에 쓰인다. CD가 Docker Build/Runtime 시점에 실제 값을 주입하며([`cd.md`](./cd.md) 참고), 로컬 개발자가 직접 설정할 필요가 없어(안전한 기본값으로 기동됨) `.env.example`에는 포함하지 않았다.

PostgreSQL/Redis 연결 환경 변수(`DATABASE_URL` 등)는 이 표에 중복하지 않고 [`persistence.md`](./persistence.md)에서 관리한다.

`SERVER_PORT`(→ `server.port`)처럼 Spring Boot가 기본으로 지원하는 환경 변수는 [Relaxed Binding](https://docs.spring.io/spring-boot/reference/features/external-config.html)으로 이미 동작하므로 `application.yaml`에 `${SERVER_PORT:8080}` 형태로 다시 선언하지 않았다. 새 환경 변수가 필요해지면 이 표와 `.env.example`을 함께 갱신한다.

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
