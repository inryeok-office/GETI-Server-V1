[GETI File 도메인 개발 요구사항]

현재 GETI-Server의 File 도메인을 개발합니다.

File 도메인은 GETI 전체에서 사용되는 공통 파일 인프라입니다.
공고 첨부파일, 프로그램 첨부파일, 학생 지원 서류, 문의 첨부파일,
향후 포트폴리오 등에서 사용하는 파일의 업로드·저장·검증·다운로드·권한 확인·삭제를 담당합니다.

중요:
- File 도메인은 단순 S3 업로드 Utility가 아닙니다.
- 파일 Metadata, 소유자, 사용 목적, 연결 상태, 접근 권한을 관리하는 독립 도메인입니다.
- Job, Program, Application, Inquiry 등 다른 도메인이 S3 SDK나 Storage 경로를 직접 다루면 안 됩니다.
- 실제 Binary Storage와 비즈니스 Metadata를 분리합니다.
- 다른 도메인 Entity/Repository를 직접 참조하지 않습니다.
- 현재 프로젝트의 Modular Monolith / Spring Modulith / DDD 구조와 기존 코드 스타일을 그대로 따릅니다.
- 기존 Migration 파일은 수정하지 않고 새로운 Migration을 추가합니다.

==================================================
1. File 도메인의 책임
==================================================

File 도메인이 담당하는 기능:

1. 파일 업로드
2. 파일 Metadata 저장
3. 파일 원본 이름 보존
4. Object Storage 저장 Key 생성
5. 파일 크기 검증
6. 파일 확장자 검증
7. MIME Type 검증
8. 파일 실제 내용과 MIME 불일치 검증
9. FilePurpose별 업로드 정책 적용
10. 업로드 사용자 소유권 기록
11. 다른 도메인에 File ID 제공
12. 파일 연결 상태 관리
13. 다운로드 권한 검증
14. 안전한 다운로드 제공
15. 다른 도메인에서 사용할 공개 File Port 제공
16. 미연결 파일 정리
17. 실제 Object Storage 파일 삭제
18. DB Metadata와 Storage 상태 정합성 관리
19. 교사·관리자 일괄 다운로드 기능을 위한 파일 조회·Stream/Archive 기반 제공

File 도메인이 담당하지 않는 기능:

- Job의 공고 수정 권한 판단
- Program 신청 가능 여부 판단
- Application 상태 변경
- Inquiry 답변
- Portfolio 공개 여부 정책
- 누가 특정 Job/Application/Inquiry를 조회할 수 있는지에 대한 전체 비즈니스 규칙
- Discord 첨부 전송
- AI 파일 분석
- 다른 도메인의 Entity 상태 직접 변경

다른 도메인은 File ID를 사용하고,
File 도메인은 공개 계약을 통해 파일 Metadata와 Storage 기능을 제공합니다.

==================================================
2. 저장 구조
==================================================

현재 GETI Backend의 Object Storage 방향:

- MinIO
- S3 Compatible Storage

따라서 Storage 구현은 특정 환경에 강결합하지 않습니다.

권장 구조:

File Application Service
→ FileStoragePort
→ S3FileStorageAdapter
→ MinIO / S3 Compatible Object Storage

예:

interface FileStoragePort {

    fun upload(
        key: String,
        contentType: String,
        size: Long,
        inputStream: InputStream,
    )

    fun download(
        key: String,
    ): StoredFileResource

    fun delete(
        key: String,
    )

    fun exists(
        key: String,
    ): Boolean
}

AWS SDK를 사용하더라도 Domain/Application 계층에서
S3Client, PutObjectRequest 등을 직접 참조하면 안 됩니다.

==================================================
3. File Entity
==================================================

권장 Entity:

File 또는 StoredFile

필드 초안:

- id: Long
- uploaderMemberId: Long
- purpose: FilePurpose
- originalName: String
- storedName 또는 storageKey: String
- extension: String
- contentType: String
- size: Long
- status: FileStatus
- createdAt: LocalDateTime
- linkedAt: LocalDateTime?
- deletedAt: LocalDateTime?

필요 시:

- checksum: String?
- storageBucket: String
- version: Long 또는 Optimistic Lock Version

중요:

DB에는 Binary 자체를 저장하지 않습니다.

DB:
- Metadata
- Ownership
- Purpose
- Storage Key
- 상태

Object Storage:
- 실제 Binary

를 저장합니다.

originalName을 Storage Key로 직접 사용하면 안 됩니다.

잘못된 예:

uploads/한의준/자기소개서.pdf

권장:

files/{yyyy}/{MM}/{UUID}

또는

{purpose}/{UUID}

파일명이 같아도 충돌하지 않는 구조를 사용합니다.

==================================================
4. FilePurpose
==================================================

API 명세에는 File 업로드 시 다음 값을 받도록 되어 있습니다.

