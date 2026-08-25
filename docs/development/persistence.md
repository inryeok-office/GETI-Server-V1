# Persistence 및 데이터 접근 기반

GETI-Server는 PostgreSQL(JPA/Hibernate + Flyway)과 Redis(Lettuce)에 연결하기 위한 공통 기반을 구성했다. 이번 범위는 접속/Migration/Test 기반이며, 실제 GETI Domain Entity, Repository, 비즈니스 Migration, 조회 최적화(QueryDSL 등), Cache 전략은 포함하지 않는다.

## 적용 Dependency

`./gradlew dependencies`로 실제 해석된 Version 기준이다(Spring Boot 4.1.0 BOM이 관리).

| Dependency | Scope | Version(해석됨) | 역할 |
| --- | --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-data-jpa` | `implementation` | `4.1.0` | Spring Data JPA, Hibernate ORM |
| `org.springframework.boot:spring-boot-starter-data-redis` | `implementation` | `4.1.0` | Spring Data Redis, Lettuce Client |
| `org.springframework.boot:spring-boot-flyway` | `implementation` | `4.1.0` | Flyway Spring Boot Auto Configuration |
| `org.flywaydb:flyway-core` | `implementation` | `12.4.0` | Flyway 엔진 |
| `org.flywaydb:flyway-database-postgresql` | `implementation` | `12.4.0` | Flyway PostgreSQL Dialect 지원 |
| `org.postgresql:postgresql` | `runtimeOnly` | `42.7.11` | PostgreSQL JDBC Driver |
| `com.zaxxer:HikariCP` | (전이) | `7.0.2` | Connection Pool (Spring Boot 기본) |
| `io.lettuce:lettuce-core` | (전이) | `7.5.2.RELEASE` | Redis Client (Spring Boot 기본) |
| `org.testcontainers:testcontainers-postgresql` | `integrationTestImplementation` | `2.0.5` | PostgreSQL Integration Test Container |
| `com.redis:testcontainers-redis` | `integrationTestImplementation` | `2.2.4` | Redis Integration Test Container |
| `org.springframework.boot:spring-boot-testcontainers` | `integrationTestImplementation` | `4.1.0` | `@ServiceConnection` 등 Spring Boot ↔ Testcontainers 연동 |
| `org.testcontainers:testcontainers-junit-jupiter` | `integrationTestImplementation` | `2.0.5` | `@Testcontainers` JUnit 5 확장 |
| `org.springframework.boot:spring-boot-starter-data-redis-test` | `integrationTestImplementation` | `4.1.0` | `@DataRedisTest` Slice Test |

`io.spring.dependency-management` Plugin은 `spring-boot-dependencies` POM 안의 중첩 BOM Import(`testcontainers-bom`)를 항상 전파하지 않는다(실측 확인). 그래서 `build.gradle.kts`의 `dependencyManagement.imports`에 `org.testcontainers:testcontainers-bom:2.0.5`를 직접 추가했다. Testcontainers 2.x부터 Maven Artifact 이름이 `testcontainers-` 접두사를 갖도록 바뀌었다(예: 과거 `org.testcontainers:postgresql` → 현재 `org.testcontainers:testcontainers-postgresql`).

## PostgreSQL 연결 설정

| 환경 변수 | Spring Property | 필수 여부 | 사용 Profile | Secret 여부 | 기본값 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `DATABASE_URL` | `spring.datasource.url` | `local`은 선택 / `prod`는 필수 | 전체 | 아니오(URL 자체는 Secret 아님) | `local`: `jdbc:postgresql://localhost:5432/geti` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `spring.datasource.username` | `local`은 선택 / `prod`는 필수 | 전체 | 아니오 | `local`: `geti` | PostgreSQL 접속 User |
| `DATABASE_PASSWORD` | `spring.datasource.password` | `local`은 선택 / `prod`는 필수 | 전체 | **예** | `local`: `geti-local-only`(Local 전용 값) | PostgreSQL 접속 Password |

`local` Profile(`application-local.yaml`)의 기본값은 [`docker.md`](./docker.md)의 `compose.yaml` PostgreSQL 기본값과 동일해, Docker Compose 인프라를 그대로 켜면 값을 지정하지 않아도 연결된다. `prod` Profile(`application-prod.yaml`)은 기본값이 없어 값을 지정하지 않으면 기동 시점에 즉시 실패한다(Fail-Fast).

## Redis 연결 설정

| 환경 변수 | Spring Property | 필수 여부 | 사용 Profile | Secret 여부 | 기본값 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `REDIS_HOST` | `spring.data.redis.host` | `local`은 선택 / `prod`는 필수 | 전체 | 아니오 | `local`: `localhost` | Redis Host |
| `REDIS_PORT` | `spring.data.redis.port` | `local`은 선택 / `prod`는 필수 | 전체 | 아니오 | `local`: `6379` | Redis Port |
| `REDIS_PASSWORD` | `spring.data.redis.password` | 선택(Local Redis는 인증 미사용) | 전체 | **예** | 없음(빈 값) | Redis 인증 Password. `prod`도 값이 없으면 빈 문자열로 해석되어 인증 없는 Redis에도 연결은 시도한다 |

