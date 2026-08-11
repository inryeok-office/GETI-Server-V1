[GETI Server - Discord Bot 연동을 위한 Notification 후속 개발 요구사항]

대상 Repository:
inryeok-office/GETI-Server-V1

작업 기준:
develop 최신 브랜치

전제:
별도 Repository `inryeok-office/GETI-Bot-V1`에서 Discord Bot 핵심 기능 개발이 완료된 이후 작업한다.

이번 작업의 목적은 GETI Server가 Discord SDK/Webhook을 직접 다루는 것이 아니라,
Notification 도메인이 Discord Delivery 상태와 재시도를 관리하고
별도 GETI-Bot-V1 서비스의 Internal REST API를 호출하도록 서버 구조를 완성하는 것이다.

==================================================
1. 변경 배경
==================================================

현재 develop 기준 Notification Core는 인앱 알림 조회/읽음 처리까지만 구현되어 있다.

현재 Notification 후속 계획에도 다음이 별도 범위로 남아 있다.

- discord_deliveries
- discord_delivery_attempts
- DiscordBotClient Port
- FakeDiscordBotClient
- Worker
- 자동/수동 재시도
- Discord 상태 조회 API
- Program의 기존 Discord 상태 정리
- Job/Program/Inquiry Event 연결
- 실제 Discord Bot 연동

따라서 현재 구조는 Discord Bot 분리 아키텍처를 수용할 수 있지만,
실제 HTTP 연동 코드와 Delivery Persistence가 아직 없다.

이번 작업은 이 후속 범위를 실제 GETI-Bot-V1 계약에 맞춰 구현한다.

==================================================
2. 최종 Architecture
==================================================

전체 흐름은 다음으로 통일한다.

Job / Program / Inquiry
        ↓
Domain Event
        ↓
@TransactionalEventListener(AFTER_COMMIT)
        ↓
Notification
        ↓
DiscordDelivery 저장
        ↓
DiscordDeliveryWorker
        ↓
DiscordBotClient Port
        ↓
HttpDiscordBotClient
        ↓
GETI-Bot-V1 Internal REST API
        ↓
discord.js
        ↓
Discord API

핵심 원칙:

- Job/Program/Inquiry는 GETI-Bot-V1을 직접 호출하지 않는다.
- Job/Program/Inquiry는 Discord HTTP Client에 의존하지 않는다.
- Notification이 Discord Delivery의 Source of Truth다.
- Bot은 GETI DB에 접근하지 않는다.
- Discord 장애로 원본 비즈니스 Transaction을 Rollback하지 않는다.

==================================================
3. 서버 ↔ Bot 책임 분리
==================================================

GETI Server가 담당:

- 어떤 이벤트를 Discord로 보낼지 판단
- targetType / targetId
- action
- template
- channelId
- mentionRoleIds
- 의미 데이터(Semantic DTO)
- Delivery 상태
- Retry 횟수
- 다음 Retry 시각
- Idempotency Key 생성
- Discord messageId 저장
- 실패 이력
- 수동 Retry 권한

GETI Bot이 담당:

- HTTP Request Validation
- Internal API 인증
- Discord Embed Rendering
- Discord Message CREATE
- Discord Message UPDATE
- CLOSE_NOTICE 표현
- DELETE_NOTICE 표현
- Role Mention
- allowedMentions
- Discord Error Mapping
- Bot process 내 Idempotency

Server에서 Discord Embed JSON을 직접 만들지 않는다.

금지:

DiscordEmbed
EmbedBuilder
Discord Field/Color 직접 생성
Discord Markdown Formatting을 서버에서 수행

Server는 의미 데이터만 전달한다.

==================================================
4. Bot Internal API 계약
==================================================

Bot의 확정 구현을 우선 Source of Truth로 삼는다.

기본 Endpoint:

POST /internal/v1/discord/messages

PATCH /internal/v1/discord/messages/{messageId}

POST:
CREATE 전용

PATCH:
UPDATE
CLOSE_NOTICE
DELETE_NOTICE

별도:

/close
/delete-notice

Endpoint는 만들지 않는다.

실제 Bot Phase 1~4 완료 후 Request/Response Schema를 반드시 확인하고
그 계약과 1:1로 맞춘다.

추측 DTO를 만들지 않는다.

==================================================
5. Internal HTTP Headers
==================================================

Server → Bot 요청 시 최소 다음 Header를 사용한다.

X-Internal-Api-Key
X-Request-Id
X-Idempotency-Key

역할:

X-Internal-Api-Key
- Service-to-Service 인증