{
  "file": "MultipartFile",
  "purpose": "FilePurpose"
}

FilePurpose는 반드시 Enum으로 관리합니다.

다만 현재 노션에서는 정확한 최종 Enum 목록이 확정되어 있지 않으므로
개발자가 임의로 값을 확정하지 않습니다.

현재 사용처를 기준으로 예상 가능한 Purpose:

- JOB_ATTACHMENT
- PROGRAM_ATTACHMENT
- JOB_APPLICATION
- PROGRAM_APPLICATION
- INQUIRY_ATTACHMENT
- PORTFOLIO

위 이름은 최종 확정값이 아니라 설계 후보입니다.

작업 전 기존 코드·API·Migration·노션에서 이미 사용 중인 값이 있는지 먼저 확인합니다.

목적별로 다음 정책을 다르게 적용할 수 있어야 합니다.

FilePolicy:

- allowedExtensions
- allowedMimeTypes
- maxFileSize
- maxFileCount

예:

FilePolicyRegistry

FilePurpose
→ FileUploadPolicy

구조를 권장합니다.

switch/when을 Controller 여러 곳에 중복 작성하지 않습니다.

==================================================
5. 파일 업로드 API
==================================================

Endpoint:

POST /api/v1/files

권한:

- STUDENT
- TEACHER
- DEVELOPER

Headers:

Authorization: Bearer {accessToken}
Content-Type: multipart/form-data

Request:

- file: MultipartFile
- purpose: FilePurpose

현재 명세 응답:

{
  "success": true,
  "data": {
    "fileId": "Long",
    "originalName": "String",
    "contentType": "String",
    "size": "Long",
    "purpose": "FilePurpose",
    "createdAt": "LocalDateTime"
  },
  "meta": {
    "requestId": "UUID"
  }
}

HTTP:

201 Created

주요 오류:

413 Payload Too Large
- FILE_TOO_LARGE

415 Unsupported Media Type
- FILE_TYPE_NOT_ALLOWED
- MIME_MISMATCH

인증 실패:
- 프로젝트 공통 401

==================================================
6. 업로드 처리 순서
==================================================

권장 순서:

1. 인증 사용자 확인
2. Multipart File 존재 여부
3. 빈 파일 여부
4. FilePurpose 유효성
5. 목적별 최대 크기 검사
6. originalFilename 정규화
7. 확장자 검사
8. 선언된 Content-Type 검사
9. 실제 파일 Content/Magic Number 검사
10. 저장 Key 생성
11. Object Storage 업로드
12. File Metadata 저장
13. File ID 반환

단순히 아래 값만 믿으면 안 됩니다.

MultipartFile.originalFilename
MultipartFile.contentType

이 값은 클라이언트가 임의 조작할 수 있습니다.

예:

malware.exe
→ 이름만 resume.pdf로 변경
→ Content-Type application/pdf 전송

같은 입력을 최소한 확장자 + MIME + 실제 Signature의 조합으로 방어합니다.

==================================================
7. 확장자·MIME 검증
==================================================

노션 확정 정책:

- 목적별 허용 확장자 검사
- MIME Type 검사
- 최대 파일 크기 검사
- 최대 파일 개수 검사
- 실제 파일 형식도 서버에서 검증

따라서 다음 구조를 권장합니다.

data class FileUploadPolicy(
    val allowedExtensions: Set<String>,
    val allowedMimeTypes: Set<String>,
    val maxSizeBytes: Long,
    val maxCount: Int,
)

주의:

현재 노션에서 각 Purpose의 정확한 확장자·MIME·크기·개수 숫자는 확정되지 않았습니다.

임의로 다음과 같은 정책을 확정하지 않습니다.

PDF만 허용
10MB
5개

등.

해당 값은 DECISION_REQUIRED로 보고하고,
구조만 설정 가능하게 구현합니다.

설정값으로 분리하는 방식도 가능:

file:
  policies:
    job-application:
      max-size: ...
      extensions: ...
      mime-types: ...

다만 Configuration이 지나치게 복잡하면 Enum 기반 Policy Registry로 시작해도 됩니다.

==================================================
8. MIME_MISMATCH
==================================================

API 명세에 다음 오류가 이미 존재합니다.

MIME_MISMATCH

따라서 단순 확장자 검증만 구현하면 안 됩니다.

검증 예:

extension = pdf
clientContentType = application/pdf
detectedContentType = application/pdf

세 값의 관계를 검증합니다.

실제 파일 타입 탐지는 기존 Tech Stack에 Apache Tika가 있다면 재사용을 우선 검토합니다.

단, 프로젝트 의존성에 아직 Tika가 없다면
File 도메인 하나를 위해 과도한 의존성을 추가하기 전에 현재 Stack과 범위를 확인합니다.

파일 전체를 메모리에 올려 타입을 검사하지 않도록 주의합니다.