Redis Client는 Spring Boot 기본값인 Lettuce를 그대로 사용하며 별도 Client Library를 추가하지 않았다. Application 코드에서 직접 Redis에 접근해야 하면 우선 `StringRedisTemplate`(Spring Boot가 기본 제공하는 Bean)을 사용한다. Java 기본 직렬화를 사용하는 `RedisTemplate<String, Any>`류의 범용 Bean을 미리 만들지 않았다 — 실제로 객체를 저장해야 하는 요구가 생기는 시점에 어떤 직렬화 방식(JSON 등)을 쓸지 그 PR에서 판단한다.

## Flyway (Migration)

- `spring.flyway.clean-disabled=true`를 `application.yaml`(공통 설정)에 명시했다. 모든 환경에서 `flyway clean`(전체 Schema 삭제) 실행을 차단한다.
- `src/main/resources/db/migration/V2__create_core_domain_schema.sql`이 실제 GETI Domain의 첫 Migration이다. 최신 최소 19개 Table ERD([`erd.md`](../architecture/erd.md) 참고)를 Table/PK/FK/UNIQUE/CHECK/Index까지 반영한다. `V1`이 아니라 `V2`로 시작하는 이유도 같은 문서와 파일 상단 주석에 있다(`integrationTest` 전용 `V1__create_persistence_probe.sql`과의 classpath Version 충돌 회피).
- `src/integrationTest/resources/db/migration/V1__create_persistence_probe.sql`은 Persistence Integration Test 전용 Migration이며(`persistence_probe` 임시 Table), 실제 Domain Migration이 아니다. `integrationTest` Source Set에만 존재해 Production Classpath에 포함되지 않는다. `integrationTest` 실행 시에는 main과 integrationTest의 `db/migration` Resource가 같은 classpath 위치로 합쳐지므로, `persistence_probe` Table도 Domain Schema와 함께 같은 Testcontainers DB에 생성된다.
- Migration 파일은 한 번 병합되면 내용을 수정하지 않고 새 버전으로 추가한다(Flyway의 Checksum 검증 원칙). `V2__create_core_domain_schema.sql`이 병합된 이후에는 이 원칙이 그대로 적용된다.

## JPA / Hibernate 정책

- `spring.jpa.hibernate.ddl-auto=validate`를 공통 설정에 명시했다. `create`/`create-drop`/`update`는 어떤 Profile에서도 사용하지 않는다. Schema 변경은 Flyway Migration만으로 수행한다.
- `spring.jpa.open-in-view=false`를 공통 설정에 명시했다(Spring Boot 기본값은 `true`). Transaction 경계 밖에서 Lazy Loading이 일어나는 것을 막는다.
- 19개 Table에 대응하는 Entity가 모두 `ddl-auto=validate` 대상이다. `CoreDomainSchemaIntegrationTest`(`src/integrationTest`)가 Flyway로 만든 실제 Schema를 대상으로 Validate·저장·조회·제약조건 동작을 확인한다. `PostgresPersistenceIntegrationTest`는 여전히 `persistence_probe` Table 하나만으로 Flyway+Hibernate 연동 자체를 검증하는 별개의 기술 Smoke Test로 유지된다.
- `src/test`의 `GetiServerApplicationTests`(`@SpringBootTest` Application Context Smoke Test)는 PostgreSQL 전용 Migration(`jsonb`, Partial Unique Index 등)을 H2로 검증하지 않기 위해 그 Test Class에서만 `spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=none`으로 재정의한다. 실제 Schema/Migration 검증은 Testcontainers 기반 `integrationTest`가 담당한다.
- Entity/Repository는 각 Domain Module Package 안에 둔다. Root Package 바로 아래 공용 `entity`/`repository` Package를 만들지 않는다([`modularity.md`](../architecture/modularity.md) 참고).
- Controller에서 Transaction을 시작하지 않는다. `@Transactional`은 Service(또는 그에 준하는 Application 계층) 경계에서 사용한다.

## Integration Test (Testcontainers)

`test`/`check`/`build`는 Docker 없이 실행된다. Docker(Testcontainers)가 필요한 Persistence Integration Test는 별도 Gradle Source Set/Task인 `integrationTest`로 분리했고, `check`/`build`에서 의도적으로 제외했다.