X-Request-Id
- 요청 Trace

X-Idempotency-Key
- 동일 Logical Discord Action 중복 수행 방지

Bot의 Header Validation 계약을 그대로 따른다.

==================================================
6. 환경변수
==================================================

Server에는 Bot 호출용 환경변수를 추가한다.

권장:

DISCORD_BOT_BASE_URL
DISCORD_BOT_INTERNAL_API_KEY

실제 이름은 기존 configuration naming convention을 확인해 확정한다.

예:

DISCORD_BOT_BASE_URL=http://geti-bot:3000
DISCORD_BOT_INTERNAL_API_KEY=<secret>

중요:

실제 Secret 값을:

application.yml
application-prod.yml
.env.example
README
PR
Issue

등에 작성하지 않는다.

.env.example이나 문서에는 Placeholder만 남긴다.

GETI-Bot-V1의:

GETI_INTERNAL_API_KEY

와 Server의:

DISCORD_BOT_INTERNAL_API_KEY

는 같은 Secret 값을 사용하되,
각 서비스 관점의 환경변수 이름은 달라도 된다.

==================================================
7. Discord Delivery 상태
==================================================

공통 상태를 Notification 도메인이 소유한다.

DiscordDeliveryStatus:

PENDING
PROCESSING
DELIVERED
FAILED

정확한 의미:

PENDING
- Delivery 생성됨
- 아직 Worker 처리 전

PROCESSING
- Worker가 현재 전송 처리 중

DELIVERED
- Bot이 Discord 작업을 성공 완료

FAILED
- 허용된 자동 Retry를 모두 소진했거나 재시도 불가능 오류

다음 기존 상태를 신규 구조에 사용하지 않는다.

Program:
SUCCESS
FAILED
SKIPPED

Collector:
PENDING
SENDING
SENT
FAILED

Program의 기존 Enum은 새 공통 Notification 계약으로 교체한다.

현재 develop의 Program 코드 주석에도
새 공통 계약 PENDING/PROCESSING/DELIVERED/FAILED로 교체해야 한다고 명시되어 있다.

==================================================
8. DiscordDelivery Entity
==================================================

Notification 도메인에 DiscordDelivery를 구현한다.

권장 필드:

id: Long

targetType: DiscordDeliveryTargetType

targetId: Long

action: DiscordDeliveryAction

template: DiscordMessageTemplate

status: DiscordDeliveryStatus

channelId: String

discordMessageId: String?

idempotencyKey: String

automaticRetryCount: Int

manualRetryCount: Int

nextRetryAt: LocalDateTime?

lastErrorCode: String?

lastErrorMessage: String?

createdAt: LocalDateTime

updatedAt: LocalDateTime

필요하면:

lastAttemptAt
deliveredAt

추가 가능.

단 실제 운영/조회 요구에 필요한 필드만 추가하고
미래 기능을 이유로 과도한 필드를 만들지 않는다.

Discord Snowflake:

channelId
messageId
roleId
guildId

등은 숫자형 Long이 아니라 String을 우선한다.

==================================================
9. DiscordDeliveryTargetType
==================================================

초기 지원:

JOB
PROGRAM
INQUIRY

Application 등은 이번 Discord Bot 초기 연동 범위에서 추가하지 않는다.

==================================================
10. DiscordDeliveryAction
==================================================

정확히:

CREATE
UPDATE
CLOSE_NOTICE
DELETE_NOTICE

RETRY는 Action이 아니다.

Retry는 기존 Action의 재시도다.

==================================================
11. Discord Message Template
==================================================

Bot v1과 정확히 일치하도록 한다.

지원:

JOB_PUBLISHED
JOB_UPDATED
JOB_CLOSED
JOB_DELETED

PROGRAM_PUBLISHED
PROGRAM_UPDATED
PROGRAM_CLOSED
PROGRAM_DELETED

INQUIRY_CREATED

Server와 Bot의 Enum 이름이 반드시 일치할 필요는 없지만,
직렬화되는 문자열은 Bot Contract와 정확히 일치해야 한다.

가능하면 서버에서도 동일 명칭을 사용한다.

==================================================
12. discord_delivery_attempts
==================================================

실제 Bot API 호출 이력을 별도 저장한다.

권장:

id
discordDeliveryId
attemptType
attemptNumber
requestId
startedAt
finishedAt
result
errorCode
retryable

중요:

Bot Token
Internal API Key
Authorization Header
전체 Request Payload
문의 전체 Content

등은 Attempt Log에 저장하지 않는다.

필요 최소 운영 정보만 보존한다.