==================================================
9. 업로드 파일 이름 처리
==================================================

원본 파일명은 사용자 표시용으로 보존합니다.

하지만 Storage Key에는 사용하지 않습니다.

originalName 처리 시:

- path traversal 제거
- null 또는 blank 방어
- 지나치게 긴 파일명 제한
- 제어 문자 제거
- Windows/Unix Path 구분자 제거

예:

../../secret.txt

C:\Users\user\secret.pdf

등을 그대로 저장 Key로 사용하지 않습니다.

==================================================
10. 업로드와 DB Transaction
==================================================

DB와 Object Storage는 하나의 ACID Transaction이 아닙니다.

따라서 다음 실패를 고려합니다.

Case A:
Storage upload 성공
→ DB save 실패

Case B:
DB save 성공
→ Storage upload 실패

권장 기본 흐름:

Storage 업로드
→ Metadata 저장

DB 저장 실패 시:

best-effort Storage 삭제

또는 상태 기반 보상 처리를 사용합니다.

중요한 것은 Storage와 DB의 orphan을 무시하지 않는 것입니다.

미연결 파일 정리 Scheduler가 존재하더라도
명확한 실패 보상 처리를 우선합니다.

==================================================
11. 파일 연결 상태
==================================================

GETI 파일 업로드는 일반적으로 다음 두 단계입니다.

1. 파일 먼저 업로드
2. 반환받은 fileId를 Job/Program/Application/Inquiry 등에 연결

따라서 업로드 직후 File은 아직 특정 Resource에 연결되지 않을 수 있습니다.

권장 상태:

FileStatus:

UPLOADED
LINKED
DELETED

또는:

isLinked + linkedAt

하지만 상태 Enum 쪽이 향후 확장성이 높습니다.

흐름:

POST /api/v1/files
→ UPLOADED

Job/Application/Program/Inquiry가 fileId 사용
→ File 도메인의 Attach/Claim Port 호출
→ LINKED

미연결 상태로 일정 시간이 지나면
정리 대상이 됩니다.

==================================================
12. 다른 도메인이 파일을 연결하는 방법
==================================================

금지:

JobService
→ FileRepository 직접 조회

ProgramService
→ FileEntity 직접 수정

ApplicationService
→ S3Client 직접 호출

권장:

FileUseCase / Public Port

예:

interface FileLinkPort {

    fun validateAndLink(
        requesterId: Long,
        fileIds: Collection<Long>,
        purpose: FilePurpose,
        target: FileTarget,
    ): List<FileSnapshot>
}

또는 공개 Port를 목적별로 더 작게 분리할 수 있습니다.

검증할 내용:

- File 존재
- DELETED가 아님
- 현재 사용자가 업로드한 File인지
- Expected FilePurpose와 실제 Purpose 일치
- 이미 다른 Resource에서 사용 중인지
- 중복 File ID인지
- 해당 Resource의 최대 첨부 개수 초과 여부

단, File 도메인이 대상 Job/Application의 비즈니스 권한을 판정하면 안 됩니다.

예:

Application 도메인:
“이 학생이 이 Application을 수정 가능한가?”

File 도메인:
“이 File이 이 학생 소유이며 JOB_APPLICATION 용도인가?”

역할을 분리합니다.

==================================================
13. File과 Target 관계
==================================================

File이 어떤 Resource에 연결됐는지 추적할 필요가 있습니다.

가능한 설계:

A.
files에 직접

targetType
targetId

저장

B.
file_links 테이블 사용

권장:

file_links 분리 검토

예:

file_links

- id
- fileId
- targetType
- targetId
- createdAt

FileTargetType 후보:

JOB
PROGRAM
JOB_APPLICATION
PROGRAM_APPLICATION
INQUIRY
PORTFOLIO

장점:

- File Entity가 특정 도메인 FK를 갖지 않음
- 여러 도메인의 DB Entity에 직접 의존하지 않음
- 공통 File 도메인 유지
- 연결 이력 추적 가능

다만 “한 File은 반드시 한 Resource에만 연결”이라는 정책이면
files 내부 targetType/targetId로 단순화할 수도 있습니다.

작업 전에 현재 ERD 및 사용처를 확인합니다.

==================================================
14. 다른 사용자 File ID 탈취 방지
==================================================

중요한 보안 요구사항입니다.

학생 A:

POST /api/v1/files
→ fileId = 100

학생 B가 다음 요청을 보내면:

{
  "fileIds": [100]
}

자신의 Application에 연결되면 안 됩니다.

반드시 uploaderMemberId 또는 파일 소유권을 검사합니다.

File ID는 단순 숫자 식별자이지 권한 증명이 아닙니다.

다음도 검사합니다.

- 다른 사용자 파일 연결
- 다른 사용자의 미연결 파일 다운로드
- 이미 사용된 파일 재사용
- 다른 Purpose의 파일 연결

