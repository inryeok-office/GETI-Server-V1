# File 도메인 구현 명세 (PR A / PR B)

[`file_domain_development.md`](./file_domain_development.md) 요구사항 문서를 저장소의 실제 상태(이미 병합된 V2~V16 Migration, Spring Modulith 경계, 기존 Job/Program/Application/Notification 구현 패턴)에 맞춰 확정한 구현 명세다.

지시서 §43이 "작업 전 확인"하라고 남긴 20개 항목은 모두 아래 §1에서 코드로 확인했다. §44의 미확정 정책 12개 중 이번 범위에 걸리는 것은 §17에 `DECISION_REQUIRED`로 남긴다.

사용자 요청으로 **AWS S3 Bucket 연동**을 전제로 하며, S3 연동에 필요한 코드·설정·인프라 선행 조건을 §7과 §14에 모두 정리했다.

이 문서는 지시서 §41의 **Phase 1~4**를 다루고 **PR A / PR B 두 개**로 나눈다. Phase 5(Cleanup Scheduler)와 Phase 6(ZIP Archive)은 §18에 방향만 적는다.

---

## 1. 작업 전 확인 결과 (지시서 §43)

| # | 확인 항목 | 실제 상태 |
| --- | --- | --- |
| 1 | 기존 File Entity 존재 여부 | **존재**. `domain/file/entity/StoredFile.kt`(V2 Schema 그대로 매핑) + **비어 있는** `StoredFileRepository`. Service·Controller·DTO·Enum·Exception은 **전혀 없음** |
| 2 | 기존 S3/MinIO Config | **없음**. `@ConfigurationProperties`도, Client Bean도, Adapter도 없음 |
| 3 | AWS SDK 의존성 | **없음**. `build.gradle.kts`에 `software.amazon.awssdk`·`io.minio` 모두 없음 |
| 4 | 기존 Storage Adapter | **없음** |
| 5 | 환경변수 Naming | Storage 관련 환경변수 **없음**. MinIO Container 자격증명(`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`)만 `compose.yaml`에 존재하며 **App에 주입되지 않음** |
| 6 | 최신 Flyway Migration 번호 | **V16**(`V16__align_notifications_for_in_app_api.sql`) → 이번 신규는 **V17** |
| 7 | Member ID 참조 방식 | Domain 경계를 넘는 FK는 JPA 연관관계 없이 평범한 `Long` Column(`docs/architecture/erd.md` "JPA 연관관계 전략"). `files.uploader_member_id`도 동일 |
| 8 | Job `fileIds` 구현 상태 | **미구현**. `JobCreateRequest.kt:16` 주석이 "이번 범위에서 제외" 명시 |
| 9 | Program `fileIds` 구현 상태 | **미구현**. `ProgramCreateRequest.kt:13`이 "File 도메인에 공개 Use Case가 아직 없어" 제외했다고 명시 |
| 10 | Application `fileIds` 구현 상태 | DTO Field만 존재(`ApplicationAnswer.kt:23`, `ProgramApplicationAnswer.kt:22`). **검증·연결 로직 없음**. `ProgramController.kt:106`이 "`fileIds` 소유권 검증 미구현" 명시 |
| 11 | Inquiry `fileIds` 구현 상태 | **미구현**. `inquiries` Table에 파일 Column 자체가 없음 → 첨부는 `files.owner_type='INQUIRY'`로만 표현 가능 |
| 12 | Portfolio 현재 범위 | Entity·Repository만 존재(`PortfolioRequest`, `PortfolioSubmission`). Service·Controller 없음 |
| 13 | 다른 도메인의 File Entity 직접 참조 | **없음**. `Member.profileImageFileId`, `Company.logoFileId`는 `Long?` Column일 뿐 Type 의존 없음 |
| 14 | 현재 일괄 다운로드 구현 여부 | **없음**. `async_operations.result_file_id` Column만 존재(사용처 없음) |
| 15 | Scheduler/ShedLock 사용 패턴 | 평범한 `@Scheduled`만 3개(collector 2, search 1). **ShedLock 미도입** — 지시서 §19의 "현재 Scheduler에서 ShedLock을 사용하므로"는 **사실이 아니다**. `CollectorScheduler.kt:18` 주석이 미도입을 명시 |
| 16 | Apache Tika 의존성 | **없음** |
| 17 | Named Interface / 공개 Port 패턴 | 두 가지 공존. (a) Kotlin `@NamedInterface`를 Type에 직접(`ErrorCode`, `BusinessException`, `ApiResponse`), (b) `src/main/java/**/package-info.java`로 Package 공개(`operation.entity.type` 등 3개). 공개 Port는 `domain/{d}/query/XxxQueryPort` + `service/impl/XxxQueryPortImpl` 6개 선례 |
| 18 | Testcontainers MinIO 사용 여부 | **미사용**. `integrationTest`에 PostgreSQL·Redis·Elasticsearch Container만 있음 |
| 19 | 최대 Multipart 크기 Spring 설정 | **미설정** → Spring 기본 **1MB / 10MB**. 정책보다 먼저 여기에 막힌다 |
| 20 | 기존 ErrorCode | `global.error.CommonErrorCode` 11개 + Domain별 Enum(`NotificationErrorCode` 등). `FILE_*` Code는 **없음**. `GlobalExceptionHandler`에 `MaxUploadSizeExceededException` 처리 **없음**(현재 500으로 떨어짐) |

### 추가로 확인한 것

| 항목 | 실제 상태 |
| --- | --- |
| `files` Table | **V2에 이미 병합됨**. `members.profile_image_file_id`, `companies.logo_file_id`, `async_operations.result_file_id` **3개 FK가 이미 참조 중** |
| `files` 데이터 | **비어 있음**. 어떤 Production 코드도 `StoredFileRepository`를 쓰지 않는다 → V17의 Column 추가/제약 강화에 데이터 위험이 없다 |
| Modulith 검증 | `ModularityTest`가 `modules.verify()` 실행. `domain.file` 포함 17개 Module이 각각 독립 → **모듈 간 순환 의존이 검출된다** (§4 참고) |
| 인증 방식 | `JwtAuthenticationFilter`가 **Authorization Header만** 읽음. Cookie 인증 **전무** → `<img src>`는 인증을 통과할 수 없다 (§11.3 참고) |
| Member/Company의 대기 상태 | `MemberServiceImpl.kt:92`가 `profileImageUrl` 요청을 **명시적으로 거부**("File 업로드 API 연동 이후 다시 시도해주세요"). `MemberServiceImpl.kt:52,158`·`MemberSearchServiceImpl.kt:64`는 `profileImageUrl = null` 하드코딩. `CompanyResponse.kt:53`도 동일 |
| 배포 환경 | EC2에 SSH 접속 후 `docker compose --profile app up --build`(`.github/workflows/cd.yml:124-134`). 앱은 **Container 안**에서 동작 |
| CI | `unit-test`(Docker 불필요)와 `integration-test`(Docker 제공) Job이 분리되어 있음 → **Workflow 변경 없이** MinIO Testcontainers 추가 가능 |

---

## 2. 전제: 기존 `files` Schema와 지시서 §3/§37의 불일치

`files` Table은 **이미 `develop`에 병합되어 있다**. 지시서 §3/§37의 필드 목록은 이와 다른 Schema를 전제로 쓰였다.

```sql
-- V2 실제 (병합됨, 수정 금지)
CREATE TABLE files (
    id, uploader_member_id, owner_type NOT NULL, owner_id NOT NULL,
    object_key, original_name, content_type, size_bytes,
    contains_personal_information, expires_at, created_at, deleted_at
);
```

| 지시서 §3/§37 필드 | V2 실제 Schema | 결정 |
| --- | --- | --- |
| `storageKey` / `storage_key` | `object_key VARCHAR(1000)` | **이름 유지**. 동의어이고 API에 노출되지 않는다. RENAME은 churn만 생긴다. V17에서 `UNIQUE` 제약만 추가(§37 요구) |
| `size` | `size_bytes BIGINT` | **이름 유지**. `ck_files_size_bytes CHECK (size_bytes >= 0)`가 §37 요구를 이미 충족 |
| `purpose` | Column 없음 | **V17에서 추가**(`VARCHAR(50) NOT NULL`) |
| `status` | Column 없음 | **V17에서 추가**(`VARCHAR(20) NOT NULL`) |
| `extension` | Column 없음 | **V17에서 추가**(`VARCHAR(20)`) |
| `linkedAt` | Column 없음 | **V17에서 추가** |
| `updatedAt` | Column 없음 | **V17에서 추가**. `docs/architecture/erd.md:55`가 "`files`는 `updated_at`이 없다"고 기록한 상태를 해소한다 |
| `checksum` (§29 선택) | Column 없음 | **추가하지 않음**. §29가 "MVP에서 필요성이 없다면 제외 가능"이라 했고 중복 제거 요구가 없다 |
| `storageBucket` (§3 선택) | Column 없음 | **추가하지 않음**. Bucket은 환경별 설정값이지 Row별 속성이 아니다. Bucket 이전이 필요해지는 시점에 판단 |
| (지시서에 없음) | `owner_type` / `owner_id` **NOT NULL** | **NULLABLE로 완화 후 유지**. 이것이 §13의 "files에 targetType/targetId" 방식이며 `file_links`를 만들지 않는 근거 (§3.4) |
| (지시서에 없음) | `contains_personal_information NOT NULL` | **유지하되 이번 범위에서 사용하지 않음**. 값의 판단 주체가 확정되지 않아 업로드 시 `false`로 고정하고 §17에 남긴다 |
| (지시서에 없음) | `expires_at` | **유지하되 이번 범위에서 사용하지 않음**. Phase 5 Cleanup이 사용할 후보 |