==================================================
13. Migration
==================================================

현재 최신 Migration 번호를 먼저 확인한다.

기존 Migration 수정 금지.

신규 Migration으로:

discord_deliveries
discord_delivery_attempts

를 추가한다.

필요 Index:

status + nextRetryAt
targetType + targetId
idempotencyKey unique
discordDeliveryId + createdAt

등 실제 Worker/조회 Query에 필요한 범위.

Database Enum 대신 현재 프로젝트 관례대로 VARCHAR + Enum mapping을 우선한다.

==================================================
14. DiscordBotClient Port
==================================================

Notification 도메인이 Outbound Port를 소유한다.

예:

interface DiscordBotClient {
    fun create(command: DiscordCreateCommand): DiscordBotResult

    fun update(command: DiscordUpdateCommand): DiscordBotResult
}

Bot API 세부 Endpoint 구조 때문에 필요하면:

send()
modify()

등 Naming 변경 가능.

중요:

Port는 Spring HTTP Client 타입에 의존하지 않는다.

금지:

ResponseEntity
RestClient.ResponseSpec
WebClient.ResponseSpec

를 Port 계약에 노출.

==================================================
15. HttpDiscordBotClient
==================================================

Infrastructure Adapter에서 Bot Internal API를 호출한다.

현재 Spring Boot 버전에서 프로젝트가 사용하는 표준 HTTP Client를 확인한다.

새로운 HTTP Library를 불필요하게 추가하지 않는다.

후보:

RestClient

기존 프로젝트에서 이미 WebClient 등을 사용한다면 Convention 우선.

구현:

- Base URL 설정
- Internal API Key Header
- Request ID
- Idempotency Key
- JSON Serialization
- Timeout
- Error Response Parsing
- retryable 해석

==================================================
16. Bot Error Contract 처리
==================================================

Bot v1의 ErrorCode를 실제 구현 기준으로 확인한다.

예상 기본:

INVALID_REQUEST
UNAUTHORIZED
CHANNEL_NOT_FOUND
MESSAGE_NOT_FOUND
MISSING_PERMISSION
RATE_LIMITED
DISCORD_UNAVAILABLE
DISCORD_API_ERROR
INTERNAL_ERROR

Server는 Bot 오류의 raw Stack Trace나 SDK Error를 저장하지 않는다.

Server가 사용하는 핵심 정보:

code
retryable
requestId
필요 최소 message

Bot의 retryable 값을 Retry 판단에 활용하되,
Server가 최종 Retry Policy의 Source of Truth를 유지한다.

==================================================
17. 자동 Retry
==================================================

Notification Worker가 자동 Retry를 담당한다.

기존 요구사항 기준:

자동 최대 3회

권장 Retry:

1분
5분
30분

단 Bot이 429 등으로 Retry-After를 제공하거나
응답 계약상 재시도 시각을 제공한다면 가능한 범위에서 우선 고려한다.

retryable=false:

즉시 FAILED

retryable=true:

잔여 자동 횟수가 있으면:
PENDING + nextRetryAt

횟수 소진:
FAILED

정확한 Retry 횟수 정책은 Notification 최신 요구사항과 대조하여 확정한다.

==================================================
18. 수동 Retry
==================================================

FAILED 상태에서만 허용.

기존 요구사항 기준:

수동 최대 3회

자동 Retry 횟수와 별도 Count.

수동 Retry 시:

manualRetryCount + 1
status = PENDING

새 Delivery를 생성하지 않는다.

동일 Logical Delivery를 재사용한다.

==================================================
19. Retry 시 최신 데이터
==================================================

Retry Payload는 실패 당시 저장한 전체 Discord Payload Snapshot을 그대로 보내지 않는다.

Retry 시:

targetType
targetId

기준으로 원본 도메인의 최신 데이터를 조회하여
Semantic DTO를 다시 만든다.

예:

PROGRAM_UPDATED가 실패
→ 이후 프로그램 이름이 수정됨
→ Retry에서는 최신 이름으로 Discord 수정

Bot Render 결과를 Server DB에 Snapshot으로 장기간 저장할 필요 없다.

==================================================
20. Idempotency Key
==================================================

Server가 안정적인 Logical Key를 생성한다.

중요:

Retry할 때 Key가 바뀌면 안 된다.

예:

discord:{deliveryId}:{action}:{revision}

같은 형태를 고려한다.

하지만 문자열 포맷 자체는 Bot Phase 4 실제 Contract와 맞춘 뒤 확정한다.

핵심 요구:

동일 Logical Action:
동일 Key

새로운 Logical Action:
새 Key