==================================================
15. 다운로드 API
==================================================

Endpoint:

GET /api/v1/files/{fileId}/download

권한:

- STUDENT
- TEACHER
- DEVELOPER

그러나 로그인했다고 모든 파일을 다운로드할 수 있는 것이 아닙니다.

파일별 접근 권한 검사가 반드시 필요합니다.

현재 명세:

Response Body:
- File Binary

또는

302 Redirect

Header:
- Location

또는

Content-Disposition

HTTP:

302 Found
404 FILE_NOT_FOUND
403 FILE_ACCESS_DENIED

다운로드 구현 선택:

A. Backend Proxy Streaming
B. 짧은 유효기간 Presigned URL 발급

둘 다 명세와 호환 가능합니다.

Object Storage 사용 구조에서는
Presigned URL을 우선 검토할 수 있습니다.

단:

- Bucket은 Public이면 안 됨
- URL은 제한된 시간만 유효
- fileId만 알면 URL을 만들 수 있으면 안 됨
- URL 발급 전에 권한 검증

==================================================
16. 다운로드 권한
==================================================

File 도메인이 모든 도메인의 권한 정책을 직접 알면 안 됩니다.

예:

Job 공고 첨부:
- 해당 Job을 볼 수 있는 사용자

Application 파일:
- 작성 학생
- 해당 공고 관리 권한이 있는 교사
- 필요한 운영 권한의 DEVELOPER

Inquiry 첨부:
- 문의 작성자
- 해당 문의 관리 권한이 있는 DEVELOPER 등

Program 파일:
- 해당 Program 접근 가능 사용자

따라서 File은 대상 Resource의 접근 가능 여부를 공개 Query Port로 확인하거나,
각 Resource 도메인이 File Download Permission을 제공하는 구조를 사용합니다.

잘못된 구조:

FileService
→ JobRepository
→ ProgramRepository
→ ApplicationRepository
→ InquiryRepository

이렇게 모든 Repository를 직접 참조하면 안 됩니다.

권장:

FileAccessResolver

또는

도메인별 Public Query Port Registry

예:

FileAccessPolicyResolver
→ JobFileAccessQueryPort
→ ProgramFileAccessQueryPort
→ ApplicationFileAccessQueryPort
→ InquiryFileAccessQueryPort

Modulith 의존 방향과 기존 Named Interface 패턴을 반드시 확인합니다.

==================================================
17. Storage Bucket 공개 금지
==================================================

Object Storage Bucket은 Public Access로 만들지 않습니다.

금지:

https://storage.example.com/files/resume.pdf

와 같은 영구 Public URL 반환.

API Response에는 Storage Key, Bucket 내부 경로 등을 최대한 노출하지 않습니다.

기본 파일 Response:

- fileId
- originalName
- contentType
- size
- purpose
- createdAt

정도로 제한합니다.

==================================================
18. 파일 실제 삭제
==================================================

File 도메인 설명에 “실제 삭제”가 명시되어 있습니다.

하지만 Resource 삭제와 동시에 무조건 File Binary를 삭제하면 안 됩니다.

예:

Application 이력 보존
Program 삭제 이력 보존
Job 지원 이력 보존

같은 정책이 존재할 수 있기 때문입니다.

따라서:

Resource 삭제
≠ File 즉시 물리 삭제

로 봅니다.

실제 Storage 삭제 조건은 다음을 구분합니다.

1. 미연결 Upload
2. 연결 해제되어 더 이상 참조되지 않는 File
3. 명시적인 File 삭제
4. 보존 기간이 지난 File

다른 Resource가 참조 중인 File은 삭제하면 안 됩니다.

==================================================
19. 미연결 파일 Cleanup
==================================================

PRD 확정 사항:

- 미연결 파일 정리
- 매일 1회
- 보존 시간이 지난 파일만 정리

따라서 Scheduler에서:

status = UPLOADED
AND createdAt < cleanupThreshold

대상만 조회합니다.

처리:

1. Cleanup 후보 조회
2. 해당 File이 실제 미연결 상태인지 다시 확인
3. Object Storage 삭제
4. Metadata 삭제 또는 DELETED 처리
5. 실패 로그 및 재시도 가능 구조

다른 서버 Instance와 중복 Cleanup을 방지합니다.

현재 Scheduler에서 ShedLock을 사용하므로 프로젝트 패턴을 확인합니다.

“업로드 직후 미사용” 파일을 바로 삭제하면 안 됩니다.

정확한 미연결 보존 시간은 현재 문서에서 확정되지 않았으므로
임의 숫자를 정하지 말고 설정 가능하게 두며 DECISION_REQUIRED로 보고합니다.

==================================================
20. File 삭제 API
==================================================

현재 /api/v1/files API 명세에는 명시적인 DELETE Endpoint가 없습니다.