```text
src/integrationTest/kotlin/team/inreok/getiserver/persistence/
├── PersistenceProbeEntity.kt          Test 전용 Entity
├── PersistenceProbeRepository.kt      Test 전용 Repository
├── PostgresPersistenceIntegrationTest.kt
└── RedisPersistenceIntegrationTest.kt

src/integrationTest/resources/db/migration/
└── V1__create_persistence_probe.sql   Test 전용 Migration
```

`team.inreok.getiserver.persistence`는 `integrationTest` Source Set에만 존재하는 Test 지원 Package다. `main` Classpath에 없으므로 `ApplicationModules.of(GetiServerApplication::class.java)`(Spring Modulith) 탐지 대상이 아니며, Application Module로 세지 않는다.

### PostgreSQL Integration Test

`PostgresPersistenceIntegrationTest`는 `org.testcontainers.postgresql.PostgreSQLContainer`(`postgres:18.4-alpine`)를 `@ServiceConnection`으로 띄우고, `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ImportAutoConfiguration(FlywayAutoConfiguration::class)`로 Flyway Migration이 실제로 실행된 뒤 Hibernate가 그 Schema를 `validate`하고 저장/조회가 되는지 확인한다.

`@DataJpaTest`는 기본적으로 `FlywayAutoConfiguration`을 Import하지 않는다(Slice Test가 최소 구성만 올리기 때문). Flyway 동작까지 검증하려면 `@ImportAutoConfiguration(FlywayAutoConfiguration::class)`를 명시해야 한다는 것을 실제 실행 결과(Schema Validation 실패 재현)로 확인했다.

### Redis Integration Test

`RedisPersistenceIntegrationTest`는 `com.redis.testcontainers.RedisContainer`(`redis:8.8.1-alpine`)를 `@ServiceConnection`으로 띄우고, `@DataRedisTest`로 주입한 `StringRedisTemplate`의 SET/GET/DELETE 동작을 확인한다.

### 실행 명령

```bash
./gradlew test              # Docker 불필요. Unit/Slice Test만 실행
./gradlew integrationTest   # Docker 필요. PostgreSQL/Redis Testcontainers 실행
./gradlew check             # spotlessCheck + detekt + test (integrationTest 제외)
./gradlew clean test build  # 위와 동일하게 integrationTest는 제외
```

`integrationTest`가 `check`/`build`에서 제외된 이유는 두 가지다.

1. Kover(Coverage 측정 Plugin)가 기본으로 등록된 모든 `Test` Task를 계측 대상에 포함시켜 `check`가 암묵적으로 `integrationTest`에 의존하게 된다. `build.gradle.kts`의 `kover { currentProject { instrumentation { disabledForTestTasks.add("integrationTest") } } }`로 명시적으로 제외했다.
2. detekt는 새 Source Set을 자동 인식해 `detektIntegrationTest` Task를 만든다. 이 Task 자체는 Docker가 필요 없어 `check`에 포함되지만(정적 분석만 수행), Test **실행**(`integrationTest` Task)은 Docker가 필요하므로 `check`/`build`가 의존하지 않도록 별도로 유지한다.

## Object Storage (S3 Compatible)