예:

CREATE delivery #100
1차 요청 → key A
Timeout
Retry → key A

UPDATE revision 2
→ key B

Bot은 InMemory Idempotency를 제공하므로
Server의 DB Delivery/Idempotency가 더 강한 Source of Truth다.

==================================================
21. CREATE 성공 처리
==================================================

Bot CREATE 성공 Response에서:

discordMessageId

를 반드시 받는다.

Server:

discordMessageId 저장
status = DELIVERED

이후 UPDATE/CLOSE/DELETE_NOTICE는
이 messageId를 사용한다.

CREATE가 성공했는데 messageId가 없으면
DELIVERED 처리하지 않는다.

Contract 오류로 취급한다.

==================================================
22. UPDATE / CLOSE / DELETE
==================================================

UPDATE:
- 기존 Discord messageId 필요
- Discord Message edit
- Mention 없음

CLOSE_NOTICE:
- 기존 Message edit
- 마감 상태 표현
- Mention 없음

DELETE_NOTICE:
- 기존 Message edit
- 삭제 안내 상태 표현
- Discord Message physical delete 금지
- Mention 없음

messageId가 없다면 요청하지 않고 FAILED 처리하거나
명확한 내부 Error로 처리한다.

자동으로 새 Discord Message를 재생성하지 않는다.

==================================================
23. Missing Discord Message
==================================================

Bot에서:

MESSAGE_NOT_FOUND

를 반환하면:

FAILED

처리.

자동 CREATE fallback 금지.

이유:

Discord에서 Message가 수동 삭제된 경우
새 Message를 몰래 만들어 중복 Announcement를 발생시키면 안 된다.

향후 필요하면 별도:

REPUBLISH

기능으로 설계한다.

==================================================
24. Mention 정책
==================================================

Mention은 Logical Message Lifecycle에서 최초 성공 CREATE 한 번만.

Server가 Bot에 Role IDs를 전달한다.

CREATE 최초 성공 전:
mentionRoleIds 전달 가능

CREATE 성공 이후:

UPDATE
CLOSE_NOTICE
DELETE_NOTICE
Retry of those actions

에는 mentionRoleIds를 비워서 전달한다.

주의:

첫 CREATE가 Bot에 실제 성공하지 않은 경우 Retry CREATE는 Mention 가능.

즉:

Retry == 항상 Mention 금지

가 아니다.

정확한 규칙:

"최초 성공한 CREATE에 최대 1회 Mention"

==================================================
25. Role/Channel Mapping
==================================================

사용자가 직접 Discord Role ID나 Channel ID를 Request Body로 입력하지 않는다.

Server 설정 또는 관리 가능한 Mapping에서 결정한다.

Bot은 Server가 전달한:

channelId
mentionRoleIds

를 신뢰하되 자체 Validation을 수행한다.

현재 정확한 학교 Discord Channel/Role Mapping은
환경별 설정으로 분리한다.

Secret은 아니지만 코드에 운영 Snowflake를 하드코딩하지 않는 방향을 우선한다.

==================================================
26. Job Event 연결
==================================================

현재 Job에는 JobChangedEvent가 존재한다.

실제 Event 구조와 사용처를 확인한 뒤
필요한 경우 Discord 목적에 충분한 Domain Event를 세분화한다.

필요 이벤트 예:

JobPublishedEvent
JobUpdatedEvent
JobClosedEvent
JobDeletedEvent

또는 기존 JobChangedEvent + 변경 후 상태 조회 방식.

프로젝트 패턴상 Event에 Entity 전체를 넣지 않는다.

Notification이 Event 수신 후
Job 공개 Query Port를 통해 최신 Snapshot을 조회하는 구조를 우선한다.

==================================================
27. Program Event 연결
==================================================

현재 Program은 Discord 실제 Event 발행이 없다.

필요:

ProgramPublishedEvent
ProgramUpdatedEvent
ProgramClosedEvent
ProgramDeletedEvent

또는 동일 목적을 만족하는 최소 Event 구조.

AFTER_COMMIT 처리.

Program Transaction 내부에서 Bot HTTP 호출 금지.

==================================================
28. Inquiry Event 연결
==================================================

문의 생성 성공 시:

InquiryCreatedEvent

발행.

Notification에서:

INQUIRY_CREATED Discord Delivery 생성.

Inquiry 저장 성공 이후 Discord 실패가 발생해도:

문의 생성 API는 정상 성공 상태 유지.

Discord Delivery만 FAILED/PENDING.

Inquiry Event에는 Entity 전체를 전달하지 않는다.