따라서 사용자용 다음 API를 임의로 추가하지 않습니다.

DELETE /api/v1/files/{fileId}

다른 도메인의 첨부 파일 제거는
Resource 수정 API에서 fileIds 연결을 갱신하는 구조를 우선합니다.

File 도메인 내부적으로는 Storage 실제 삭제 UseCase가 필요합니다.

외부 DELETE API가 필요하다고 판단되면
새 API를 만들기 전에 요구사항으로 보고합니다.

==================================================
21. 파일 교체
==================================================

Application 수정 등에서는 기존 파일을 새 파일로 교체할 수 있습니다.

권장:

새 File 업로드
→ 새 fileId
→ Resource 수정 시 새 File 연결
→ 기존 File 연결 해제

기존 Binary를 같은 File ID로 덮어쓰지 않습니다.

이유:

- 이력 보존
- 캐시
- 감사
- 지원서 이전 버전

File ID는 업로드 당시 Binary를 식별하는 immutable 개념으로 보는 편이 안전합니다.

==================================================
22. File Metadata Snapshot
==================================================

다른 도메인이 File Entity를 직접 반환받으면 안 됩니다.

공개 Snapshot:

data class FileSnapshot(
    val fileId: Long,
    val originalName: String,
    val contentType: String,
    val size: Long,
)

정도만 제공합니다.

다른 도메인에 다음을 노출하지 않습니다.

- storageKey
- bucket
- uploader 내부 Entity
- S3 SDK 객체
- Hibernate Entity
- Repository

==================================================
23. 일괄 다운로드
==================================================

기능명세에는 교사·관리자 화면에서
선택 파일 일괄 다운로드가 필요합니다.

또한 Application·Program 관련 API에는
application/zip Binary 또는 Location 방식의 일괄 다운로드 계약이 존재합니다.

중요:

“어떤 신청자를 선택할 수 있는가”
“교사가 해당 지원자 파일을 볼 수 있는가”

는 Application/Program 도메인이 판단합니다.

File 도메인은 다음 공통 기능을 제공할 수 있습니다.

- 권한 검증된 File 목록 조회
- Storage Stream 획득
- ZIP 생성 지원
- Archive 파일명 안전화

다만 ZIP Export의 API Endpoint 소유권까지 File로 가져오지 않습니다.

예:

GET /api/v1/admin/jobs/{jobId}/applications/export

같은 API는 Application 도메인 소유로 유지하고
내부적으로 File 공개 Port를 사용합니다.

ZIP 생성 시 주의:

- 전체 파일 메모리 적재 금지
- Streaming
- Zip Slip 방지
- 중복 파일명 처리
- 한글 파일명
- 매우 큰 Archive 처리

==================================================
24. 파일 개수 검증
==================================================

노션에서는 목적별 최대 File 개수도 검사하도록 되어 있습니다.

주의:

POST /api/v1/files는 파일 하나를 업로드하기 때문에
단일 업로드 시점에는 해당 Resource가 아직 존재하지 않을 수 있습니다.

따라서 maxFileCount 검증은 두 위치로 구분할 수 있습니다.

Upload:
- 개별 파일 자체 정책 검증

Resource Link:
- Job/Application/Inquiry에 File 연결 시 전체 개수 검증

예:

File 10개를 각각 업로드하는 것 자체는 성공
→ Application에 허용 개수가 3개이면
→ 네 번째 File 연결 시 거부

정확한 정책은 목적별로 적용합니다.

==================================================
25. 고아 파일과 미사용 File ID 공격
==================================================

클라이언트가 파일을 수백 개 업로드하고 실제 Resource에 연결하지 않는 경우를 고려합니다.

방어:

- Authentication 필수
- 파일 크기 제한
- Purpose 제한
- 미연결 Cleanup
- 필요 시 사용자별 Upload Rate Limit
- 필요 시 미연결 파일 개수 제한

Rate Limit의 정확한 값은 현재 확정 요구사항이 없으므로
이번 작업에서 임의 도입하지 않습니다.

==================================================
26. Error Code
==================================================

현재 확정:

FILE_TOO_LARGE
FILE_TYPE_NOT_ALLOWED
MIME_MISMATCH
FILE_NOT_FOUND
FILE_ACCESS_DENIED

추가로 필요할 가능성이 있는 내부/공개 Error Code:

FILE_EMPTY
INVALID_FILE_NAME
INVALID_FILE_PURPOSE
FILE_NOT_OWNED
FILE_ALREADY_LINKED
FILE_PURPOSE_MISMATCH
FILE_COUNT_EXCEEDED
FILE_STORAGE_ERROR

기존 프로젝트의 ErrorCode Naming/Exception 구조를 확인한 후 최소한으로 추가합니다.

같은 상황에 비슷한 ErrorCode를 여러 개 만들지 않습니다.

Storage SDK의 예외 메시지를 사용자에게 그대로 노출하지 않습니다.

