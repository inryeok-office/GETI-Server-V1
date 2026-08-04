# 모듈 구조 (Spring Modulith)

GETI-Server는 향후 도메인 기능이 늘어날 때 Package 기반 논리 모듈(Application Module) 경계를 명확하게 유지하기 위해 [Spring Modulith](https://spring.io/projects/spring-modulith)를 사용한다.

Spring Modulith 도입은 즉시 MSA로 분리한다는 뜻이 아니다. 현재 단계에서는 하나의 배포 단위(Modular Monolith) 안에서 모듈 경계를 명시적으로 만들고, 향후 서비스 분리가 실제로 필요할 때 판단할 수 있는 기반을 마련하는 목적이다.

## 확정 Architecture

최상위 Production Package는 반드시 다음 두 종류만 사용한다.

```text
{root-package}.domain
{root-package}.global
```

실제 비즈니스 코드는 `{root-package}.domain.{domain-name}` 아래에 둔다. `{root-package}.global`은 여러 Domain이 실제로 공유하는 기술 기반만 담고 특정 Domain 로직을 두지 않는다. Root Package(`team.inreok.getiserver`) 바로 아래에는 `GetiServerApplication`(진입점), `domain`, `global`만 있다.

## 현재 상태

- Root Package: `team.inreok.getiserver`
- 최신 19개 Table 최소 ERD([`erd.md`](./erd.md) 참고)를 구현한 15개 Domain Package가 `domain` 아래에 있다: `member`, `auth`, `file`, `company`, `job`, `ai`, `recommendation`, `application`, `program`, `portfolio`, `notification`, `inquiry`, `collector`, `operation`, `audit`.
- `global`은 공통 기반 Package(`error`, `web`)를 담는다. `config`/`response`/`security`/`persistence`는 아직 실제 Class가 없어 만들지 않았다(아래 "global Package 책임" 참고).
- 각 Domain Package는 현재 `entity`(+ 필요하면 `entity/type`)와 `repository`만 가진다. Service/Controller/DTO/Exception이 아직 없어 만들지 않았다(아래 "Domain Package 내부 구조" 참고).
- Module 간 FK는 JPA 연관관계가 아니라 `Long`/`UUID` ID Column으로만 참조하므로(Entity 사이에 Java/Kotlin 타입 의존성이 없음), 대부분의 Domain은 서로 컴파일 시점 의존성이 전혀 없다. 유일한 예외는 `collector`(JobCollectionRun)가 `operation`의 공개 Type(`OperationStatus`)을 재사용하는 것이며, Spring Modulith Named Interface로 명시적으로 허용했다(아래 "Domain 간 허용 의존" 참고).
- `./gradlew test --tests "*ModularityTest"`(`modules.verify()`)가 15개 Domain Module + `global` 구성에서 순환 의존성이나 비공개 접근 없이 통과한다.

## Package Tree

`global`(공통 기반) Package 구조는 다음과 같다(PR 12 Global 전역 예외 처리 및 에러 응답 계약 정비 시점 그대로 유지).

```text
team.inreok.getiserver                            (Root Package)
├── GetiServerApplication.kt                      (Production, Application 진입점)
├── GetiServerApplicationTests.kt                 (Test, Application Context Smoke Test)
├── ModularityTest.kt                             (Test, Spring Modulith 구조 검증)
├── ModuleDocumentationTest.kt                     (Test, 문서 생성)
├── PackageArchitectureTest.kt                     (Test, ArchUnit 기반 Package 배치 규칙 검증)
├── DomainApplicationModuleDetectionStrategy.kt    (Test, domain/{name} 단위 Module 탐지 Strategy)
├── ApplicationProfileConfigurationTest.kt         (Test, Profile Config Data Binding 검증)
└── global                                         (Production + Test, Application Module — 공통 기반)
    ├── error
    │   ├── ErrorCode.kt                          (Production, Framework Error Code)
    │   ├── ErrorResponse.kt                       (Production, 공통 오류 응답)
    │   ├── BusinessException.kt                   (Production, Domain 예외 공통 기반)
    │   ├── GlobalExceptionHandler.kt               (Production, 전역 예외 처리)
    │   └── GlobalExceptionHandlerTest.kt           (Test, 오류 응답 Contract 검증)
    └── web
        ├── ApiResponse.kt                         (Production, 공통 성공 응답)
        ├── PageResponse.kt                        (Production, Pagination 응답)
        ├── CorsProperties.kt                       (Production, CORS ConfigurationProperties)
        ├── WebCorsConfig.kt                        (Production, CORS 등록)
        ├── WebPageableConfig.kt                    (Production, Pagination 최대 Size 강제)
        ├── RequestIdFilter.kt                      (Production, requestId 생성/MDC 등록)
        ├── WebTestSupportController.kt             (Test 전용 Controller)
        ├── WebCorsConfigTest.kt                    (Test, CORS 허용/거부 검증)
        └── RequestIdFilterTest.kt                  (Test, requestId 생성/재사용 검증)
```

`domain` 아래에는 15개 Domain Package가 있다. 각 Package는 동일한 형태를 반복한다(예시로 `member`만 전개, Enum이 없는 Domain은 `entity/type`도 없다).

```text
team.inreok.getiserver.domain
├── member
│   ├── entity
│   │   ├── Member.kt
│   │   ├── MemberRole.kt
│   │   ├── MemberRoleId.kt
│   │   └── type
│   │       ├── OAuthProvider.kt
│   │       ├── RoleType.kt
│   │       ├── MemberStatus.kt
│   │       ├── AcademicStatus.kt
│   │       └── DepartmentType.kt
│   └── repository
│       ├── MemberRepository.kt
│       └── MemberRoleRepository.kt
├── auth        (RefreshToken + RefreshTokenRepository)
├── file        (StoredFile + StoredFileRepository — java.io.File와 이름 충돌을 피해 StoredFile 사용)
├── company     (Company + CompanyType/MouStatus + CompanyRepository)
├── job         (Job + PostingType/ApplicationMethod/JobStatus + JobRepository)
├── ai          (JobAiAnalysis + AiStatus + JobAiAnalysisRepository)
├── recommendation (MemberJobPreference(+Id), Recommendation + SuitabilityLevel/ExclusionType + Repository 2종)
├── application (JobApplication + JobApplicationStatus + JobApplicationRepository)
├── program     (Program, ProgramApplication + Enum 3종 + Repository 2종)
├── portfolio   (PortfolioRequest, PortfolioSubmission + Enum 2종 + Repository 2종)
├── notification (Notification + NotificationRepository)
├── inquiry     (Inquiry + InquiryType/InquiryStatus + InquiryRepository)
├── collector   (JobCollectionRun + JobCollectionRunRepository — operation의 OperationStatus를 재사용)
├── operation   (AsyncOperation + OperationStatus/OperationType + AsyncOperationRepository)
└── audit       (AuditLog + AuditLogRepository)
```

각 Domain의 19개 Table 전체 목록, Enum, FK/삭제 정책은 [`erd.md`](./erd.md)를 따른다. `common`/`util`/`controller`/`service`/`repository` 같은 Root 수준의 무제한·포괄 Package는 없다.

모든 Spring Bean은 Main Application Class(`GetiServerApplication`)가 있는 Root Package 아래에 위치해야 한다. `@SpringBootApplication`은 별도 `scanBasePackages` 없이 Application Class가 속한 Package를 기준으로 Component Scan을 수행하므로, Root Package 밖에 Bean을 두면 자동으로 탐지되지 않는다.

## Domain Package 내부 구조

Entity/Repository 기반 PR이므로 현재 각 Domain은 다음 Sub-package만 가진다.

```text
domain/{domain-name}/
├── entity/
│   └── type/       Enum이 있는 Domain만(EntityId 등 복합키 Class는 entity/ 바로 아래)
└── repository/
```

향후 실제 기능이 생기면 같은 Domain 아래에 필요한 만큼만 추가한다.

```text
domain/{domain-name}/
├── entity/
├── repository/
├── service/
│   └── impl/
├── controller/
├── dto/
└── exception/
```

아직 실제 구현이 없는 `service`/`controller`/`dto`/`exception`은 미리 빈 Package로 만들지 않는다. `presentation`/`application`/`infrastructure` 같은 확정되지 않은 추가 Layer로도 전환하지 않는다.

### Service Interface/Impl 분리

Member 도메인(PR 44)부터 `service` Package는 Interface만, 구현 Class는 `service/impl` Package에 둔다. `@Service`/`@Transactional`은 구현 Class에만 붙이고, Controller는 Interface 타입으로 의존을 주입받는다(구체 Class에 직접 의존하지 않음). Interface 이름과 구현 Class 이름은 `{Name}`/`{Name}Impl` 관례를 따른다(예: `MemberService`/`MemberServiceImpl`). 한 Interface에 구현 Class가 하나뿐이면 Spring이 Type 기반으로 자동 주입하므로 `@Qualifier` 등 추가 설정은 필요 없다. Service끼리 협력이 필요하면 서로의 Interface(구체 Impl이 아님)에 의존한다.

Entity는 반드시 담당 Domain의 `entity` Package(복합 ID는 그 아래, Enum은 `entity/type`)에 두고, Repository는 반드시 담당 Domain의 `repository` Package에 둔다. 다음 Package 형태는 사용하지 않는다.

```text
{root-package}.entity / {root-package}.repository / {root-package}.service / {root-package}.controller
{root-package}.domain.entity / {root-package}.domain.repository   (모든 Domain을 합친 Package)
{root-package}.global.entity / {root-package}.global.repository
{root-package}.persistence.entity / infrastructure.persistence.entity
```

## Repository 구현 방식

Repository Interface는 `org.springframework.data.jpa.repository.JpaRepository`를 직접 상속하며, Spring Data가 Proxy로 구현을 자동 생성한다. 손으로 작성한 별도 Adapter Class를 두지 않았다 — Spring Data Proxy를 그대로 위임 호출하기만 하는 Adapter는 실질적인 차이가 없는 반복 코드이기 때문이다(`docs/ai/coding-conventions.md`의 "불필요한 추상화를 만들지 않는다" 원칙). 필요한 기본 조회 Method만 추가했고 추측성 조회 Method를 대량으로 만들지 않았다.

## global Package 책임

`global`은 특정 비즈니스 Domain을 소유하지 않고, 여러 Domain이 실제로 공유하는 기술 기반만 담는다.

| Package 후보 | 용도 | 현재 상태 |
| --- | --- | --- |
| `global.error` | 모든 Domain이 공유하는 오류 계약(Error Code, 오류 응답, 전역 예외 처리, 공통 예외 기반) | 있음([`web-api.md`](../development/web-api.md) 참고) |
| `global.web` | 모든 Domain Controller가 공유하는 HTTP Web 기술 기반(공통 응답, Pagination, CORS, requestId 등) | 있음([`web-api.md`](../development/web-api.md) 참고) |
| `global.openapi` | 모든 Domain Controller가 공유하는 Springdoc OpenAPI 공통 설정(Info, JWT Bearer Security Scheme) | 있음([`docs/ai/openapi-documentation.md`](../ai/openapi-documentation.md) 참고) |
| `global.config` | Spring Framework Configuration Class, `@ConfigurationProperties` | 없음. 실제 Class가 생기는 시점에 만든다 |
| `global.response` | (검토 중) `global.web`과 책임이 겹친다 | 없음. `global.web`에 이미 `ApiResponse`/`PageResponse`가 있어 중복 Package를 만들지 않았다. 실제 필요가 명확해지면 `global.web`을 `global.response`로 재구성할지 별도로 판단한다 |
| `global.security` | Spring Security 설정 | 없음. Spring Security 자체가 아직 도입되지 않았다 |
| `global.persistence` | 여러 Domain이 공유하는 Persistence 기술 요소(예: 공통 Auditing 설정) | 없음. 19개 Table의 Timestamp Column 구성이 균일하지 않아(예: `files`는 `updated_at`이 없음) 공용 BaseEntity를 아직 도입하지 않았다([`erd.md`](./erd.md)의 "시간 타입과 Timestamp 자동화" 참고) |

다음 규칙을 적용한다.

- `global`에 Member, Job, Company 등 비즈니스 Entity를 두지 않는다.
- `global`에 Domain Repository를 두지 않는다.
- `global`에 Domain별 Enum을 두지 않는다.
- `global`이 특정 `domain.{domain-name}` Package를 참조하지 않는다.
- `global`을 비즈니스 코드의 쓰레기통 Package로 사용하지 않는다.

Class가 한두 개뿐이라면 위 Package 아래에 다시 하위 Package를 만들지 않는다.

## 의존성 구성

| 항목 | 값 |
| --- | --- |
| Spring Modulith 버전 | `1.4.1` (Maven Central 최신 Stable/GA) |
| 적용 방식 | `dependencyManagement { imports { mavenBom(...) } }`으로 BOM 적용 |
| 구조 검증(Test) | `testImplementation("org.springframework.modulith:spring-modulith-starter-test")` — `spring-modulith-test`, `spring-modulith-core`, `spring-modulith-docs`, ArchUnit을 Test Classpath에 Transitive로 포함한다 |
| Named Interface Annotation(Main, Compile 전용) | `compileOnly("org.springframework.modulith:spring-modulith-api")` — 아래 "Domain 간 허용 의존" 참고 |
| Production Runtime Dependency | 없음(`compileOnly`는 Runtime Classpath와 최종 Artifact에 포함되지 않는다) |

### 왜 `spring-modulith-api`만 `compileOnly`로 추가했는가

`collector` Domain(`JobCollectionRun`)이 `operation` Domain의 공개 Type(`OperationStatus`)을 참조하려면 Spring Modulith Named Interface(`@org.springframework.modulith.NamedInterface`)를 `domain.operation.entity.type` Package의 `package-info.java`에 선언해야 한다(Kotlin은 Package-level Annotation을 지원하지 않아 이 파일만 Java로 작성했다). `ApplicationModules.of(GetiServerApplication::class.java)`가 `GetiServerApplication`과 같은 Code Source(`src/main` Compile 결과물)에서 Class를 Scan하므로, 이 `package-info.java`는 `src/test`가 아니라 `src/main/java`에 있어야 구조 검증(`ModularityTest`, Test 전용)이 인식한다. 그 결과 Annotation Type 자체는 Main Compile 시점에 필요하지만, `compileOnly`이므로 Runtime Classpath나 최종 Artifact(`bootJar`)에는 포함되지 않는다 — Production 코드가 Spring Modulith에 실제로 의존하는 것은 아니다.

### 왜 1.4.1을 선택했는가 (Spring Boot 버전 호환성)

Spring Modulith 공식 Reference Documentation의 Compatibility Matrix 기준으로 `1.4.x`는 Spring Boot 3.5를 대상으로 컴파일/테스트된다. 이 프로젝트는 Spring Boot 4.1.0(Spring Framework 7.0.8)을 사용하므로 공식적으로 검증된 조합은 아니다.

실제 검증 시점 기준으로 Maven Central과 Spring Milestone 저장소에는 Spring Boot 4.x를 공식 대상으로 하는 Stable(GA) Spring Modulith 릴리스가 없었다(Spring Boot 4.x 대상은 아직 Milestone 단계). 이 프로젝트는 이미 detekt에서도 동일한 상황(Kotlin 2.3.21과 호환되는 Stable 릴리스가 없어 Alpha 버전을 실측 검증 후 채택, [`docs/development/code-quality.md`](../development/code-quality.md) 참고)을 겪었다.

이번에는 반대로 접근했다. Milestone/Pre-release를 앞당겨 쓰는 대신, 실제 Stable GA인 `1.4.1`을 이 프로젝트의 실제 Dependency 조합(Spring Boot 4.1.0, Gradle 9.5.1, Kotlin 2.3.21, Java Toolchain 25)에서 직접 빌드하고 실행해 검증했다.

- `./gradlew dependencies --configuration testRuntimeClasspath`로 확인한 결과, Gradle의 Spring Dependency Management가 `spring-modulith-core`/`spring-modulith-test`가 요구하는 `spring-core`/`spring-context`/`spring-test`/`spring-tx` `6.2.8`을 이 프로젝트가 관리하는 `7.0.8`로 강제 정렬했다(`6.2.8 -> 7.0.8`).
- `ApplicationModules.of(GetiServerApplication::class.java)` 생성, `modules.verify()`, `Documenter(modules).writeModulesAsPlantUml().writeModuleCanvases()` 실행을 모두 실제로 수행해 정상 동작(예외 없음, 정상적인 PlantUML 출력)을 확인했다.
- Spring Modulith의 핵심 기능(Package 스캔, ArchUnit 기반 구조 검증)은 ArchUnit이 Bytecode를 직접 분석하는 방식이라 Spring Framework Runtime 버전 자체에 크게 의존하지 않는다.

이 판단은 실측에 근거한 것이며, 공식적으로 보증된 조합은 아니다. Spring Modulith가 Spring Boot 4.1을 공식 지원하는 Stable 버전을 배포하면 재검토한다.

## 모듈 탐지 전략

Spring Modulith 기본 전략(Root Package의 Direct Subpackage를 Module로 인식)은 `domain`/`global`만 Root Package의 Direct Subpackage이므로, 기본 전략을 그대로 쓰면 `domain` 전체가 하나의 거대한 Module로 잡히고 `domain.member`, `domain.job` 같은 개별 Domain은 Module로 인식되지 않는다.

`domain/{domain-name}` 단위로 Module을 인식시키기 위해 Custom `ApplicationModuleDetectionStrategy`를 추가했다.

```text
team.inreok.getiserver.DomainApplicationModuleDetectionStrategy   (src/test 전용)
```

`domain`의 Direct Subpackage(예: `domain.member`)는 개별 Module로, 그 외 Root Direct Subpackage(`global`)는 그대로 하나의 Module로 인식한다. `spring.modulith.detection-strategy`(`src/test/resources/application.yaml`)에 이 Class의 Fully Qualified Name을 지정했다. 이 Property는 `BeanUtils.instantiateClass`가 Public 기본 생성자로 직접 생성하는 방식이라 Spring Bean이 아니며, Spring Modulith가 실제로 사용되는 곳(현재는 `ModularityTest`/`ModuleDocumentationTest`뿐)에만 영향을 준다. `src/main`에는 이 설정이 없어 Production Runtime 동작에는 영향이 없다(Spring Modulith Production Dependency 자체가 없다).

`ApplicationModules.of(GetiServerApplication::class.java)`로 실측한 결과 Module은 총 16개(`global` + 15개 `domain.{domain-name}`)다.

## Domain 간 허용 의존

Module 간 순환 의존성을 만들지 않고, 다른 Module의 내부 구현(비공개 Package)을 직접 참조하지 않는 것이 기본 원칙이다. 다른 Module에 공개해야 하는 타입만 공개 API 또는 Spring Modulith Named Interface로 노출한다.

`collector`(`JobCollectionRun.status`)가 `operation`의 `OperationStatus` Enum을 재사용하는 것이 첫 사례다. `job_collection_runs.status`와 `async_operations.status`가 ERD상 동일한 `operation_status` 값 집합을 쓰기 때문에([`erd.md`](./erd.md)의 "collector가 operation의 Enum을 재사용하는 이유" 참고), `domain.operation.entity.type` Package에 `@NamedInterface("type")`를 선언해 이 Package만 다른 Domain에 공개했다(`src/main/java/team/inreok/getiserver/domain/operation/entity/type/package-info.java`). `operation`의 다른 부분(`entity`의 `AsyncOperation`, `repository`)은 여전히 비공개다.

두 번째 사례는 `collector`(`CollectorExecutionServiceImpl`)가 `job`이 공개한 `domain.job.upsert` Package(`CollectedJobUpsertUseCase`/`CollectedJobUpsertCommand`/`CollectedJobUpsertResult`, 각 Type에 `@NamedInterface` 직접 선언, `CompanyQuery`와 같은 방식)를 통해 외부 수집 공고를 반영하는 것이다(Issue #62). Collector는 이 Package를 통해서만 `job`에 접근하고, `Job` Entity나 `JobRepository`는 참조하지 않는다. `collector`의 새 CollectionRun 실행 상태(`CollectionRunStatus`)는 `operation.OperationStatus`와 값 집합이 달라(`PARTIAL_SUCCESS`/`CANCELED` 포함) 재사용하지 않고 `domain.collector.entity.type`에 새로 정의했다.

세 번째 사례는 같은 `CollectorExecutionServiceImpl`이 `company`가 공개한 `domain.company.external` Package(`CompanyExternalImportUseCase`/`CompanyExternalImportCommand`/`CompanyExternalImportResult`)를 통해 외부에서 수집한 기업명으로 기존 기업을 찾거나 최소 정보로 새로 만드는 것이다(Issue #62). 정규화된 공고는 기업명(String)만 제공하는데 `jobs.company_id`가 `NOT NULL`이라 Collector가 companyId를 직접 해석해야 했고, `CompanyQuery`는 ID 기준 조회만 공개해(Issue #56) 이 용도에 맞지 않았다. `company` 도메인이 소유한 (name, type) 미삭제 Unique 판정(`uk_companies_name_type_active`)을 그대로 재사용해 별도 중복 정책을 만들지 않았다. `CompanyRepository`/`Company` Entity는 여전히 비공개다.

새로운 Domain 간 의존이 필요해지면 이 방식(Named Interface로 필요한 Package만 명시적으로 공개)을 그대로 따르고, 이 문서에 근거를 추가한다.

## 만들지 않는 Package

다음은 이 저장소에서 만들지 않는다. 편의를 위해 예외를 두지 않는다.

```text
Root 수준의 controller / service / repository / entity / dto (기술 Layer를 비즈니스 Module처럼 사용)
domain.entity / domain.repository (모든 Domain을 합친 Package)
무제한 common / global / util / shared / core (책임이 불명확한 포괄 Package)
특정 Domain 전용 코드를 담은 공용 Package
아직 실제 Class가 없는 빈 Domain 또는 Layer Package
아직 필요하지 않은 미래 기능을 위한 Marker/Placeholder Class
다른 Module의 내부 구현을 직접 참조하는 코드(Named Interface로 공개하지 않은 Package)
presentation/application/infrastructure 같은 확정되지 않은 4-Layer 구조로의 임의 전환
```

## Test Package 원칙

- Test Class는 검증 대상 Production Class와 같은 Package에 둔다.
- `ModularityTest`/`ModuleDocumentationTest`/`PackageArchitectureTest`/`DomainApplicationModuleDetectionStrategy`처럼 특정 Production Class를 검증하지 않는 Architecture Test/지원 Class도 별도 Package로 분리하지 않고 Root Test Package(`team.inreok.getiserver`)에 둔다.
- Domain Package가 추가되면 그 Package의 Test도 같은 Domain Package 안에 둔다(`domain.{domain-name}`). 모든 Test를 하나의 `test`나 `integration` Package로 모으지 않는다.
- 실제 공유 Fixture가 필요해지기 전에는 Test Support Package를 만들지 않는다.

## 구조 검증

```bash
./gradlew test --tests "*ModularityTest"
./gradlew test --tests "*PackageArchitectureTest"
```

`ModularityTest`(`src/test/kotlin/team/inreok/getiserver/ModularityTest.kt`)는 `ApplicationModules.of(GetiServerApplication::class.java)`를 생성하고 다음을 검증한다.

- `modules.verify()`: Module 간 순환 의존성, 다른 Module의 내부 구현(Non-public) 접근, 명시적으로 선언한 허용 Dependency 위반이 없는지.
- 실제 탐지된 Module 16개(`global` + 15개 `domain.{domain-name}`)의 이름이 기대한 값과 정확히 일치하는지.

`PackageArchitectureTest`(`src/test/kotlin/team/inreok/getiserver/PackageArchitectureTest.kt`)는 ArchUnit(`spring-modulith-starter-test`가 Test Classpath에 이미 Transitive로 포함)으로 `ModularityTest`가 다루지 않는 "어느 Package에 있어야 하는가"라는 배치 규칙을 검증한다.

- 모든 `@Entity` Class가 `domain.{domain-name}.entity` 아래에 있는지.
- 모든 `JpaRepository` 구현 Interface가 `domain.{domain-name}.repository` 아래에 있는지.
- `global`에 `@Entity`나 Repository가 없는지.
- Root 전역 `entity`/`repository` Package, `domain.entity`/`domain.repository`(모든 Domain을 합친 Package)에 Class가 없는지.
- `global`이 `domain`에 의존하지 않는지.

두 Test 모두 일반 `./gradlew test` 실행에 포함된다.

## 모듈 구조 문서 생성

```bash
./gradlew test --tests "*ModuleDocumentationTest"
```

`ModuleDocumentationTest`는 `Documenter`로 PlantUML Component Diagram과 Module Canvas를 생성한다. 출력 경로는 실제 실행 결과로 확인했다.

```text
build/spring-modulith-docs/components.puml            전체 Module 관계 PlantUML
build/spring-modulith-docs/module-global.adoc          global Module Canvas
build/spring-modulith-docs/module-domain.member.adoc   domain.member Module Canvas
...                                                    (domain.{domain-name}마다 1개, 총 15개)
```

이 Test는 일반 `test` 실행에 포함되며, 결과물은 `build/` 아래에만 생성되고 Git에 Commit하지 않는다(`.gitignore`의 기존 `build/` 규칙으로 충분하다).

## Runtime Verification

`spring.modulith.runtime.verification-enabled=true`를 운영 Application 시작 시 강제하지 않는다. 구조 검증은 현재 Test 단계(`ModularityTest`)에서만 수행한다. CI에서의 `integrationTest` 실행과 마찬가지로 별도 PR에서 역할 분리를 재검토한다.

## Module Integration Test

`@ApplicationModuleTest`는 특정 Module과 그 Module이 선언한 협력 Module만 골라 Spring Context를 구성하는 Slice Test다. 일반 `@SpringBootTest`가 전체 Context를 올리는 것과 달리, 검증 대상 Module의 경계 안에서만 통합 동작을 확인할 때 사용한다.

15개 Domain Module이 있지만 아직 각 Module 내부에 Spring Bean 협력(Service 등)이 없고, Persistence 검증은 여러 Module의 Table을 함께 다루는 `CoreDomainSchemaIntegrationTest`(`src/integrationTest`)가 담당하고 있어 `@ApplicationModuleTest`를 사용하는 빈 Test를 만들지 않았다. 실제 Module 내부에 Spring Bean 협력이 생기는 시점에 해당 Module Package 안에 Integration Test를 추가한다.

## 아직 도입하지 않은 것

다음은 이번 PR 범위가 아니며, Persistence/Event Infrastructure/운영 환경이 구성된 이후 별도로 검토한다.

```text
spring-modulith-starter-jdbc / jpa / mongodb / neo4j / insight
spring-modulith-actuator
spring-modulith-observability
spring-modulith-events-* (Event Publication Registry)
Runtime Verification 강제 활성화
Module Integration Test (@ApplicationModuleTest)
jMolecules
```