`files` Table은 **비어 있으므로** NOT NULL Column 추가와 UNIQUE 제약 추가에 데이터 위험이 없다. 따라서 `LEGACY` 같은 더미 Enum 값을 영구히 남기지 않는다.

---

## 3. 확정된 결정 요약

| # | 항목 | 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | Migration 방향 | **기존 `files` 확장**(V17). 새 Table도, `file_links`도 만들지 않는다 | 사용자 확정. 기존 3개 FK와 ERD 문서가 그대로 유지된다 |
| 2 | PR 범위 | **Phase 1~4**. Cleanup(5)·Archive(6) 제외 | 사용자 확정. Phase 3까지 가야 Job/Program의 `fileIds` 제외 사유가 해소되고, Phase 4 없이는 §45 완료 조건을 못 채운다 |
| 3 | PR 분할 | **PR A(File 도메인) + PR B(Member·Company 배선)** 2개 | 사용자 확정. PR B가 다른 도메인의 API 계약을 바꾸므로 분리해 논의 |
| 4 | File ↔ Target 관계 | **`files.owner_type` / `owner_id` 단일 연결**. `file_links` 미생성 | §13이 "한 File은 반드시 한 Resource에만"이면 단순화 가능하다고 했고, V2가 이미 이 구조다. 다중 연결·연결 이력 요구가 확정되면 그때 `file_links`로 이관 |
| 5 | Storage SDK | **AWS SDK v2 (`software.amazon.awssdk:s3`) 단일 Adapter** | 사용자 확정. endpoint·path-style 설정만으로 local=MinIO / prod=AWS S3를 모두 처리. §2 "특정 환경에 강결합하지 않는다" |
| 6 | 업로드 정합성 | **DB 선행 2단계 커밋** (`PENDING` → S3 → `UPLOADED`) | 사용자 확정. Cleanup이 없는 이번 PR에서도 모든 고아가 DB에 흔적을 남겨 §30의 "관측 가능해야 한다"를 만족 |
| 7 | MIME 실제 검증 | **`org.apache.tika:tika-core` 추가** | 사용자 확정. ~~docx/hwpx는 ZIP, hwp는 OLE2 Container라 수작업 시그니처로는 구분 불가~~ **정정(PR #87 리뷰)**: `tika-core` 단독에는 `ZipContainerDetector`가 없어 Container 형식은 오히려 파일명 Hint로 판정된다. 이 근거는 성립하지 않으며, 그래서 허용 목록에서 `docx`를 제외했다. PDF/PNG/JPEG의 Magic Number 판정에는 그대로 유효하다 |
| 8 | FilePurpose | **8개 확정**(§4 후보 6 + `PROFILE_IMAGE` + `COMPANY_LOGO`) | 사용자 확정. 뒤 둘은 기존 FK가 이미 있어 추측이 아니다. `OPERATION_RESULT`는 Upload API를 경유하지 않아 제외 |
| 9 | 정책(확장자·MIME·크기·개수) | **`@ConfigurationProperties`로 외부화**, 값은 잠정값 + `DECISION_REQUIRED` | 사용자 확정. §7이 허용하고 `app.collector.provider.*` 선례와 동일 |
| 10 | 다운로드 구현 | **302 Redirect → 단기 Presigned URL** | 사용자 확정. §15가 "Presigned URL을 우선 검토할 수 있다"고 명시 |
| 11 | 이미지 URL 전달 | **응답 Body에 Presigned URL 직접** | 사용자 확정. `<img src>`가 Bearer Token을 보내지 않아 302 Endpoint로는 표시 불가 (§11.3) |
| 12 | 권한 검증 구조 | **DIP: `domain.file`이 `FileAccessChecker` Interface 소유, 각 도메인이 구현** | 사용자 확정. §16을 Notification 방식(도메인이 Port 정의)으로 구현하면 **Modulith 순환 의존**이 발생한다 (§4) |
| 13 | Checker 구현 범위 | **`MEMBER`(프로필 이미지) + `COMPANY`(로고) 2개만**. 나머지 `ownerType`은 미등록 → **기본 거부** | 나머지 도메인은 파일을 붙이는 기능 자체가 아직 없다 |
| 14 | 운영 자격증명 | **IAM Instance Profile**(자격증명 미지정 → `DefaultCredentialsProvider`) | 사용자 확정. 장기 Access Key를 서버·Secret·Compose 어디에도 두지 않는다 |
| 15 | 사용자용 DELETE API | **추가하지 않음** | §20 명시. 연결 해제는 Resource 수정 API가 담당 |
| 16 | 검증 범위 | **Unit + `@WebMvcTest` Controller + Testcontainers MinIO IT** | 사용자 확정. §45가 세 가지를 모두 요구 |

---

## 4. Module 경계와 의존 방향 (가장 중요한 구조 결정)

`ModularityTest`가 `modules.verify()`를 실행하고 `domain.file`·`domain.job` 등 17개가 **각각 독립 Application Module**이다. Spring Modulith는 **모듈 간 순환 의존을 검출해 실패시킨다.**

지시서 §12와 §16을 각각 저장소의 기존 패턴대로 구현하면 순환이 생긴다.

```
§12: JobService ──> file.FileLinkPort                    domain.job  ──> domain.file
§16: FileAccessResolver ──> job.query.JobFileAccessQueryPort
                                                          domain.file ──> domain.job
     └─ Notification이 쓴 패턴(각 도메인이 query/ 아래 Port를 정의)

⇒ 순환. ./gradlew test --tests "*ModularityTest" 실패
```

Notification은 **소비만** 하므로(`notification → job`, `notification → program` 단방향) 이 문제가 없었다. File은 **양방향**이라 같은 패턴을 쓸 수 없다.

### 채택 구조: 의존성 역전(DIP)

`domain.file`이 Interface를 소유하고 각 도메인이 구현체를 등록한다. 의존 방향이 `domain.X → domain.file` 단방향으로 통일된다.

```kotlin
// domain/file/access/FileAccessChecker.kt  ← File이 소유하고 공개
@NamedInterface
interface FileAccessChecker {
    val ownerType: FileOwnerType
    fun canDownload(requesterId: Long, ownerId: Long): Boolean
}

// domain/file/access/FileAccessResolver.kt  ← File 내부
@Component
class FileAccessResolver(checkers: List<FileAccessChecker>) {
    private val byOwnerType = checkers.associateBy { it.ownerType }
    // 등록된 Checker가 없는 ownerType → 기본 거부(FILE_ACCESS_DENIED)
}

// domain/member/service/impl/MemberProfileImageAccessChecker.kt  ← Member가 구현 (PR B)
@Component
class MemberProfileImageAccessChecker(
    private val memberRepository: MemberRepository,
) : FileAccessChecker { ... }
```

### 확정된 의존 방향

```
domain.member  ──┐
domain.company ──┼──> domain.file   (FileLinkPort, FileUrlPort, FileAccessChecker)
domain.job     ──┤    ※ 향후
domain.program ──┘

domain.file ──> global   (BusinessException, ErrorCode, ApiResponse)
domain.file ──> 어떤 도메인 Module도 참조하지 않는다
```

`domain.file`이 다른 도메인의 Repository·Entity를 직접 참조하지 않으므로 §12·§16·§42의 금지 사항을 모두 지킨다.

### 공개 Type (`@NamedInterface`)

`ApiResponse`·`ErrorCode`가 쓴 **Kotlin Type 직접 Annotation** 방식을 따른다(`package-info.java` 방식은 Package 전체 공개라 여기선 과하다).

| 공개 Type | 위치 | 용도 |
| --- | --- | --- |
| `FileLinkPort` | `domain/file/link/` | 다른 도메인이 fileId를 자기 Resource에 연결 |
| `FileUrlPort` | `domain/file/link/` | 다른 도메인이 fileId → Presigned 이미지 URL 배치 변환 |
| `FileAccessChecker` | `domain/file/access/` | 각 도메인이 다운로드 권한 판정을 구현 |
| `FileSnapshot` | `domain/file/link/` | 공개 Metadata(§22) |
| `FilePurpose` / `FileOwnerType` | `domain/file/entity/type/` | 위 Port의 Parameter Type |

`StoredFile`(Entity), `StoredFileRepository`, `FileStoragePort`, `objectKey`, `bucket`은 **공개하지 않는다**(§22 금지 목록).

---

## 5. V17 Migration

`V17__extend_files_for_upload_lifecycle.sql` (신규 파일. 기존 Migration 수정 없음)

```sql
-- 1) 미연결 상태 표현을 위해 owner_* NOT NULL 완화
ALTER TABLE files ALTER COLUMN owner_type DROP NOT NULL;
ALTER TABLE files ALTER COLUMN owner_id   DROP NOT NULL;

-- 2) 생명주기 Column 추가 (Table이 비어 있어 DEFAULT 없이 NOT NULL 가능)
ALTER TABLE files
    ADD COLUMN purpose    VARCHAR(50) NOT NULL,
    ADD COLUMN status     VARCHAR(20) NOT NULL,
    ADD COLUMN extension  VARCHAR(20),
    ADD COLUMN linked_at  TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL;

-- 3) 제약
ALTER TABLE files ADD CONSTRAINT uk_files_object_key UNIQUE (object_key);
ALTER TABLE files ADD CONSTRAINT ck_files_link_state CHECK (
    (status IN ('PENDING', 'UPLOADED', 'FAILED')
        AND owner_type IS NULL AND owner_id IS NULL)
    OR
    (status IN ('LINKED', 'DELETED')
        AND owner_type IS NOT NULL AND owner_id IS NOT NULL)
);

-- 4) 인덱스 (§37)
CREATE INDEX idx_files_purpose ON files (purpose);
CREATE INDEX idx_files_status_created_at ON files (status, created_at);  -- Phase 5 Cleanup 조회용
```

기존 `idx_files_uploader_member_id`·`idx_files_owner`·`ck_files_size_bytes`는 그대로 재사용한다.

> **주의**: `ck_files_link_state`는 `DELETED`도 `owner_*` NOT NULL을 요구한다. 미연결 상태에서 삭제되는 파일은 `DELETED`가 아니라 Hard Delete로 처리한다(§38이 "미연결 임시 File은 Metadata까지 Hard Delete할 수도 있다"고 허용). Phase 5 설계 시 이 제약을 함께 검토한다.

---

## 6. Entity와 Enum

### `StoredFile` 확장 (`domain/file/entity/StoredFile.kt`)

기존 Field는 유지하고 다음을 추가한다.

```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "purpose", nullable = false, length = 50)
var purpose: FilePurpose

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 20)
var status: FileStatus

@Column(name = "extension", length = 20)
var extension: String? = null

@Column(name = "linked_at")
var linkedAt: LocalDateTime? = null

@UpdateTimestamp
@Column(name = "updated_at", nullable = false)
var updatedAt: LocalDateTime? = null
```

`ownerType`/`ownerId`는 `String`/`Long` → `FileOwnerType?`/`Long?`로 변경한다. 상태 전이는 Entity Method로 캡슐화한다(`markUploaded()`, `markFailed()`, `linkTo(ownerType, ownerId)`, `unlink()`).

### Enum (`domain/file/entity/type/`)

```kotlin
enum class FilePurpose {
    PROFILE_IMAGE,        // owner_type = MEMBER          — members.profile_image_file_id
    COMPANY_LOGO,         // owner_type = COMPANY         — companies.logo_file_id
    JOB_ATTACHMENT,       // owner_type = JOB
    PROGRAM_ATTACHMENT,   // owner_type = PROGRAM
    JOB_APPLICATION,      // owner_type = JOB_APPLICATION
    PROGRAM_APPLICATION,  // owner_type = PROGRAM_APPLICATION
    INQUIRY_ATTACHMENT,   // owner_type = INQUIRY
    PORTFOLIO,            // owner_type = PORTFOLIO_SUBMISSION
}

enum class FileOwnerType {
    MEMBER, COMPANY, JOB, PROGRAM,
    JOB_APPLICATION, PROGRAM_APPLICATION, INQUIRY, PORTFOLIO_SUBMISSION,
}

enum class FileStatus {
    PENDING,   // DB Row만 생성됨. S3 업로드 진행 중 — 조회·연결·다운로드 전부 불가
    UPLOADED,  // S3 업로드 완료. 아직 어떤 Resource에도 연결되지 않음
    LINKED,    // Resource에 연결됨
    FAILED,    // S3 업로드 실패. 보상 삭제 시도 완료 — 사용자에게 노출되지 않음
    DELETED,   // 논리 삭제 (§38)
}
```

`FilePurpose ↔ FileOwnerType`는 1:1 고정 매핑이며 `FilePurpose.ownerType` Property로 표현한다. `PORTFOLIO`의 `ownerType`은 `PORTFOLIO_SUBMISSION`이다(`portfolio_requests`가 아니라 실제 제출물이 붙는 쪽).

`OPERATION_RESULT`(서버 생성 ZIP)는 **포함하지 않는다** — Upload API를 경유하지 않아 지금 넣으면 Swagger Enum과 실제 지원 범위가 어긋난다(§36). Phase 6에서 추가한다.

---

## 7. Storage 계층 (S3 연동)

### 7.1 의존성 (`build.gradle.kts`)

**반영 완료** — `build.gradle.kts`에 적용하고 실제 해석·다운로드·Build까지 검증했다.

```kotlin
dependencyManagement {
    imports {
        // 기존 3개에 추가
        mavenBom("software.amazon.awssdk:bom:2.51.2")
    }
}

dependencies {
    // S3 Object Storage. local은 MinIO, 운영은 AWS S3 — endpoint/path-style 설정으로만 구분한다.
    implementation("software.amazon.awssdk:s3")

    // 업로드 파일의 실제 형식(Magic Number) 탐지. 본문을 파싱하는 tika-parsers는 쓰지 않는다.
    // Spring Boot Dependency Management가 Tika를 관리하지 않아 Version을 직접 고정한다.
    implementation("org.apache.tika:tika-core:3.3.2")

    // Version은 기존 testcontainers-bom(2.0.5)이 관리하므로 고정하지 않는다.
    "integrationTestImplementation"("org.testcontainers:testcontainers-minio")
}
```

### 검증 결과

| 확인 항목 | 결과 |
| --- | --- |
| `awssdk:bom` 최신 안정 | **2.51.2** (`s3 → 2.51.2` 해석 확인) |
| `tika-core` 최신 안정 | **3.3.2**. `maven-metadata.xml`의 `<latest>`/`<release>`는 `4.0.0-beta-1`을 가리키지만 beta라 제외 |
| Testcontainers MinIO Artifact 이름 | **`org.testcontainers:testcontainers-minio`**. Maven Central 검색 색인에는 잡히지 않지만 `testcontainers-bom:2.0.5`가 **이미 관리**하고 있고 `maven-metadata.xml` 200 응답 확인 → **별도 Version 고정 불필요**. jar 안에 `org.testcontainers.containers.MinIOContainer` 존재 확인 |
| `S3Presigner`가 `s3` Artifact에 포함되는지 | **포함**. `software/amazon/awssdk/services/s3/presigner/S3Presigner.class`, `.../model/PresignedGetObjectRequest.class` 확인 → `s3-presigner` 별도 Artifact 불필요 |
| Java 25 Toolchain 호환성 | **문제 없음**. bytecode major version — `s3` 52(Java 8), `tika-core` 55(Java 11), `testcontainers-minio` 52(Java 8) |
| Build | `./gradlew clean test build` **BUILD SUCCESSFUL** (3m 21s) |

### 7.2 Port (`domain/file/storage/FileStoragePort.kt`) — 공개하지 않음

```kotlin
interface FileStoragePort {
    fun upload(key: String, contentType: String, size: Long, inputStream: InputStream)
    fun download(key: String): StoredFileResource
    fun delete(key: String)
    fun exists(key: String): Boolean
    fun presignedGetUrl(key: String, filename: String, disposition: ContentDisposition): URI
}
```

Domain/Application 계층은 `S3Client`·`PutObjectRequest`를 **직접 참조하지 않는다**(§2). SDK Type은 `S3FileStorageAdapter` 안에서만 등장한다.

### 7.3 Adapter (`domain/file/storage/S3FileStorageAdapter.kt`)

- `upload`: `RequestBody.fromInputStream(stream, size)` — 파일 전체를 메모리에 올리지 않는다(§42).
- `presignedGetUrl`: `S3Presigner`로 서명하며 **`responseContentDisposition`과 `responseContentType`을 서버가 강제 지정**한다. 원본 파일명은 서버가 RFC 5987(`filename*=UTF-8''...`)로 Encoding하므로 §28의 Header Injection과 한글 파일명 문제가 동시에 해결된다.
- SDK 예외(`S3Exception`, `NoSuchKeyException`)는 Adapter 경계에서 `FileStorageException`으로 변환한다. **SDK 메시지를 API 응답에 그대로 노출하지 않는다**(§26/§42).
- `NoSuchKey`는 `FILE_NOT_FOUND`로 뭉개지 않고 **로그에서 구분**한다(§30) — DB Metadata는 있는데 Storage Object가 없는 정합성 장애를 숨기지 않기 위함이다. 응답은 500 `FILE_STORAGE_ERROR`.

### 7.4 Client 구성 (`domain/file/storage/FileStorageConfig.kt`)

```kotlin
@Bean fun s3Client(props: FileStorageProperties): S3Client =
    S3Client.builder()
        .region(Region.of(props.region))
        .apply { props.endpoint?.let { endpointOverride(URI.create(it)) } }   // local: MinIO
        .forcePathStyle(props.pathStyleAccess)                                 // local: true
        .credentialsProvider(credentialsProvider(props))
        .build()

// access-key/secret-key가 모두 있으면 Static(local MinIO), 없으면 Default(운영 IAM Role)
private fun credentialsProvider(props: FileStorageProperties): AwsCredentialsProvider =
    if (!props.accessKey.isNullOrBlank() && !props.secretKey.isNullOrBlank()) {
        StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey, props.secretKey))
    } else {
        DefaultCredentialsProvider.create()
    }
```

`S3Presigner`도 동일한 설정으로 별도 Bean으로 등록한다.

### 7.5 Object Key 규칙

```
{purpose}/{yyyy}/{MM}/{UUID}
예: PROFILE_IMAGE/2026/08/9f3c1a2e-....
```

- `originalName`은 **절대 Key에 사용하지 않는다**(§3/§42). 확장자도 붙이지 않는다 — Content-Type은 Metadata로 관리하고 Key는 순수 식별자로 둔다.
- `purpose` Prefix는 향후 S3 Lifecycle Rule을 목적별로 다르게 걸 수 있게 한다.
- UUID라 파일명이 같아도 충돌하지 않고 외부에서 추측할 수 없다.

---

## 8. 정책과 검증

### 8.1 `FileStorageProperties` / `FilePolicyProperties`

```yaml
# application.yaml (공통)
app:
  file:
    storage:
      presigned-url-ttl-seconds: 900        # 15분
    # 아래 숫자는 모두 잠정값이다 (DECISION_REQUIRED §44.2~5). 확정되면 이 값만 교체한다.
    policies:
      PROFILE_IMAGE:
        extensions: [png, jpg, jpeg, webp]
        mime-types: [image/png, image/jpeg, image/webp]
        max-size-bytes: 5242880            # 5MB
        max-count: 1
      COMPANY_LOGO:
        extensions: [png, jpg, jpeg, webp]
        mime-types: [image/png, image/jpeg, image/webp]
        max-size-bytes: 2097152            # 2MB
        max-count: 1
      # 아래는 계획 시점의 초안이다. 실제 구현에서는 docx/hwp 같은 Container 형식을 제외했다
      # (표 7번 정정 참고 -- tika-core만으로는 내용 검증이 성립하지 않는다).
      JOB_APPLICATION:
        extensions: [pdf, docx, hwp, png, jpg, jpeg]
        mime-types: [application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/x-hwp, image/png, image/jpeg]
        max-size-bytes: 10485760           # 10MB
        max-count: 5
      # PROGRAM_APPLICATION / JOB_ATTACHMENT / PROGRAM_ATTACHMENT / INQUIRY_ATTACHMENT / PORTFOLIO 동일 구조

spring:
  servlet:
    multipart:
      max-file-size: 20MB      # 모든 policy의 max-size-bytes보다 커야 한다
      max-request-size: 25MB
```

**SVG는 어떤 Purpose에서도 허용하지 않는다.** SVG는 스크립트를 품을 수 있어 §27/§28에 따라 `attachment` + `octet-stream`으로 강등되는데, 로고는 브라우저에 inline 표시해야 하므로 "업로드는 되지만 표시는 안 되는" 모순이 생긴다.

기동 시 `@PostConstruct`로 다음 불변식을 검증하고 위반이면 **기동을 거부**한다(`CollectorSeedProdGuard` 선례).

1. `FilePurpose` 8개 전부에 정책이 정의되어 있다.
2. 모든 `max-size-bytes` ≤ `spring.servlet.multipart.max-file-size`.
3. `extensions`가 비어 있지 않고, `mime-types`도 비어 있지 않다.
4. `max-count` ≥ 1.

### 8.2 파일명 정규화 (`FileNameSanitizer`) — §9/§27

`originalName`은 **표시용으로만 보존**하며 다음을 순서대로 적용한다.

1. `null`/blank → `INVALID_FILE_NAME`
2. Windows/Unix Path 구분자 기준으로 마지막 segment만 취함 (`../../secret.txt`, `C:\Users\user\secret.pdf` → `secret.txt`/`secret.pdf`)
3. Null Byte(`\u0000`)와 제어 문자 제거
4. 유니코드 정규화(NFC)
5. 길이 제한 — Column이 `VARCHAR(500)`이므로 그 이내로 자르되 확장자는 보존
6. 확장자는 **마지막 `.` 이후**만 인정 → `resume.pdf.exe`의 확장자는 `exe`이며 허용 목록에 없으면 거부(§27 Double Extension)
7. 결과가 빈 문자열이거나 확장자가 없으면 `INVALID_FILE_NAME`

### 8.3 3중 교차 검증 (`FileContentTypeValidator`) — §8

```
extension       = "pdf"              ← 정규화된 originalName에서 추출
clientMimeType  = "application/pdf"  ← MultipartFile.contentType. 참고용, 신뢰하지 않음
detectedMime    = "application/pdf"  ← Tika가 앞부분만 읽어 탐지

1. detectedMime ∉ policy.mimeTypes            → 415 FILE_TYPE_NOT_ALLOWED
2. extension    ∉ policy.extensions           → 415 FILE_TYPE_NOT_ALLOWED
3. detectedMime ∉ mimeTypesOf(extension)      → 415 MIME_MISMATCH
```

- **DB `content_type`에는 `detectedMime`을 저장한다.** 클라이언트가 보낸 값이 아니다.
- **`clientMimeType`으로는 실패시키지 않는다.** 브라우저가 `image/jpg`·`application/octet-stream` 같은 비표준/불명 값을 흔히 보내 정상 업로드가 오탐으로 막힌다. 불일치는 로그에만 남긴다.
- Tika에는 **`BufferedInputStream`의 앞부분만** 넘기고 `mark`/`reset`으로 되감아 그대로 S3에 스트리밍한다. 파일 전체를 메모리에 올리지 않는다(§8/§42).

---

## 9. 업로드 흐름 — `POST /api/v1/files`

### 9.1 API 계약 (§5)

| 항목 | 값 |
| --- | --- |
| Method / Path | `POST /api/v1/files` |
| Content-Type | `multipart/form-data` |
| 권한 | 인증된 사용자(STUDENT / TEACHER / DEVELOPER) |
| Request | `file`: MultipartFile, `purpose`: FilePurpose |
| Success | `201 Created`, `ApiResponse<FileUploadResponse>` |

```kotlin
data class FileUploadResponse(
    val fileId: Long,
    val originalName: String,
    val contentType: String,      // Tika가 탐지한 값
    val size: Long,
    val purpose: FilePurpose,
    val createdAt: LocalDateTime,
)
```

`objectKey`·`bucket`·`status`·`uploaderMemberId`는 **응답에 포함하지 않는다**(§17/§22).

### 9.2 처리 순서 (§6 + §10)

```
 1. 인증 사용자 확인            authentication.principal as Long
 2. file 누락/빈 파일           → 400 FILE_EMPTY
 3. purpose 유효성              → 400 (Enum Binding 실패는 CommonErrorCode.TYPE_MISMATCH)
 4. 목적별 최대 크기            → 413 FILE_TOO_LARGE
 5. originalFilename 정규화     → 400 INVALID_FILE_NAME
 6. 확장자 검사                 → 415 FILE_TYPE_NOT_ALLOWED
 7. Tika 실제 형식 탐지         → 415 FILE_TYPE_NOT_ALLOWED / MIME_MISMATCH
 8. objectKey 생성              {purpose}/{yyyy}/{MM}/{UUID}
─────────────────────────────────────────────────────────────
 9. TX1  INSERT files(status=PENDING, object_key, ...) COMMIT
10.      S3 PutObject                    ← Transaction 밖 (Transaction Convention 17)
11. TX2  UPDATE status=UPLOADED          COMMIT
12. fileId 반환
```

**실패 보상**

| 실패 지점 | 처리 | 응답 |
| --- | --- | --- |
| 10번 (S3 Put) | best-effort `S3 delete` + `status=FAILED` 커밋 | 500 `FILE_STORAGE_ERROR` |
| 11번 (상태 전환) | S3 Object는 존재, DB는 `PENDING`으로 남음 — **DB에 흔적이 남아 추적 가능** | 500 `FILE_STORAGE_ERROR` |

`PENDING`·`FAILED`는 조회·연결·다운로드가 모두 불가능해 사용자에게 노출되지 않는다. Phase 5 Cleanup이 `status IN ('PENDING','FAILED','UPLOADED') AND created_at < threshold`로 그대로 수거한다.

> §10의 "Storage 먼저 → Metadata 저장" 순서를 쓰지 않는 이유: best-effort 삭제까지 실패하면 **DB에 아무 흔적이 없는 S3 Object**가 남는다. Cleanup은 DB를 기준으로 동작하므로 이 Object는 영원히 발견되지 않는다.

### 9.3 `MaxUploadSizeExceededException`

Spring이 Multipart 한계 초과 시 던지는 예외를 현재 `GlobalExceptionHandler`가 처리하지 않아 **500으로 떨어진다**. 명세는 413 `FILE_TOO_LARGE`를 요구한다.

`FileErrorCode`는 `domain.file`에 있어 `global`이 참조할 수 없으므로, **`domain.file` 안에 범위를 좁힌 Advice**를 둔다.

```kotlin
// domain/file/controller/FileUploadExceptionAdvice.kt
@RestControllerAdvice(assignableTypes = [FileController::class])
class FileUploadExceptionAdvice { /* MaxUploadSizeExceededException → 413 FILE_TOO_LARGE */ }
```

`global`이 특정 Domain을 알지 못한다는 기존 원칙(`ErrorCode.kt` 주석)을 깨지 않는다.

---

## 10. 연결(Link)과 소유권 — 공개 Port

### 10.1 `FileLinkPort` (`domain/file/link/`)

```kotlin
@NamedInterface
interface FileLinkPort {
    /** 소유권·목적·상태를 검증하고 대상 Resource에 연결한다. 실패 시 BusinessException. */
    fun validateAndLink(
        requesterId: Long,
        fileIds: Collection<Long>,
        purpose: FilePurpose,
        ownerType: FileOwnerType,
        ownerId: Long,
    ): List<FileSnapshot>

    /** 대상 Resource의 연결을 해제한다(Resource 수정으로 첨부가 빠질 때). */
    fun unlinkAllOf(ownerType: FileOwnerType, ownerId: Long)

    fun snapshotsOf(fileIds: Collection<Long>): Map<Long, FileSnapshot>
}

@NamedInterface
data class FileSnapshot(
    val fileId: Long,
    val originalName: String,
    val contentType: String,
    val size: Long,
)
```

`FileSnapshot`에는 `objectKey`·`bucket`·`uploaderMemberId`·Entity·Repository를 **넣지 않는다**(§22).

### 10.2 `validateAndLink` 검증 항목 (§12/§14)

| # | 검증 | 실패 Error Code |
| --- | --- | --- |
| 1 | 전달된 `fileIds`에 중복이 없음 | `INVALID_REQUEST` (`CommonErrorCode`) |
| 2 | 모든 File이 존재 | `FILE_NOT_FOUND` (404) |
| 3 | `status = UPLOADED` (PENDING/FAILED/DELETED 아님) | `FILE_NOT_FOUND` (404) |
| 4 | `uploaderMemberId == requesterId` | `FILE_NOT_OWNED` (403) |
| 5 | `file.purpose == 기대 purpose` | `FILE_PURPOSE_MISMATCH` (400) |
| 6 | 이미 다른 Resource에 연결되지 않음(`status != LINKED`) | `FILE_ALREADY_LINKED` (409) |
| 7 | `fileIds.size + 기존 연결 수 ≤ policy.maxCount` | `FILE_COUNT_EXCEEDED` (400) |

**#4가 §14의 핵심 보안 요구사항이다.** 학생 A가 업로드한 `fileId=100`을 학생 B가 자기 Application에 연결하는 것을 막는다. File ID는 식별자일 뿐 권한 증명이 아니다.

**File 도메인은 대상 Resource의 비즈니스 권한을 판정하지 않는다**(§12). "이 학생이 이 Application을 수정할 수 있는가"는 Application 도메인이, "이 File이 이 학생 소유이며 해당 Purpose인가"는 File 도메인이 판단한다.

### 10.3 `maxCount` 검증 위치 (§24)

`POST /api/v1/files`는 파일 하나만 업로드하므로 업로드 시점에는 대상 Resource가 없을 수 있다.

- **Upload 시점**: 개별 파일 정책만(크기·확장자·MIME)
- **Link 시점**: 대상 Resource 기준 전체 개수 검증(위 #7)

즉 파일 10개를 각각 업로드하는 것 자체는 성공하고, `maxCount=3`인 Resource에 네 번째를 연결할 때 `FILE_COUNT_EXCEEDED`로 거부한다.

### 10.4 `FileUrlPort` (`domain/file/link/`)

```kotlin
@NamedInterface
interface FileUrlPort {
    /** 이미지 Purpose 전용. 목록 응답에서 N+1이 나지 않도록 배치로 조회한다. */
    fun presignedImageUrls(fileIds: Collection<Long>): Map<Long, String>
}
```

Notification이 `findAllByIds(ids): Map<Long, Snapshot>` 배치 Port를 쓴 선례를 따른다 — `MemberSearchResponse`처럼 목록 N명의 아바타를 그릴 때 단건 조회를 반복하면 N+1이 난다.

이미지가 아닌 Purpose의 fileId가 들어오면 결과 Map에서 제외한다(예외를 던지지 않는다).

---

## 11. 다운로드와 접근 권한

### 11.1 API 계약 (§15)

| 항목 | 값 |
| --- | --- |
| Method / Path | `GET /api/v1/files/{fileId}/download` |
| Query | `disposition`: `attachment`(기본) / `inline` — **요청일 뿐 서버가 최종 결정** |
| 권한 | 인증된 사용자 + **파일별 접근 권한 검증**(§16) |
| Success | `302 Found`, `Location: <Presigned URL>` |
| 오류 | `404 FILE_NOT_FOUND`, `403 FILE_ACCESS_DENIED`, `500 FILE_STORAGE_ERROR` |

`SecurityConfig`에 `authorize("/api/v1/files/**", authenticated)`를 추가한다. 로그인만으로는 부족하며 **Resource별 권한을 `FileAccessResolver`가 별도로 판정**한다(Notification과 동일한 주석 패턴).

### 11.2 접근 권한 판정 순서

```
1. File 조회. 없으면                                    → 404 FILE_NOT_FOUND
2. status = PENDING / FAILED / DELETED                  → 404 FILE_NOT_FOUND
3. status = UPLOADED (미연결)  → uploaderMemberId == requesterId 만 허용
                                  아니면                → 403 FILE_ACCESS_DENIED
4. status = LINKED             → uploaderMemberId == requesterId 이면 허용
                                  아니면 FileAccessResolver.canDownload(ownerType, ...)
                                  Checker 미등록 ownerType → 403 FILE_ACCESS_DENIED (기본 거부)
5. Presigned URL 발급 → 302
```

**3번이 §14의 "다른 사용자의 미연결 파일 다운로드" 방어다.** 4번의 **기본 거부**는 Checker를 등록하지 않은 도메인의 파일이 실수로 노출되는 것을 막는다.

이번에 구현하는 Checker (PR B):

| Checker | ownerType | 규칙 |
| --- | --- | --- |
| `MemberProfileImageAccessChecker` | `MEMBER` | 본인이거나 `members.profile_public = true` |
| `CompanyLogoAccessChecker` | `COMPANY` | 인증된 사용자 모두 (`/api/v1/companies`가 이미 인증만 요구) |

나머지 6개 `ownerType`은 해당 도메인이 파일을 붙이는 기능을 구현하는 PR에서 Checker를 추가한다.

### 11.3 Content-Disposition 강제 (§27/§28)

`disposition=inline`은 **요청일 뿐** 서버가 최종 결정한다.

```
detectedContentType ∈ {image/png, image/jpeg, image/webp, image/gif}  → inline 허용
그 외 (image/svg+xml, text/html, application/pdf 포함)                 → attachment 강제
                                                                       + Content-Type: application/octet-stream
```

Presigned URL의 `responseContentDisposition`/`responseContentType` Parameter로 강제하므로 S3가 직접 응답하더라도 서버가 정한 헤더가 적용된다. 파일명은 서버가 RFC 5987로 Encoding해 넣으므로 Header Injection이 불가능하다.

### 11.4 이미지 URL은 왜 302가 아니라 응답 Body인가

`JwtAuthenticationFilter`는 **Authorization Header만** 읽고 저장소에 Cookie 인증이 전혀 없다. 브라우저는 `<img src>` 요청에 Bearer Token을 붙이지 않으므로 `/api/v1/files/{id}/download`는 **401**이 된다.

따라서 이미지 Purpose(`PROFILE_IMAGE`, `COMPANY_LOGO`)는 **서버가 이미 권한을 검증한 뒤 응답 Body에 Presigned URL을 직접 담는다**(§10.4의 `FileUrlPort`). 두 경로 모두 Presigned URL을 쓰며 **전달 위치만 다르다**(Location Header vs 응답 Body).

§17의 "Storage Key·Bucket 내부 경로를 최대한 노출하지 않는다"와의 관계:

- Bucket은 **Private 유지** — Public Access Block을 켠다(§14.3).
- Object Key는 **UUID**라 추측·열거가 불가능하다.
- URL은 **설정된 TTL 후 만료**된다 → §42가 금지하는 "영구 Public URL"·"Presigned URL 영구 저장"에 해당하지 않는다.
- §15가 "Object Storage 사용 구조에서는 Presigned URL을 우선 검토할 수 있다"고 명시적으로 허용한다.

---

## 12. Member·Company 배선 (PR B)

`MemberServiceImpl.kt:92`가 `profileImageUrl` 요청을 명시적으로 거부하고 있고 `CompanyResponse.kt:53`도 File 연동을 기다린다. PR B가 이를 해소한다.

### 12.1 변경되는 API 계약

```
PATCH /api/v1/me/profile
  이전: { "profileImageUrl": "..." } → 400 PROFILE_VALIDATION_FAILED
  이후: { "profileImageFileId": 42 } → 200

GET /api/v1/me/profile,  GET /api/v1/members/{id},  GET /api/v1/members?...
  이전: "profileImageUrl": null              (하드코딩)
  이후: "profileImageUrl": "<Presigned URL>"  (없으면 null)

POST/PATCH /api/v1/admin/companies
  이후: { "logoFileId": 43, ... } 수용

GET /api/v1/companies,  GET /api/v1/companies/{id}
  이후: "logoUrl": "<Presigned URL>"          (없으면 null)
```

**응답 Field 이름(`profileImageUrl`/`logoUrl`)은 바꾸지 않는다** — 값이 `null`에서 실제 URL로 바뀔 뿐이다. 새로 생기는 것은 **요청** Field(`profileImageFileId`/`logoFileId`)뿐이다.

### 12.2 처리 흐름

```
MemberService.updateProfile(memberId, { profileImageFileId: 42 })
  1. fileLinkPort.validateAndLink(
         requesterId = memberId, fileIds = [42],
         purpose = PROFILE_IMAGE, ownerType = MEMBER, ownerId = memberId)
  2. 기존 profileImageFileId가 있으면 fileLinkPort.unlinkAllOf(MEMBER, memberId) 선행
  3. member.profileImageFileId = 42
```

조회 시에는 `fileUrlPort.presignedImageUrls(fileIds)`로 **한 번에** 변환한다.

Company 로고는 소유자가 개인이 아니므로 `ownerId = companyId`로 연결하고, 업로더는 등록을 수행한 DEVELOPER다.

---

## 13. Error Code / Security / Swagger

### 13.1 `FileErrorCode` (`domain/file/exception/FileErrorCode.kt`)

§26의 확정 5개 + 실제로 발생하는 것만 추가한다. **같은 상황에 비슷한 Code를 여러 개 만들지 않는다.**

| Code | HTTP | 발생 시점 |
| --- | --- | --- |
| `FILE_TOO_LARGE` | 413 | 정책 초과 / Multipart 한계 초과 |
| `FILE_TYPE_NOT_ALLOWED` | 415 | 확장자 또는 탐지 MIME이 허용 목록 밖 |
| `MIME_MISMATCH` | 415 | 확장자 ↔ 탐지 MIME 불일치 |
| `FILE_NOT_FOUND` | 404 | 없음 / PENDING / FAILED / DELETED |
| `FILE_ACCESS_DENIED` | 403 | 다운로드 권한 없음 |
| `FILE_EMPTY` | 400 | 빈 파일 |
| `INVALID_FILE_NAME` | 400 | 정규화 실패 |
| `FILE_NOT_OWNED` | 403 | 다른 사용자의 파일 연결 시도 (§14) |
| `FILE_PURPOSE_MISMATCH` | 400 | 기대 Purpose와 불일치 |
| `FILE_ALREADY_LINKED` | 409 | 이미 다른 Resource에서 사용 중 |
| `FILE_COUNT_EXCEEDED` | 400 | Resource 최대 첨부 개수 초과 |
| `FILE_STORAGE_ERROR` | 500 | Storage 장애 |

`INVALID_FILE_PURPOSE`는 추가하지 않는다 — Enum Binding 실패는 `GlobalExceptionHandler`가 이미 `CommonErrorCode.TYPE_MISMATCH`로 처리한다.

`FILE_NOT_OWNED`/`FILE_ACCESS_DENIED`를 404로 감추지 않고 403으로 돌려주는 것은 `ApplicationErrorCode.APPLICATION_ACCESS_FORBIDDEN`·`NotificationErrorCode.NOTIFICATION_ACCESS_DENIED`의 기존 관례를 따른 것이다.

### 13.2 SecurityConfig

```kotlin
// 파일 업로드·다운로드는 학생·교사·개발자 모두 사용하므로 인증만 요구한다. 로그인만으로
// 모든 파일을 받을 수 있다는 뜻이 아니며, 파일별 소유권·대상 Resource 접근 권한은 Role로
// 알 수 없어 FileAccessResolver가 별도로 판정한다(File 도메인 요구사항 §15/§16).
authorize("/api/v1/files", authenticated)
authorize("/api/v1/files/**", authenticated)
```

### 13.3 Swagger (§36)

- `@Tag(name = "File - 파일", description = "... 필요 권한: 인증된 사용자(학생, 교사, 개발자).")`
- Upload는 `@RequestBody(content = [Content(mediaType = MULTIPART_FORM_DATA_VALUE)])`로 `file`·`purpose`를 명시.
- Download는 302 Redirect와 `Location` Header를 응답 설명에 기술.
- 오류 응답 5종(413/415/404/403/401)을 `@ApiResponses`에 기술.
- **Swagger Enum에는 확정한 8개 `FilePurpose`만** 노출한다. `OPERATION_RESULT`는 지원하지 않으므로 넣지 않는다.
- `OpenApiDocumentationTest`를 통과시킨다(`docs/ai/openapi-documentation.md` 필수).

---

## 14. 설정 · 환경변수 · 인프라 선행 조건

### 14.1 Profile별 설정

```yaml
# application-local.yaml  (MinIO — compose.yaml의 기존 Container 재사용)
app:
  file:
    storage:
      endpoint: http://localhost:9000
      region: us-east-1
      bucket: ${FILE_STORAGE_BUCKET:geti-local}
      path-style-access: true
      access-key: ${MINIO_ROOT_USER:geti-local}
      secret-key: ${MINIO_ROOT_PASSWORD:geti-local-minio}

# application-prod.yaml  (AWS S3 — 안전하지 않은 기본값을 두지 않는다)
app:
  file:
    storage:
      region: ${FILE_STORAGE_REGION}
      bucket: ${FILE_STORAGE_BUCKET}
      path-style-access: false
      # endpoint 미지정 → AWS 기본 Endpoint
      # access-key/secret-key 미지정 → DefaultCredentialsProvider (IAM Instance Profile)
```

`application-prod.yaml`이 `${ELASTICSEARCH_URIS}`처럼 기본값 없이 선언하는 기존 규칙("값이 없으면 기동을 거부한다")을 그대로 따른다. **실제 Secret 값은 어떤 yaml에도 쓰지 않는다**(§33).

`compose.yaml`의 `app` 서비스에 `FILE_STORAGE_BUCKET`·`FILE_STORAGE_REGION`을 전달하도록 추가한다. MinIO Bucket 자동 생성은 `S3FileStorageAdapter`가 기동 시 `CreateBucket`(이미 있으면 무시)으로 처리하되 **local Profile에서만** 수행한다 — 운영에서 앱이 Bucket을 만들지 않는다.

### 14.2 IAM Role (운영)

```json
{
  "Effect": "Allow",
  "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
  "Resource": "arn:aws:s3:::<bucket>/*"
}
```

`s3:ListBucket`은 필요 없다(Key를 항상 알고 접근한다). Bucket 생성·정책 변경 권한도 주지 않는다.

### 14.3 인프라 선행 조건 (코드로 해결할 수 없음)

배포 전에 사람이 수행해야 하며, 하나라도 빠지면 운영에서 실패한다.

| # | 항목 | 이유 |
| --- | --- | --- |
| 1 | **EC2 Metadata hop limit을 2로 상향** (`aws ec2 modify-instance-metadata-options --http-put-response-hop-limit 2 --http-tokens required`) | 앱이 **Container 안**에서 돌아 IMDS까지 hop이 하나 더 필요하다. 기본값 1이면 `DefaultCredentialsProvider`가 자격증명을 못 얻어 **모든 S3 호출이 실패**한다 |
| 2 | EC2 Instance에 위 IAM Role 연결 | 자격증명 공급원 |
| 3 | S3 Bucket 생성 + **Block Public Access 전체 활성화** | §17/§42 "Bucket Public 금지" |
| 4 | Bucket 기본 암호화(SSE-S3 이상) 활성화 | 지원서·개인정보가 저장된다 |
| 5 | `FILE_STORAGE_BUCKET`·`FILE_STORAGE_REGION`을 배포 환경에 주입 | 미설정 시 기동 거부 |
| 6 | (선택) Bucket CORS 설정 | `<img src>`는 CORS 없이 동작하지만, 프론트가 `fetch`로 Presigned URL을 직접 받는 경우 필요. 실제 Web Origin이 확정된 뒤 판단 |
| 7 | (선택) S3 Lifecycle Rule | Object Key의 `{purpose}/` Prefix로 목적별 보존 기간 적용 가능. 보존 정책이 확정되면 |

---

## 15. 테스트 계획

### `src/test` (Docker 불필요 — 기존 CI `unit-test` Job 그대로)

`InMemoryFileStoragePort` Fake로 Storage를 대체한다.

| Test | 검증 (§34/§35) |
| --- | --- |
| `FileUploadServiceTest` | 정상 / 빈 파일 / 크기 초과 / 확장자 불허 / MIME 불허 / 확장자·MIME 불일치 / 잘못된 Purpose / 파일명 정규화 / **Storage 실패 시 FAILED 전환 + 보상 삭제** / **상태 전환 실패 시 PENDING 잔존** |
| `FileLinkServiceTest` | 본인 File 연결 성공 / 타인 File 연결 실패 / Purpose 불일치 / 이미 연결된 File / fileId 중복 전달 / maxCount 초과 |
| `FileDownloadServiceTest` | 본인 접근 / Checker 허용 / Checker 거부 / **Checker 미등록 ownerType 기본 거부** / 없는 File / PENDING·FAILED·DELETED / Storage Object 없음 |
| `FileNameSanitizerTest` | `../../secret.txt` / `C:\Users\user\secret.pdf` / Null Byte / 제어 문자 / 과다 길이 / `resume.pdf.exe` / 확장자 없음 / blank |
| `FileContentTypeValidatorTest` | 3중 교차 검증 규칙, 비표준 client MIME이 오탐을 내지 않는지 |
| `FileDispositionPolicyTest` | 이미지만 inline, SVG·HTML·PDF는 attachment + octet-stream 강등 |
| `FilePolicyPropertiesTest` | 기동 불변식 4종, 위반 시 기동 거부 |
| `FileControllerTest` (`@WebMvcTest`) | 401 / STUDENT·TEACHER·DEVELOPER 성공 / multipart / file 누락 / purpose 누락 / 413 / 415(2종) / 201 / 공통 `ApiResponse` 구조 / 302 Location / 404 / 403 / **응답에 objectKey·bucket 미포함** |
| `MemberProfileImageAccessCheckerTest`, `CompanyLogoAccessCheckerTest` | 각 규칙 (PR B) |

### `src/integrationTest` (Testcontainers — 기존 CI `integration-test` Job 그대로)

| Test | 검증 |
| --- | --- |
| `FileStorageIntegrationTest` | MinIO Container로 실제 `upload`/`download`/`delete`/`exists`, **Presigned URL 서명·만료**, `responseContentDisposition` override가 실제로 적용되는지, `endpointOverride`+`forcePathStyle` 설정 검증 |
| `FileUploadFlowIntegrationTest` | PostgreSQL + MinIO. 실제 Multipart 업로드 → S3 Object 생성 확인 → DB Metadata 확인 → Presigned Download → 삭제. **V17 Migration이 실제 PostgreSQL에 적용되는지**(H2는 구문이 달라 `src/test`로는 검증 불가) |

### 구조 검증

```bash
./gradlew test --tests "*ModularityTest"          # 순환 의존 없음 (§4)
./gradlew test --tests "*PackageArchitectureTest"
./gradlew test --tests "*OpenApiDocumentationTest"
```

### 최종 검증 명령

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean test build     # spotlessCheck, detekt, koverVerify 포함
.\gradlew.bat integrationTest      # Docker 필요
```

---

## 16. 변경·생성 파일 목록

### PR A — `feat/{issue}-file-domain-core`

```
build.gradle.kts                                       awssdk BOM, s3, tika-core, testcontainers-minio
src/main/resources/db/migration/V17__extend_files_for_upload_lifecycle.sql   신규
src/main/resources/application.yaml                    app.file.policies, spring.servlet.multipart
src/main/resources/application-local.yaml              MinIO storage 설정
src/main/resources/application-prod.yaml               AWS S3 storage 설정
compose.yaml                                           app 서비스에 FILE_STORAGE_* 전달

domain/file/entity/StoredFile.kt                       확장 (purpose/status/extension/linkedAt/updatedAt, 상태 전이 Method)
domain/file/entity/type/FilePurpose.kt                 신규
domain/file/entity/type/FileOwnerType.kt               신규
domain/file/entity/type/FileStatus.kt                  신규
domain/file/repository/StoredFileRepository.kt         Query Method 추가

domain/file/storage/FileStoragePort.kt                 신규
domain/file/storage/S3FileStorageAdapter.kt            신규
domain/file/storage/FileStorageConfig.kt               신규 (S3Client, S3Presigner Bean)
domain/file/storage/FileStorageProperties.kt           신규
domain/file/storage/StoredFileResource.kt              신규

domain/file/policy/FilePolicyProperties.kt             신규 (기동 불변식 검증 포함)
domain/file/policy/FileNameSanitizer.kt                신규
domain/file/policy/FileContentTypeValidator.kt         신규 (Tika)
domain/file/policy/FileDispositionPolicy.kt            신규

domain/file/service/FileUploadService.kt (+impl)       신규
domain/file/service/FileDownloadService.kt (+impl)     신규
domain/file/service/impl/FileLinkPortImpl.kt           신규
domain/file/service/impl/FileUrlPortImpl.kt            신규

domain/file/link/FileLinkPort.kt                       신규 (@NamedInterface)
domain/file/link/FileUrlPort.kt                        신규 (@NamedInterface)
domain/file/link/FileSnapshot.kt                       신규 (@NamedInterface)
domain/file/access/FileAccessChecker.kt                신규 (@NamedInterface)
domain/file/access/FileAccessResolver.kt               신규

domain/file/controller/FileController.kt               신규
domain/file/controller/FileUploadExceptionAdvice.kt    신규
domain/file/dto/FileUploadResponse.kt                  신규
domain/file/exception/FileErrorCode.kt                 신규
domain/file/exception/*.kt                             BusinessException 하위 예외들

global/security/SecurityConfig.kt                      /api/v1/files/** 규칙 추가
docs/architecture/erd.md                               files Table 변경 반영
docs/development/persistence.md                        Storage 계층 설명 추가

+ src/test, src/integrationTest (§15)
```

### PR B — `feat/{issue}-member-company-file-wiring`

```
domain/member/service/impl/MemberProfileImageAccessChecker.kt    신규
domain/member/service/impl/MemberServiceImpl.kt                  profileImageFileId 수용, URL 채우기
domain/member/service/impl/MemberSearchServiceImpl.kt            URL 배치 조회
domain/member/controller/MemberProfileController.kt              Swagger 갱신
domain/member/dto/*.kt                                           요청 Field 추가

domain/company/service/impl/CompanyLogoAccessChecker.kt          신규
domain/company/service/impl/CompanyServiceImpl.kt                logoFileId 수용, URL 채우기
domain/company/dto/CompanyResponse.kt                            logoUrl 채우기
domain/company/dto/CompanyCreateRequest.kt, CompanyUpdateRequest.kt  logoFileId 추가

+ 해당 Controller/Service Test 갱신
```

---

## 17. DECISION_REQUIRED

지시서 §44와 별개로, **이번 작업에서 잠정값을 쓰거나 판단을 보류한 항목**이다. 확정되면 해당 부분만 교체한다.

| # | 항목 | 이번 처리 | 확정 시 영향 |
| --- | --- | --- | --- |
| 1 | Purpose별 허용 확장자 (§44.2) | 잠정값 (§8.1) | YAML만 교체 |
| 2 | Purpose별 허용 MIME (§44.3) | 잠정값 | YAML만 교체 |
| 3 | Purpose별 최대 크기 (§44.4) | 잠정값 | YAML만 교체 |
| 4 | Purpose별 최대 개수 (§44.5) | 잠정값 | YAML만 교체 |
| 5 | Presigned URL 유효기간 | 잠정 900초 | YAML만 교체 |
| 6 | `hwp`/`hwpx` 허용 여부와 정확한 MIME | 잠정 포함 | YAML만 교체 |
| 7 | 미연결 File 보존 시간 (§44.6) | **Phase 5로 미룸** | Cleanup PR |
| 8 | 연결 해제 후 실제 삭제 보존 기간 (§44.9) | **Phase 5로 미룸** | Cleanup PR |
| 9 | Metadata Hard Delete 정책 (§44.10) | **Phase 5로 미룸**. `ck_files_link_state`가 미연결 `DELETED`를 막으므로 함께 검토 필요 | Cleanup PR |
| 10 | 일괄 다운로드 최대 파일/용량 (§44.11) | **Phase 6로 미룸** | Archive PR |
| 11 | Virus Scan 도입 (§44.12) | **도입하지 않음**. 요구사항 확정 없음 | 별도 Issue |
| 12 | `contains_personal_information` 판단 주체 | 업로드 시 `false` 고정 | 정책 확정 시 Purpose별 기본값 또는 요청 Field |
| 13 | Rate Limit (§25) | **도입하지 않음**. §25가 "임의 도입하지 않는다" 명시 | 별도 Issue |
| 14 | Audit 연동 (§32) | **하지 않음**. 일반 사용자 다운로드까지 기록할지 미확정 | Audit 도메인 PR |

### CONTRACT_MISMATCH — 사용자 결정으로 진행

| 항목 | 내용 |
| --- | --- |
| `PATCH /api/v1/me/profile` 요청 Field | GETI Notion API 명세서는 `profileImageUrl`(String URL)을 받도록 되어 있으나, 현재 Schema는 `profile_image_file_id BIGINT`뿐이고 File 도메인이 URL이 아닌 `fileId`를 발급한다. **사용자가 `profileImageFileId` 수용을 명시적으로 선택**했다. 응답 Field 이름은 `profileImageUrl` 그대로 유지되며 값만 채워진다. Notion 명세 갱신이 필요하다 |

---

## 18. 범위 밖 (후속 PR)

| Phase | 내용 | 선행 결정 |
| --- | --- | --- |
| **Phase 5** — Cleanup | `status IN ('PENDING','FAILED','UPLOADED') AND created_at < threshold` 대상 Scheduler. Object Storage 실제 삭제, 실패 재시도 | **ShedLock 도입 여부**. 저장소에 ShedLock이 없고 `CollectorScheduler`도 중복 실행을 감수 중이다(§1.15). 파일 삭제는 되돌릴 수 없어 중복 실행 방지가 수집보다 중요하므로 별도 판단이 필요하다. §44.6 보존 시간도 함께 확정 |
| **Phase 6** — Archive | Application/Program 일괄 다운로드용 Stream/ZIP 공통 기능. Export Endpoint 자체는 해당 도메인 소유(§23). `OPERATION_RESULT` Purpose 추가 | ZIP Slip 방지, 중복·한글 파일명 처리, 메모리 미적재 Streaming, §44.11 상한 |
| Job / Program / Application / Inquiry 배선 | 각 도메인이 `FileLinkPort`를 호출하고 `FileAccessChecker`를 구현 | 각 도메인 PR. 이번에 만든 Port를 그대로 사용 |
| Portfolio | §40이 "Portfolio 전체 기능을 File PR에서 구현하지 않는다" 명시 | Portfolio 도메인 PR |

---

## 19. 착수 준비 상태

| # | 항목 | 상태 |
| --- | --- | --- |
| 1 | Epic Issue와 하위 Issue 2개 생성 | **완료** — [#84](https://github.com/inryeok-office/GETI-Server/issues/84) Epic, [#85](https://github.com/inryeok-office/GETI-Server/issues/85) File Core(PR A), [#86](https://github.com/inryeok-office/GETI-Server/issues/86) Member·Company 배선(PR B) |
| 2 | Branch 생성 | **완료** — `feature/85-file-domain-core` (base: `develop` @ `9c34e2c`). #85 Label을 `🚧 in progress`로 전환 |
| 3 | 의존성 버전 확인 | **완료** — §7.1의 검증 결과 표 참고. `build.gradle.kts`에 반영하고 `clean test build` 통과 |
| 4 | 인프라 선행 조건 | **미착수** — §14.3의 7개 항목. 코드로 해결할 수 없어 사람이 AWS 콘솔/CLI에서 수행해야 한다. 특히 **EC2 Metadata hop limit 2 상향**이 빠지면 운영에서 모든 S3 호출이 실패한다 |

인프라(4번)는 **운영 배포 시점에만 필요**하다. local은 `compose.yaml`의 MinIO를 그대로 쓰므로 4번 없이도 PR A 구현·테스트를 끝까지 진행할 수 있다.

### PR A 구현 완료

Branch `feature/85-file-domain-core`에 9개 Commit으로 구현했다.

| # | Commit | 내용 |
| --- | --- | --- |
| 1 | `docs` | 요구사항 문서와 이 명세 |
| 2 | `chore` | S3·Tika·Testcontainers MinIO 의존성 |
| 3 | `feat` | V17 Migration, `StoredFile` 확장, Enum 3종 |
| 4 | `feat` | `FileStoragePort`와 S3 Adapter, Storage 설정 |
| 5 | `feat` | 정책·파일명 정규화·MIME 검증 |
| 6 | `feat` | 업로드 API |
| 7 | `feat` | 공개 Port(`FileLinkPort`/`FileUrlPort`/`FileSnapshot`) |
| 8 | `feat` | 다운로드 API와 `FileAccessChecker`/`FileAccessResolver` |
| 9 | `test`/`docs` | Testcontainers 통합 테스트, `erd.md`·`persistence.md` 갱신 |

계획과 달라진 점:

- **`global.error.ErrorResponse`에 `@NamedInterface`를 추가했다.** `docs/development/web-api.md`가 "Domain Error Code는 그 Domain의 전용 `@ExceptionHandler`가 이 `ErrorResponse` 형식으로 변환한다"고 규정하는데 공개 선언이 빠져 있어 `ModularityTest`가 막았다. 지금까지 이 타입을 직접 만드는 Domain이 없어 드러나지 않았던 누락이다.
- **`OpenApiDocumentationTest`가 성공 응답으로 3xx도 인정하게 했다.** 302 Redirect가 정상 동작인 다운로드 API에 실제로 반환하지 않는 200을 적는 것은 "Swagger는 실제 동작과 정확히 일치해야 한다"는 규칙에 어긋난다.
- **Test Task에 `maxHeapSize = "2g"`를 지정했다.** Gradle Test Worker 기본 Heap(512MB)이 AWS SDK의 Class 그래프와 늘어난 `@WebMvcTest` Slice를 감당하지 못해 Test JVM이 `Java heap space`로 죽었다.
- **기존 `@SpringBootTest` 2개에 `app.file.storage.*`를 추가했다.** `integrationTest` Classpath에서는 `src/main/resources/application.yaml`이 우선하는데 `app.file.storage`는 Profile별 파일에만 있어 Context가 뜨지 않았다.
- **`OPERATION_RESULT` Purpose는 끝까지 추가하지 않았다.** `CoreDomainSchemaIntegrationTest`의 `async_operations.result_file_id` 경로는 유효한 Purpose 하나를 빌려 쓰고 주석으로 사유를 남겼다(Phase 6 범위).

### 다음 작업: PR B (Issue #86)

`feature/86-member-company-file-wiring` Branch에서 §12를 구현한다. PR A가 병합된 뒤 시작한다.