==================================================
27. File Security
==================================================

반드시 검토:

- Path Traversal
- MIME Spoofing
- Double Extension
  resume.pdf.exe
- Null Byte 계열 파일명
- 빈 파일
- 파일명 과다 길이
- IDOR 다운로드
- 다른 사용자의 File ID 연결
- Public Bucket
- 영구 Presigned URL
- Storage Key 노출
- Content-Disposition Header Injection
- ZIP Slip
- 대용량 파일 Memory Exhaustion
- Content-Type Sniffing
- 실행 가능 파일 업로드

서버가 업로드 파일을 실행하거나 자동으로 렌더링하면 안 됩니다.

가능하면 다운로드 Response에 안전한 Content-Disposition을 사용합니다.

==================================================
28. Content-Disposition
==================================================

다운로드 시 originalName을 Header에 넣을 경우
Header Injection이 발생하지 않도록 안전하게 Encoding합니다.

권장:

Content-Disposition: attachment

브라우저에서 PDF/HTML/SVG 등을 무조건 inline 렌더링하는 정책은
XSS 또는 사용자 혼동 위험이 있으므로 신중하게 적용합니다.

기본적으로 다운로드 파일은 attachment 처리가 안전합니다.

==================================================
29. 파일 Checksum
==================================================

필수 노션 요구사항은 아니지만 다음 용도로 checksum을 검토할 수 있습니다.

- 업로드 무결성
- Storage 정합성
- 같은 Binary 탐지
- 테스트

하지만 현재 요구사항은 중복 파일 제거를 요구하지 않습니다.

Checksum이 있다고 해서 동일 파일 Upload를 자동 Dedup하지 않습니다.

MVP에서는 필요성이 없다면 제외 가능합니다.

==================================================
30. Storage 장애 처리
==================================================

Object Storage 장애가 발생하면:

업로드:
→ 요청 실패
→ FILE_STORAGE_ERROR 계열

다운로드:
→ File Metadata는 있지만 Storage Binary가 없음
→ 내부 오류 또는 Storage Missing 상태 기록

다음 상태는 관측 가능해야 합니다.

DB Metadata exists
Storage Object missing

Storage Object exists
DB Metadata missing

후자의 경우 Cleanup 또는 운영 도구에서 정리할 수 있어야 합니다.

Storage Key Not Found를 무조건 FILE_NOT_FOUND로 변환하면
DB/Storage 정합성 장애가 숨겨질 수 있으므로 로그에서는 구분합니다.

==================================================
31. Logging
==================================================

로그에 허용:

- fileId
- purpose
- size
- uploaderMemberId
- 처리 결과
- Storage operation type

주의해서 처리:

- originalName

로그 금지:

- 파일 Binary
- Presigned URL 전체
- Access Key
- Secret Key
- Authorization Header
- 민감한 지원서 내용
- 파일에서 추출한 본문

==================================================
32. Audit
==================================================

GETI에는 Audit 도메인이 별도로 존재합니다.

필요한 경우 다음 운영 행위는 Audit과 연결할 수 있습니다.

- 관리자 File 다운로드
- 일괄 다운로드
- 관리자 실제 File 삭제

일반 학생 자신의 파일 다운로드까지 전부 Audit Log에 저장할지는
현재 확정사항이 아니므로 임의 구현하지 않습니다.

==================================================
33. Storage 설정
==================================================

환경변수 예시:

FILE_STORAGE_ENDPOINT=
FILE_STORAGE_REGION=
FILE_STORAGE_BUCKET=
FILE_STORAGE_ACCESS_KEY=
FILE_STORAGE_SECRET_KEY=
FILE_STORAGE_PATH_STYLE_ACCESS=true

또는 기존 프로젝트 S3/MinIO 환경변수 Naming을 그대로 사용합니다.

주의:

- 실제 Secret 코드 Commit 금지
- application.yml에 실제 값 금지
- 테스트에서는 Testcontainers MinIO 또는 Fake Storage Adapter 검토
- Production Bucket Public 설정 금지

이미 Storage 관련 환경변수가 있으면 새 이름을 중복 생성하지 않습니다.

==================================================
34. 테스트 요구사항
==================================================

필수 Unit Test:

업로드:
- 정상 파일
- 빈 파일
- 최대 크기 초과
- 허용되지 않은 확장자
- 허용되지 않은 MIME
- 확장자·MIME 불일치
- 잘못된 FilePurpose
- 파일명 정규화
- Storage upload 실패
- Metadata save 실패 후 보상 처리

소유권:
- 본인 File 연결 성공
- 다른 사용자 File 연결 실패
- Purpose 불일치
- 이미 연결된 File
- File ID 중복 전달

다운로드:
- 본인 접근
- 권한 있는 관리자 접근
- 권한 없는 사용자 접근
- 존재하지 않는 File
- DELETED File
- Storage Object 없음