개인정보 최소화.

==================================================
29. AFTER_COMMIT
==================================================

외부 Discord Side Effect는 반드시 원본 Transaction Commit 이후 시작한다.

기존 저장소 패턴:

@TransactionalEventListener(AFTER_COMMIT)

을 우선 재사용한다.

잘못된 예:

@Transactional
fun createProgram(...) {
    programRepository.save(program)
    discordBotClient.create(...)
}

금지.

==================================================
30. Worker
==================================================

DiscordDelivery Worker 구현.

기본 Query:

status = PENDING
AND
(nextRetryAt IS NULL OR nextRetryAt <= now)

처리 순서:

1. Delivery Claim
2. PROCESSING
3. 최신 Semantic Data 조회
4. Bot 호출
5. Attempt 저장
6. 성공:
   DELIVERED
7. 실패:
   Retry Policy 계산
   PENDING 또는 FAILED

동일 Delivery가 여러 Worker에서 동시에 처리되지 않게 해야 한다.

현재 인스턴스 규모와 DB 구조를 고려해:

Pessimistic Lock
Atomic UPDATE
Skip Locked

중 기존 프로젝트 패턴에 맞는 단순한 방식 선택.

분산 Lock Library를 새로 도입하지 않는다.

==================================================
31. PROCESSING 복구
==================================================

Server가 Bot 호출 도중 죽으면:

PROCESSING

에 영구 정지할 수 있다.

Stale PROCESSING Recovery가 필요하다.

기존 Collector에도 재기동 Recovery Runner 패턴이 존재하므로
그 구현을 참고할 수 있다.

단 Collector 클래스를 직접 재사용하거나
도메인 간 Repository 의존을 만들지 않는다.

Notification 내부에 동일 아이디어를 구현한다.

==================================================
32. Program 기존 DiscordDeliveryStatus 정리
==================================================

현재 develop:

domain.program.entity.type.DiscordDeliveryStatus

값:

SUCCESS
FAILED
SKIPPED

이 존재한다.

이 Enum은 신규 구조에서 제거/대체 대상.

Program Response의:

discordDelivery.status

가 현재 이 Enum을 외부에 노출한다면
Notification 공통 상태로 변경한다.

Breaking Change 가능성이 있으므로:

- Swagger
- API 문서
- Controller Test
- FE 공유 필요 여부

를 확인한다.

공통 상태:

PENDING
PROCESSING
DELIVERED
FAILED

==================================================
33. Program Response
==================================================

Program DTO가 Discord 상태를 반환해야 한다면:

Program Entity의 상태가 아니라
Notification DiscordDelivery Query Port를 사용한다.

금지:

ProgramRepository
→ Notification Repository 직접 조회

권장:

Notification에서 공개 Query Port 정의
또는 프로젝트 모듈 의존 방향상 적합한 공개 계약.

Spring Modulith 순환 의존이 생기지 않는지 반드시
ModularityTest로 확인한다.

==================================================
34. Collector 기존 Webhook
==================================================

현재 Collector에는 이미:

Discord Webhook 기반 신규 공고 알림

과:

JobNotificationDeliveryStatus
PENDING
SENDING
SENT
FAILED

가 존재한다.

이번 초기 Bot 연동 PR에서 Collector를 강제로 이관하지 않는다.

초기 정책:

Collector Discord Webhook
→ 기존 유지

Job/Program/Inquiry 업무 알림
→ GETI-Bot-V1

즉 당장은 병존 허용.

이유:

Collector는 이미 재시도/복구/운영 검증이 완료된 별도 Subsystem이고,
이번 Notification Bot 연동과 동시에 변경하면 Scope와 Regression 위험이 커진다.

향후:

[REFACTOR] Collector Discord Webhook을 GETI-Bot-V1로 통합

별도 Issue로 검토.

==================================================
35. 기존 Collector Enum은 이번에 삭제하지 않음
==================================================

Collector:

JobNotificationDeliveryStatus
PENDING
SENDING
SENT
FAILED

은 기존 Collector Webhook 내부 구현 타입이므로
Collector를 Bot으로 이관하기 전까지 유지 가능.

단:

Notification 공통 DiscordDeliveryStatus와
이름이 유사하므로 package 경계를 명확히 한다.

새 Notification 구현에서 Collector Enum을 import하면 안 된다.

==================================================
36. Discord 상태 조회 API
==================================================

Job/Program/Inquiry Admin 화면에서 Delivery 상태가 필요하면
Notification 도메인의 공개 Query/API를 제공한다.

예:

targetType + targetId
→ Latest DiscordDelivery status

또는:

deliveryId
→ 상세 상태

정확한 Public REST Endpoint는 현재 Notion/API 명세에 맞춰 확인.

불필요한 Admin Endpoint를 추측하여 추가하지 않는다.

==================================================
37. Manual Retry API
==================================================

기존 Job/Program Retry 명세가 있다면
Notification 공통 Retry Service로 연결한다.

도메인 Controller가 Notification Repository를 직접 접근하지 않는다.

권한은 기존 명세를 유지한다.

특히 Job Retry 권한은 기존 문서가 등록자/담당 교사로 정해져 있다면
DEVELOPER를 임의 추가하지 않는다.

Program 역시 기존 명세 확인.

Inquiry Retry API가 명세에 없다면 임의 추가하지 않는다.

==================================================
38. DB Source of Truth
==================================================

Discord Message 전송 상태는 Notification DB가 Source of Truth.

Bot의 InMemory Idempotency 상태를
운영 상태 조회 Source로 사용하면 안 된다.

Bot 재시작 시 InMemory는 사라진다.

Server DB는 유지된다.

==================================================
39. Payload Snapshot
==================================================

discord_deliveries에 전체 Semantic Payload JSON을 Snapshot으로 저장할지 신중히 검토.

추천:

최소 식별/Delivery 정보만 저장하고
Retry 시 최신 데이터를 조회.

이유:

- 최신 정보 반영
- 개인정보 중복 저장 최소화
- Inquiry content 등 민감 정보 복제 방지

단 Bot 호출 재현/감사에 꼭 필요한 최소 Metadata는 attempts에 기록 가능.

==================================================
40. Inquiry 개인정보
==================================================

Inquiry Discord Payload에는 최소 정보만.

Server에서 Bot으로 다음을 보내지 않는다.

- 이메일
- 전화번호
- 인증정보
- Token
- Stack Trace
- 전체 파일 URL
- 영구 Presigned URL
- 불필요한 Member 정보

Bot에 전달할 데이터는 Renderer가 필요한 최소 Semantic Field만.

Inquiry 전문 전체를 Bot에 전달해야 하는 Contract라면
Bot Renderer에서 Preview만 쓰는 것보다
Server에서 아예 contentPreview를 만들어 보내는 방식도 검토.

단 Rendering 책임과 개인정보 최소화 사이에서
Bot 실제 Contract를 우선 확인.

==================================================
41. Request ID
==================================================

Server의 기존 requestId가 있다면 Bot 요청으로 전달.

없으면 Delivery Attempt마다 UUID 생성.

Server Log:

deliveryId
requestId
targetType
targetId
action

중심.

로그에:

Internal API Key
Discord Token
전체 Inquiry Content

금지.

==================================================
42. Timeout
==================================================

Bot HTTP 호출에는 명시적인 Timeout 적용.

무한 대기 금지.

권장:

connect timeout 짧게
read timeout 수 초 단위

정확한 값은 현재 인프라/Convention에 맞춰 결정.

Timeout은 일반적으로 retryable failure로 분류.

==================================================
43. Circuit Breaker
==================================================

이번 초기 연동에서 Resilience4j를 새로 도입하지 않는다.

현재 Server 문서에서도 Resilience4j는 사용하지 않고
DB Retry/Scheduler 패턴을 사용 중이다.

Bot 장애는:

DB Delivery
+
Retry Worker

로 처리.

향후 장애 빈도가 실제로 높아질 때 별도 검토.

==================================================
44. FakeDiscordBotClient
==================================================

Unit/Integration Test에서 실제 GETI-Bot-V1을 호출하지 않도록
Fake/Test Adapter를 제공.

테스트에서 Discord Bot Token 필요 없어야 한다.

CI는 외부 Network에 의존하지 않는다.

==================================================
45. Contract Test
==================================================

실제 Bot Contract를 고정한 JSON Fixture 또는 Schema 기반 Test를 추가하는 것을 권장.

검증:

- Header 이름
- Endpoint
- Action
- Template
- targetId serialization
- messageId
- error response
- retryable

Bot Repository의 Contract와 Server Test Fixture가 어긋나면
Integration 전에 발견할 수 있어야 한다.

가능하면 두 Repository 사이의 공통 Schema 생성 시스템까지 만들 필요는 없다.

현재 규모에서는 Fixture/문서 기반 Contract Test면 충분.

==================================================
46. Long → JSON
==================================================

GETI Entity ID는 Kotlin Long이다.

Bot은 JavaScript/TypeScript.