파일 바이너리는 DB가 아니라 Object Storage에 저장한다. **local은 `compose.yaml`의 MinIO, 운영은 AWS S3**를 쓰지만 Adapter 구현은 하나이며 Endpoint와 Path Style 설정으로만 갈린다(File 도메인, Issue #85).

```text
domain.file.service    →  FileStoragePort        (Interface, SDK Type 없음)
                       →  S3FileStorageAdapter   (AWS SDK v2가 등장하는 유일한 곳)
                       →  MinIO / AWS S3
```

Application 계층은 `S3Client`·`PutObjectRequest` 같은 SDK Type을 직접 참조하지 않는다. 다른 Domain은 `FileStoragePort`조차 보지 못하고 `FileLinkPort`/`FileUrlPort` 같은 공개 계약만 사용한다 — Storage Key와 Bucket을 아는 것 자체가 File 도메인의 책임이다.

설정은 `app.file.storage.*`다.

| Profile | endpoint | public-endpoint | path-style | 자격증명 |
| --- | --- | --- | --- | --- |
| `local`(Host에서 `bootRun`) | `http://localhost:9000`(MinIO) | 비움 → `endpoint` 그대로 사용 | `true` | `access-key`/`secret-key` 명시 → Static |
| `local`/`develop`(`docker compose --profile app`) | `http://minio:9000`(Compose 내부 DNS) | `http://localhost:${MINIO_API_PORT:-9000}` | `true` | `access-key`/`secret-key` 명시 → Static |
| 운영(`prod`) | 비움(AWS 기본) | 비움 → `endpoint` 그대로 사용(AWS 기본) | `false` | **선언하지 않음** → `DefaultCredentialsProvider`(EC2 IAM Role) |

`endpoint`는 `S3Client`(서버가 직접 호출하는 PutObject/GetObject/DeleteObject)가 쓰고, `public-endpoint`는 `S3Presigner`(Presigned URL 서명)가 쓴다. 둘을 분리하는 이유는 Presigned URL을 실제로 여는 주체가 서버가 아니라 **외부 Client**(Browser, GETI-Client-V1의 `next dev` 등)이기 때문이다. 앱을 `docker compose --profile app`으로 Container 안에서 실행하면 `endpoint=http://minio:9000`인데, `minio`는 Compose 내부 DNS 이름이라 Container 밖의 Client는 이 이름을 해석하지 못한다 — `public-endpoint`가 없던 이전에는 Presigned URL에도 그대로 `minio:9000`이 찍혀 다운로드/이미지 표시가 전부 실패했다. Host에서 `bootRun`으로 실행할 때는 `endpoint` 자체가 이미 `localhost:9000`이라 이 문제가 없다.

`docker compose --profile app`으로 배포하는 CD의 `develop` 환경은 아직 `public-endpoint`가 실제로 외부에서 닿는 값을 가리키지 않는다 — `compose.yaml`의 `minio` Service가 MinIO API Port(9000)를 `127.0.0.1`에만 Bind해 서버 밖에서는 애초에 접근할 수 없기 때문이다(로컬 1인 개발 환경 전용 기본값). EC2 등 실제 배포 환경에서 Presigned URL을 외부 Client가 열 수 있게 하려면 MinIO(또는 대체 Object Storage)를 공개적으로 도달 가능하게 만드는 별도 인프라 결정이 필요하다(DECISION_REQUIRED, EC2 Metadata hop limit과 같은 성격의 코드로 해결할 수 없는 선행 조건, Issue #255 참고).

운영에서 자격증명을 선언하지 않는 것이 핵심이다. 장기 Access Key를 서버·Secret·Compose 어디에도 두지 않는다. 다만 앱이 **Container 안**에서 돌기 때문에 **EC2 Metadata hop limit이 2 이상이어야** IMDS에 닿는다. 기본값 1이면 자격증명 획득에 실패해 모든 S3 호출이 실패하며, 이것은 코드로 해결할 수 없는 인프라 선행 조건이다([`docs/file/file-domain-plan.md`](../file/file-domain-plan.md) §14.3).

`auto-create-bucket`은 local 전용이다. 운영에서 앱이 Bucket을 만들면 Block Public Access와 암호화 설정이 빠진 채 생성될 수 있어 기본값이 `false`다.

### Storage Integration Test

실제 Storage 동작은 `src/integrationTest`의 `FileStorageIntegrationTest`가 Testcontainers MinIO로 검증한다(`org.testcontainers:testcontainers-minio`, Version은 기존 `testcontainers-bom`이 관리). Unit Test는 `InMemoryFileStoragePort` Fake를 쓰므로 SDK 설정(`endpointOverride`, `forcePathStyle`)과 Presigned URL 서명은 여기서만 확인된다.

## Docker Compose 연계

`compose.yaml`의 `app` Service(`--profile app`)에 Container 내부 접속 정보를 환경 변수로 이미 구성해 두었다(Host `localhost` 대신 Compose Service 이름 `postgres`/`redis` 사용). `docker compose --profile app up -d --build`로 전체 Container 환경을 실행하면 Spring Boot Container가 별도 설정 없이 PostgreSQL/Redis에 연결된다. 실제로 실행해 Hikari Connection Pool 생성, Flyway Schema History 생성, Hibernate `EntityManagerFactory` 초기화, Tomcat 정상 기동까지 확인했다(Domain Migration이 없어 "No migrations found" Warning은 예상된 정상 상태다).

인프라(Docker Compose) 자체에 대한 내용(Port, Health Check, 초기화 명령 등)은 [`docker.md`](./docker.md)를 따른다.

## 검증 명령

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew integrationTest      # Docker 필요
./gradlew test --tests "*ModularityTest"
./gradlew test --tests "*ApplicationProfileConfigurationTest*"
./gradlew koverHtmlReport
./gradlew koverXmlReport
./gradlew check
./gradlew clean test build
```

Windows에서는 `.\gradlew.bat`를 사용한다.

## 이번 범위가 아닌 것

- Use Case Service, Controller 등 Application/Presentation Layer([`erd.md`](../architecture/erd.md)의 "이번 범위와 제외 범위" 참고)
- QueryDSL, Cache 전략(예: `@Cacheable`), Redis 기반 Session/Rate Limit 등 구체적인 활용 코드
- MinIO, Kafka, Elasticsearch 연동
- Spring Security, 인증/인가, 실제 OAuth Flow
- CI/CD에서의 `integrationTest` 실행(별도 CI 도입 PR에서 역할 분리를 재검토한다. 로컬에서는 개발자가 필요할 때 직접 `./gradlew integrationTest`를 실행한다)