Cleanup:
- 오래된 미연결 파일 삭제
- 최신 미연결 파일 유지
- 연결된 파일 유지
- Storage 삭제 실패
- 중복 Scheduler 실행 방지

Integration Test 권장:

- PostgreSQL + Object Storage
- 실제 Multipart Upload
- Storage Object 생성 확인
- DB Metadata 확인
- Presigned URL 또는 Binary Download
- Object 삭제
- Flyway Migration
- 권한 API

==================================================
35. Controller Test
==================================================

POST /api/v1/files:

- 401 Unauthorized
- STUDENT 성공
- TEACHER 성공
- DEVELOPER 성공
- multipart/form-data
- 파일 누락
- purpose 누락
- FILE_TOO_LARGE
- FILE_TYPE_NOT_ALLOWED
- MIME_MISMATCH
- 201 Created
- 공통 ApiResponse

GET /api/v1/files/{fileId}/download:

- 401
- FILE_NOT_FOUND
- FILE_ACCESS_DENIED
- 정상 Redirect 또는 Binary
- Header 확인

==================================================
36. Swagger
==================================================

Swagger에는 실제 API 동작과 정확히 일치하게 문서화합니다.

Upload:

POST /api/v1/files
Content-Type: multipart/form-data

필수:

file
purpose

Response:

fileId
originalName
contentType
size
purpose
createdAt

Download:

GET /api/v1/files/{fileId}/download

Binary 또는 Redirect 방식에 맞는 Response 설명

Error:

FILE_TOO_LARGE
FILE_TYPE_NOT_ALLOWED
MIME_MISMATCH
FILE_NOT_FOUND
FILE_ACCESS_DENIED

현재 지원하지 않는 FilePurpose를 Swagger Enum에 먼저 추가하지 않습니다.

==================================================
37. DB Migration
==================================================

필요한 Table 예시:

files

- id
- uploader_member_id
- purpose
- original_name
- storage_key
- extension
- content_type
- size
- status
- linked_at
- deleted_at
- created_at
- updated_at

필요 시:

file_links

- id
- file_id
- target_type
- target_id
- created_at

Constraint:

- size >= 0
- storage_key UNIQUE
- 목적·상태는 Enum 문자열
- FK가 필요하다면 모듈 경계를 고려

인덱스:

- uploader_member_id
- status
- purpose
- created_at
- Cleanup 조회용 (status, created_at)

기존 Migration 파일 변경 금지.

==================================================
38. 실제 삭제와 DB Row 보존
==================================================

File 도메인의 “실제 삭제”는 Object Storage Binary 삭제를 의미할 수 있으나
DB Metadata까지 즉시 Hard Delete해야 한다는 뜻은 아닙니다.

운영 추적을 위해:

status = DELETED
deletedAt

을 남기는 방식을 우선 검토합니다.

미연결 임시 File처럼 감사 가치가 거의 없는 데이터는
Cleanup 후 Metadata까지 Hard Delete할 수도 있습니다.

이 정책은 구분해서 구현합니다.

==================================================
39. 삭제된 원본 Resource와 File
==================================================

Job/Program 등이 Soft Delete돼도
지원 또는 운영 이력이 남는다면 File을 바로 제거하면 안 됩니다.

예:

Program DELETED
→ 기존 신청 데이터 유지

Job DELETED
→ 기존 Application 이력 유지

따라서 “Target가 DELETED면 File 삭제” 같은 단순 Cascade를 넣지 않습니다.

File 보존 정책은 해당 Resource의 보존 정책과 연결해야 합니다.

==================================================
40. Portfolio
==================================================

File 도메인 설명에는 Portfolio도 포함되어 있지만
현재 Portfolio 기능 자체는 정책 확정 전 후속 범위일 수 있습니다.

따라서 File 구조는 PORTFOLIO Purpose 확장이 가능해야 하지만,
Portfolio 전체 기능을 File PR에서 구현하지 않습니다.

==================================================
41. 개발 순서
==================================================

권장 순서:

Phase 1 — File Core

- File Entity
- FilePurpose
- FileStatus
- Repository
- Migration
- FileStoragePort
- S3-compatible Adapter
- Metadata

Phase 2 — Upload

- POST /api/v1/files
- Multipart
- Size Validation
- Extension Validation
- MIME Validation
- Storage Upload
- Error Handling
- Swagger
- Tests

Phase 3 — Link/Ownership

- 공개 File Port
- 소유권 검증
- Purpose 검증
- File Link
- Unlink
- Snapshot
- 다른 도메인 연동 준비

Phase 4 — Download

- GET /api/v1/files/{fileId}/download
- Access Policy
- Presigned URL 또는 Streaming
- 권한 테스트

Phase 5 — Cleanup