JavaScript Number safe integer 범위를 고려해
HTTP Boundary에서는 targetId/deliveryId 등을 String으로 직렬화하는 것을 권장.

예:

"targetId": "123"

Discord Snowflake 역시 반드시 String.

Bot 실제 Zod Schema와 맞춘다.

==================================================
47. Time
==================================================

Bot으로 Timestamp를 전달한다면:

ISO-8601 / RFC3339

UTC Offset 포함.

LocalDateTime을 timezone 정보 없이 JSON String으로 보내는 것은 피한다.

기존 Server global Jackson 정책이 있다면 우선 확인.

==================================================
48. API 성공 의미
==================================================

GETI 핵심 API:

공고 등록
프로그램 등록
문의 등록

성공 의미와 Discord 성공을 분리한다.

예:

Program 저장 성공
Discord Delivery PENDING

이어도:

Program API 성공.

Discord 실패:
Program API rollback 금지.

응답에 Discord 상태를 포함한다면
실제 Delivery 상태를 그대로 반환.

가짜 DELIVERED/SUCCESS 반환 금지.

==================================================
49. Event 중복 방지
==================================================

같은 Domain Event가 중복 수신돼도
동일 Logical DiscordDelivery가 여러 Row 생성되지 않게 한다.

Server DB:

idempotencyKey unique

또는 동등한 Unique constraint 필요.

Application Event 자체는 exactly-once가 아니므로
Notification 수신 측 멱등성이 필요하다.

==================================================
50. JobChangedEvent 검토
==================================================

현재 JobChangedEvent는 jobId만 전달하는 최소 이벤트다.

이게 Discord CREATE/UPDATE/CLOSE/DELETE를 구분하기 충분하지 않다면:

eventType/action

을 추가하거나
별도 Event로 분리.

단 Search indexing 등 기존 Listener가 사용하는 Event 계약에 영향이 있다면
기존 Event를 Breaking 변경하지 않는다.

필요하면 Discord용 신규 Event 추가.

==================================================
51. 도메인 간 의존
==================================================

기존 Notification Core가 선택한 구조를 유지.

Notification이 Job/Program 공개 Query Port를 소비.

다른 Domain이 Notification의 내부 Repository/Entity에 의존하지 않는다.

Circular dependency 금지.

반드시:

ModularityTest

통과.

==================================================
52. Security
==================================================

Bot Internal API Secret은 Server-to-Bot 용도.

사용자 Client에게 절대 노출되지 않는다.

브라우저/App → Bot 직접 접근 구조 금지.

Bot Base URL이 외부에 공개돼도
Internal API Key 없이는 명령 수행 불가.

Server API에서 Internal API Key를 Response로 반환하는 코드 금지.

==================================================
53. 테스트 - Delivery
==================================================

필수 Unit Test:

- CREATE Delivery 생성
- 초기 PENDING
- Worker PROCESSING 전환
- 성공 DELIVERED
- messageId 저장
- retryable 실패 → PENDING
- non-retryable → FAILED
- 자동 retry count
- 자동 최대 횟수
- manual retry count
- manual 최대 횟수
- FAILED 외 manual retry 거부
- stale PROCESSING 복구
- 동일 idempotencyKey 중복 생성 방지

==================================================
54. 테스트 - Bot Client
==================================================

Mock HTTP Server 또는 Spring 표준 테스트 도구 사용.

검증:

- POST CREATE endpoint
- PATCH endpoint
- X-Internal-Api-Key
- X-Request-Id
- X-Idempotency-Key
- JSON Body
- 성공 Response parsing
- 401
- 400
- 404 message
- 429
- 5xx
- timeout
- malformed response

새 WireMock Dependency가 필요 없으면
기존 Spring Mock server를 우선.

==================================================
55. 테스트 - Events
==================================================

Job:
- Publish → Delivery CREATE
- Update → Delivery UPDATE
- Close → CLOSE_NOTICE
- Delete → DELETE_NOTICE

Program:
동일.

Inquiry:
Create → INQUIRY_CREATED

원본 Transaction Rollback 시:
Delivery 생성되지 않음.

원본 Commit 성공 + Bot 실패:
원본 데이터 유지.

==================================================
56. Integration Test
==================================================

PostgreSQL Testcontainers 사용.

검증:

- Migration
- discord_deliveries Entity Mapping
- attempts FK
- idempotency unique
- Worker Query
- concurrent worker claim
- status transition
- retry scheduling

실제 Discord/실제 Bot 호출 금지.

==================================================
57. Architecture Test
==================================================

반드시 검증:

job → notification repository X
program → notification repository X
inquiry → notification repository X

notification → Discord SDK X

notification infrastructure → HTTP client O

Notification application/service:
DiscordBotClient Port만 의존.

discord.js는 Server dependency에 절대 추가하지 않는다.

==================================================
58. Program Breaking Change
==================================================

Program의:

SUCCESS / FAILED / SKIPPED

→

PENDING / PROCESSING / DELIVERED / FAILED

변경은 외부 API 값 집합 변경 가능성이 있다.

따라서 PR 본문에 Breaking Change 여부를 정확히 명시.

현재 실제 사용자가 없고 개발 단계라 해도
Swagger/API 명세/FE 공유 항목으로 남긴다.

==================================================
59. Collector와 혼동 금지
==================================================

현재 Collector Webhook 구현은 이미 실제 재시도와 Recovery를 갖고 있다.

그 코드를 통째로 Notification으로 복붙하지 않는다.

참고 가능한 패턴:

- DB 기반 retry
- nextRetryAt
- stale 상태 recovery
- HTTP failure 분류

하지만 새 Notification 구조와 Bot Error Contract에 맞춰 재구현한다.

==================================================
60. 구현 PR 분리 권장
==================================================

한 PR에 전부 넣지 않는다.

추천:

PR A — Discord Delivery Persistence + Worker 기반

- discord_deliveries
- discord_delivery_attempts
- Enum
- Repository
- Worker
- Retry
- FakeDiscordBotClient
- Migration

PR B — Domain Event 연결

- Job
- Program
- Inquiry
- AFTER_COMMIT
- Delivery 생성
- Program 기존 상태 교체

PR C — 실제 GETI-Bot-V1 HTTP 연동

- HttpDiscordBotClient
- 환경변수
- Header
- Contract mapping
- Error mapping
- Timeout
- Contract tests

PR D — 통합 검증/운영 보강

- 실제 dev Bot integration
- 필요한 API 응답 정리
- retry/manual retry 검증
- 문서
- 운영 설정

각 PR마다:
Issue
→ branch
→ 구현
→ test
→ self review
→ PR
→ CI
→ review/merge

기존 GETI Server Harness 규칙을 반드시 준수.

==================================================
61. 작업 전 반드시 확인
==================================================

작업 시작 전에 최신 develop 기준으로 다시 조사:

1. Notification Core 최신 상태
2. 최신 Migration 번호
3. Notification Entity/Repository 변경 여부
4. Program DiscordDeliveryStatus 존재 여부
5. Collector Webhook 구조
6. JobChangedEvent Listener 목록
7. Program Event 존재 여부
8. Inquiry 구현/이벤트 상태
9. 최신 GETI-Bot-V1 Internal API
10. Bot Request/Response DTO
11. Bot ErrorCode
12. Bot Idempotency 동작
13. Bot Template 목록
14. Bot Mention 정책
15. Bot Header validation
16. Server configuration convention
17. 기존 HTTP Client 사용 여부
18. Scheduler convention
19. TransactionalEventListener pattern
20. ModularityTest dependency graph

현재 문서보다 실제 코드가 최신 Source of Truth.

==================================================
62. 완료 조건
==================================================

다음이 만족되어야 Server ↔ Bot Integration 완료:

- Notification이 Discord Delivery Source of Truth
- PENDING/PROCESSING/DELIVERED/FAILED 통일
- discord_deliveries
- discord_delivery_attempts
- DiscordBotClient Port
- HttpDiscordBotClient
- Bot Internal API 인증
- Request ID
- Idempotency Key
- Automatic Retry
- Manual Retry
- Stale PROCESSING recovery
- Job Event 연결
- Program Event 연결
- Inquiry Event 연결
- Program 자체 Discord 상태 제거/교체
- Discord 실패 Core Transaction 격리
- CREATE messageId 저장
- UPDATE/CLOSE/DELETE 기존 Message 수정
- Missing Message 자동 재생성 금지
- Mention 최초 성공 CREATE 1회
- Fake Bot Client Test
- HTTP Contract Test
- Integration Test
- ModularityTest
- Architecture Test
- Swagger/API 문서 갱신
- Secret 미노출
- CI 전체 통과

==================================================
63. 이번 작업에서 제외
==================================================

- Collector Webhook Bot 이관
- Redis
- Kafka
- RabbitMQ
- Resilience4j 신규 도입
- Slash Command
- Discord Button
- Discord Modal
- Bot DB 접근
- Server에서 Discord Embed 생성
- Discord Message physical delete
- Production Deployment 전체 재설계
- Mobile Push