- 미연결 File Scheduler
- Cleanup Threshold
- Object Storage 실제 삭제
- 장애 대응
- Scheduler Lock

Phase 6 — Archive Support

- Application/Program 일괄 다운로드를 위한
  Stream/Archive 공통 기능
- 실제 Export Endpoint는 해당 비즈니스 도메인에 유지

한 PR로 모든 기능을 억지로 구현하지 않아도 됩니다.

==================================================
42. 절대 하지 말아야 할 것
==================================================

- Bucket Public 설정
- Storage Key를 API에 그대로 반환
- originalFilename을 Storage Key로 사용
- originalFilename 신뢰
- Multipart Content-Type만 검사
- 다른 도메인 Repository 직접 참조
- 다른 도메인에서 S3Client 직접 사용
- File Entity를 다른 도메인에 전달
- 사용자 A File을 사용자 B가 연결
- fileId만 확인하고 다운로드 허용
- 파일 전체를 메모리에 읽어 Upload/ZIP 처리
- Resource Soft Delete와 함께 File 자동 Hard Delete
- 기존 File ID Binary 덮어쓰기
- 미연결 File 즉시 삭제
- FilePurpose별 정책 숫자 임의 확정
- 기존 Migration 수정
- Secret Commit
- Storage SDK 예외를 그대로 API에 노출
- Presigned URL 영구 저장
- 문의 첨부파일 직접 URL을 Discord에 포함

==================================================
43. 작업 전 반드시 확인
==================================================

구현 전 현재 Repository를 분석하여 다음을 확인합니다.

1. 기존 File Entity 존재 여부
2. 기존 S3/MinIO Config
3. AWS SDK 의존성
4. 기존 Storage Adapter
5. 환경변수 Naming
6. 최신 Flyway Migration 번호
7. Member ID 참조 방식
8. Job fileIds 구현 상태
9. Program fileIds 구현 상태
10. Application fileIds 구현 상태
11. Inquiry fileIds 구현 상태
12. Portfolio 현재 범위
13. 다른 도메인의 File Entity 직접 참조 여부
14. 현재 일괄 다운로드 구현 여부
15. Scheduler/ShedLock 사용 패턴
16. Apache Tika 의존성 존재 여부
17. 프로젝트의 Named Interface/Public Port 패턴
18. Testcontainers MinIO 사용 여부
19. 최대 Multipart 크기 Spring 설정
20. 기존 ErrorCode

이미 존재하는 공통 기능은 재구현하지 않습니다.

==================================================
44. 현재 문서상 DECISION_REQUIRED
==================================================

다음은 노션에 개념은 있지만 구체적인 숫자/값이 확정되지 않았습니다.

임의로 결정하지 말고 구현 전 또는 PR에서 보고합니다.

1. FilePurpose 최종 Enum 목록
2. Purpose별 허용 확장자
3. Purpose별 허용 MIME
4. Purpose별 단일 파일 최대 크기
5. Purpose별 최대 파일 개수
6. 미연결 File 보존 시간
7. 다운로드 구현:
   - Presigned URL
   - Backend Streaming
8. File과 Target 관계:
   - files에 targetType/targetId
   - file_links 별도 Table
9. Linked File 연결 해제 후 실제 삭제 보존 기간
10. File Metadata Hard Delete 정책
11. 관리자 일괄 다운로드 최대 파일/전체 용량
12. Virus Scan 도입 여부

위 값이 필요해 작업이 막히는 경우
임의 결정하지 않고 DECISION_REQUIRED로 먼저 공유합니다.

==================================================
45. 완료 조건
==================================================

다음 조건을 만족하면 File 도메인 기본 개발 완료로 봅니다.

- 인증 사용자가 File을 업로드할 수 있다.
- Multipart 업로드가 API 명세와 일치한다.
- File Metadata가 DB에 저장된다.
- Binary가 S3-compatible Storage에 저장된다.
- Original Filename과 Storage Key가 분리된다.
- Purpose를 저장한다.
- Size를 검증한다.
- Extension을 검증한다.
- MIME을 검증한다.
- 실제 파일 형식 불일치를 방지한다.
- 다른 사용자 파일 탈취가 불가능하다.
- 다른 도메인은 공개 File Port만 사용한다.
- 권한 확인 후 File을 다운로드할 수 있다.
- Public Bucket을 사용하지 않는다.
- Storage 내부 경로를 외부에 노출하지 않는다.
- 미연결 File Cleanup 구조가 존재한다.
- DB/Storage 실패 정합성을 처리한다.
- Unit Test가 존재한다.
- Controller Test가 존재한다.
- Integration Test가 존재한다.
- Swagger가 실제 구현과 일치한다.
- Migration은 신규 파일로 추가한다.
- Architecture/Modulith Test가 통과한다.
- 전체 ./gradlew test가 통과한다.
- integrationTest가 있다면 통과한다.
- clean build가 통과한다.