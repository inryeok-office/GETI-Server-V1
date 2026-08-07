[GETI Notification 도메인 개발 요구사항]

현재 Program 도메인 Phase 1~3 개발이 완료된 상태입니다.
Notification 도메인은 사용자 인앱 알림을 관리하고, 향후 별도 Discord Bot 서비스와 연동할 수 있는 전달 기반을 제공해야 합니다.

중요:
- Discord Bot은 GETI Server와 별도 서비스로 개발할 예정입니다.
- 이번 Notification 작업에서 실제 discord.js Bot을 완성하거나 실제 Discord 메시지 전송까지 구현할 필요는 없습니다.
- 단, 나중에 Bot을 연결할 때 Notification 도메인을 다시 크게 수정하지 않도록 Port, 전달 상태, 작업 큐, 재시도 구조까지 준비해야 합니다.
- 기존 프로젝트 구조, Clean Architecture/Modular Monolith 원칙, 패키지 규칙, 코드 스타일, 예외 처리, Swagger 작성 방식과 테스트 패턴을 그대로 따라야 합니다.

==================================================
1. Notification 도메인의 책임
==================================================

Notification 도메인은 다음 기능을 담당합니다.

1. 사용자별 인앱 알림 생성
2. 사용자 알림 목록 조회
3. 알림 상세 또는 대상 화면 이동에 필요한 정보 제공
4. 단일 알림 읽음 처리
5. 전체 알림 읽음 처리
6. 읽지 않은 알림 개수 조회
7. 알림 대상 리소스 접근 가능 여부 계산
8. Job, Program, Application, Inquiry 등 다른 도메인의 이벤트 수신
9. Discord 전달 요청과 상태 관리
10. 별도 Discord Bot 서비스 호출을 위한 Port 제공
11. Discord 전달 자동·수동 재시도 기반 제공
12. Discord 전달 이력 관리

Notification 도메인은 다음 역할을 하지 않습니다.

- Job, Program, Application의 비즈니스 상태를 직접 변경
- 다른 도메인의 Entity 또는 Repository 직접 참조
- Discord Embed 디자인을 직접 생성
- Discord Bot Token 관리
- Discord Gateway 연결
- Discord Slash Command, 버튼, 모달 처리
- 사용자 문의 답변 처리
- 메시지 전송 실패를 이유로 원본 도메인 Transaction 롤백

==================================================
2. 아키텍처 원칙
==================================================

도메인 연결 구조:

Job / Program / Application / Inquiry
→ 도메인 이벤트 발행
→ Notification 도메인이 이벤트 수신
→ 인앱 알림 또는 Discord Delivery 생성

Discord 연동 구조:

Notification 도메인
→ DiscordBotClient Port
→ FakeDiscordBotClient 또는 HttpDiscordBotClient
→ 별도 Discord Bot 서비스
→ Discord API

필수 원칙:

- 다른 도메인은 Notification Entity나 Repository를 직접 참조하지 않는다.
- Notification은 다른 도메인의 Entity나 Repository를 직접 참조하지 않는다.
- 도메인 간 연결은 Event 또는 공개 Query Port로만 수행한다.
- 원본 리소스 저장이 먼저 Commit돼야 한다.
- 알림 생성과 Discord 전달 실패는 원본 도메인 Transaction을 실패시키지 않는다.
- Discord 작업은 Commit 이후 비동기로 처리한다.
- 단순 @Async만으로 작업 신뢰성을 보장하지 않는다.
- 초기에는 PostgreSQL의 discord_deliveries 테이블을 영속 작업 큐로 사용한다.
- 현재 규모에서는 Kafka를 도입하지 않는다.

==================================================
3. 인앱 Notification 데이터 모델
==================================================

notification 또는 notifications 테이블을 사용합니다.

권장 필드:

- id
- receiverMemberId
- type
- title
- content
- targetType
- targetId
- targetUrl 또는 deepLink
- isRead
- readAt
- createdAt
- updatedAt

회원 Entity를 직접 연관관계로 참조하기보다 프로젝트의 현재 ID 참조 정책을 확인하고 일관되게 구현합니다.

필요 Enum:

NotificationType 예시:
- JOB_PUBLISHED
- JOB_UPDATED
- JOB_CLOSED
- JOB_DELETED
- JOB_APPLICATION_STATUS_CHANGED
- PROGRAM_PUBLISHED
- PROGRAM_UPDATED
- PROGRAM_CLOSED
- PROGRAM_DELETED
- PROGRAM_APPLICATION_APPLIED
- PROGRAM_APPLICATION_CANCELED
- PROGRAM_VACANCY_AVAILABLE
- INQUIRY_ANSWERED
- MEMBER_APPROVAL_RESULT
- SYSTEM

NotificationTargetType:
- JOB
- JOB_APPLICATION
- PROGRAM
- PORTFOLIO_REQUEST
- INQUIRY
- MEMBER_APPROVAL

현재 API 명세의 확정 Enum을 먼저 확인하며, 임의로 불필요한 타입을 추가하지 않습니다.

NotificationTargetUnavailableReason:
- DELETED
- NOT_VISIBLE
- FORBIDDEN

알림이 가리키는 원본 리소스가 삭제되거나 권한이 없어도 알림 Row 자체를 자동 삭제하지 않습니다.

응답에서 다음을 계산합니다.

- targetAvailable
- targetUnavailableReason
- availableAction 또는 deepLink

클라이언트가 원본 리소스의 공개·삭제·권한 상태를 자체 판단하지 않도록 합니다.

==================================================
4. 인앱 Notification API
==================================================

기본 Base URL:

/api/v1/notifications

공통 인증:

Authorization: Bearer {accessToken}

공통 응답 형식:

{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "UUID"
  }
}

필요 API:

1. 내 알림 목록 조회

GET /api/v1/notifications

Query:
- isRead: Boolean, 선택
- type: NotificationType, 선택
- page: Int, 기본 0
- size: Int, 기본 20, 최대 100

응답 필드 예시:

{
  "content": [
    {
      "notificationId": "Long",
      "type": "NotificationType",
      "title": "String",
      "content": "String",
      "targetType": "NotificationTargetType",
      "targetId": "Long | null",
      "targetAvailable": "Boolean",
      "targetUnavailableReason": "NotificationTargetUnavailableReason | null",
      "deepLink": "String | null",
      "isRead": "Boolean",
      "readAt": "LocalDateTime | null",
      "createdAt": "LocalDateTime"
    }
  ],
  "page": "Int",
  "size": "Int",
  "totalElements": "Long",
  "totalPages": "Int"
}

정렬:
- createdAt DESC
- 같은 시각이면 id DESC

2. 읽지 않은 알림 개수 조회

GET /api/v1/notifications/unread-count

응답:

{
  "unreadCount": "Long"
}

3. 단일 알림 읽음 처리

PATCH /api/v1/notifications/{notificationId}/read

정책:
- 본인의 알림만 처리 가능
- 이미 읽은 알림은 멱등 처리
- 다른 사용자의 알림이면 정보 노출을 고려해 프로젝트 공통 정책에 맞는 403 또는 404 반환

4. 전체 알림 읽음 처리

PATCH /api/v1/notifications/read-all

정책:
- 현재 사용자의 읽지 않은 알림만 일괄 변경
- 처리 개수를 응답할 수 있음
- 이미 모두 읽은 상태에서도 정상 처리

응답 예시:

{
  "updatedCount": "Long",
  "readAt": "LocalDateTime"
}

알림 삭제 API는 현재 요구사항에 없다면 추가하지 않습니다.

==================================================
5. 알림 생성 방식
==================================================

외부 도메인이 NotificationService를 직접 호출하는 방식보다 도메인 이벤트 수신 방식을 우선합니다.

예상 이벤트:

Job:
- JobPublishedEvent
- JobUpdatedEvent
- JobClosedEvent
- JobDeletedEvent

Program:
- ProgramPublishedEvent
- ProgramUpdatedEvent
- ProgramClosedEvent
- ProgramDeletedEvent
- ProgramApplicationAppliedEvent
- ProgramApplicationCanceledEvent
- ProgramVacancyAvailableEvent

Application:
- JobApplicationSubmittedEvent
- JobApplicationEditAllowedEvent
- JobApplicationRevisionRequestedEvent
- JobApplicationApprovedEvent
- JobApplicationRejectedEvent

Inquiry:
- InquiryAnsweredEvent

Member:
- MemberApprovalResultEvent

모든 이벤트를 이번 PR에서 실제 연결할 필요는 없습니다.
현재 구현돼 있는 도메인 이벤트와 필요한 공개 계약을 먼저 확인하고, Notification 도메인이 소비할 수 있는 구조를 만듭니다.

이벤트 DTO에는 Notification에 필요한 최소 Snapshot만 포함합니다.

예시:

data class ProgramPublishedEvent(
    val programId: Long,
    val title: String,
    val targetGrades: Set<Int>,
    val occurredAt: LocalDateTime,
)

금지:

- 이벤트에 Program Entity 전달
- 이벤트에 Member Entity 전달
- 이벤트 Listener에서 다른 도메인 Repository 직접 조회
- 이벤트 내부에 Discord SDK 객체 포함

원본 상세 정보가 더 필요하면 대상 도메인이 제공하는 공개 Query Port를 사용합니다.

==================================================
6. 중복 알림 방지
==================================================

동일 이벤트가 재처리돼도 동일 알림이 중복 생성되지 않도록 설계합니다.

권장 필드 또는 구조:

- eventId
- idempotencyKey
- sourceEventType
- sourceEventId

예시:

PROGRAM:123:PUBLISHED:v1
PROGRAM_APPLICATION:456:APPROVED
INQUIRY:32:ANSWERED:v1

DB Unique Constraint 또는 명확한 멱등성 검증을 사용합니다.

단, 사용자가 실제로 여러 번 받아야 하는 반복 알림까지 하나로 합치면 안 됩니다.

==================================================
7. Discord Delivery 소유권
==================================================

Discord 전달 관련 타입과 상태는 Notification 도메인이 소유합니다.

Program, Job, Inquiry 도메인이 각각 Discord 상태 Enum을 새로 만들지 않도록 합니다.

DiscordDeliveryStatus:

- PENDING
- PROCESSING
- DELIVERED
- FAILED

과거 값인 다음 이름을 새 코드에서 사용하지 않습니다.

- SENDING
- SENT

DiscordDeliveryTargetType:

- JOB
- PROGRAM
- INQUIRY

DiscordDeliveryAction:

- CREATE
- UPDATE
- CLOSE_NOTICE
- DELETE_NOTICE

DiscordDeliveryAttemptType:

- AUTOMATIC
- MANUAL

Discord 기능이 아직 실제 연동되지 않았다는 이유로 성공 상태를 임의 반환하면 안 됩니다.

Fake Client 환경에서도 테스트가 명확히 Fake임을 알 수 있어야 하며, production에서 Bot 호출이 비활성화됐다면 PENDING 또는 기능 비활성 정책을 일관되게 적용합니다.

==================================================
8. Discord Delivery 테이블
==================================================

discord_deliveries:

- id
- targetType
- targetId
- guildId
- channelId
- messageId, nullable
- status
- automaticRetryCount
- manualRetryCount
- maxAutomaticRetryCount
- maxManualRetryCount
- lastFailureCode, nullable
- lastFailureReason, nullable
- nextRetryAt, nullable
- requestedAt
- processingStartedAt, nullable
- deliveredAt, nullable
- lastSyncedAt, nullable
- createdAt
- updatedAt

권장 Unique 정책:

논리적으로 하나의 원본 리소스가 하나의 Discord 메시지를 갖는다면:

unique(targetType, targetId)

단, 실제 운영상 하나의 리소스를 여러 채널에 보낼 수 있다면:

unique(targetType, targetId, guildId, channelId)

현재 공고·프로그램은 하나의 선택된 채널에 게시하는 계약이므로 실제 DTO와 요구사항을 확인해 결정합니다.

discord_delivery_attempts:

- id
- deliveryId
- action
- attemptType
- status
- requestId
- idempotencyKey
- httpStatus, nullable
- failureCode, nullable
- failureReason, nullable
- requestedAt
- completedAt, nullable
- createdAt

Bot Token, 내부 API Key, Authorization Header, 문의 전문은 DB나 로그에 저장하지 않습니다.

==================================================
9. Discord 전달 생성 정책
==================================================

Program:

DRAFT 저장:
- Discord Delivery 생성하지 않음

PUBLISHED:
- Program Transaction Commit 이후 CREATE Delivery 생성
- 상태 PENDING
- 최초 게시에서만 targetGrades에 대응하는 Role ID 전달

수정:
- 기존 messageId를 사용한 UPDATE
- 멘션 없음

CLOSED:
- CLOSE_NOTICE
- 기존 메시지에 [모집 마감] 표시
- 멘션 없음

DELETED:
- DELETE_NOTICE
- 기존 메시지에 [삭제된 모집] 표시
- Discord 메시지 물리 삭제 금지
- 멘션 없음

Job도 같은 원칙을 사용합니다.

Inquiry:

- Inquiry 생성 Commit 이후 CREATE Delivery 생성
- 별도 개발·운영 Discord Guild의 관리자 전용 채널 자동 사용
- 사용자가 guildId 또는 channelId를 선택하지 않음
- 문의 접수 알림만 전달
- Discord에서 답변 또는 상태 변경하지 않음

Discord 실패 시:

- Program·Job·Inquiry 원본 상태 유지
- 원본 API 응답 성공 유지
- Delivery만 FAILED 처리

==================================================
10. 허용 채널 정책
==================================================

공고와 프로그램은 학교 Discord 서버의 허용된 채널 4개 중 하나만 사용할 수 있습니다.

클라이언트가 임의 Channel ID를 입력하게 만들면 안 됩니다.

현재 등록 API에 discordChannelId가 존재하더라도 서버에서 반드시 허용 목록을 검증해야 합니다.

허용 채널 설정:

- guildId
- channelId
- 표시 이름
- 목적 또는 지원 대상

초기에는 환경변수 또는 application 설정으로 관리할 수 있습니다.

향후 필요 시 DB 관리로 이전 가능하게 구현합니다.

문의는 고정된 별도 Guild·Channel을 사용합니다.

현재 정확한 4개 채널의 이름과 용도는 별도 확정이 필요하므로 코드 상수로 임의 지정하지 않습니다.

==================================================
11. DiscordBotClient Port
==================================================

Notification 도메인은 실제 Bot 구현과 분리된 Port를 제공합니다.

예시:

interface DiscordBotClient {
    fun createMessage(
        command: CreateDiscordMessageCommand,
    ): DiscordBotMessageResult

    fun updateMessage(
        command: UpdateDiscordMessageCommand,
    ): DiscordBotMessageResult
}

CreateDiscordMessageCommand 권장 필드:

- requestId
- idempotencyKey
- targetType
- targetId
- action
- guildId
- channelId
- mentionRoleIds
- template
- data

UpdateDiscordMessageCommand:

- requestId
- idempotencyKey
- targetType
- targetId
- action
- guildId
- channelId
- messageId
- mentionRoleIds는 빈 배열
- template
- data

결과:

- guildId
- channelId
- messageId
- deliveredAt

Bot은 Embed를 생성합니다.

GETI Server가 결정할 것:

- 대상 Guild
- 대상 Channel
- Discord Action
- 멘션 Role
- 전달할 원본 데이터
- 재시도 가능 여부
- 비즈니스 상태

Bot이 결정할 것:

- Embed 제목
- 색상
- 필드 배치
- Markdown Escape
- Discord 글자 수 제한
- Footer
- Timestamp
- 메시지 생성·수정
- Discord Rate Limit 대응
- Discord 오류 변환

Bot이 Program 상태나 대상 학년을 보고 비즈니스 판단을 수행하면 안 됩니다.

==================================================
12. FakeDiscordBotClient
==================================================

실제 Discord Bot이 아직 없어도 Notification 도메인을 개발·테스트할 수 있어야 합니다.

local/test 환경:

- 실제 Discord 요청 금지
- FakeDiscordBotClient 또는 Stub 사용

Fake 예시 동작:

- createMessage 호출 시 fake messageId 반환
- updateMessage 호출 시 전달받은 messageId 반환
- 테스트에서 성공·실패·Timeout을 주입할 수 있어야 함

반드시 검증할 시나리오:

- CREATE 성공
- UPDATE 성공
- Bot 4xx
- Bot 5xx
- Timeout
- Connection 실패
- MESSAGE_NOT_FOUND
- RATE_LIMITED
- 동일 Idempotency Key 중복 요청

Fake 구현을 Production Bean으로 잘못 사용하는 일이 없도록 Profile 또는 명시적인 Configuration을 사용합니다.

==================================================
13. Worker와 상태 전이
==================================================

Worker는 PENDING이고 nextRetryAt이 현재 이전인 작업을 가져옵니다.

권장 흐름:

1. PENDING 조회
2. 처리 대상 Row Lock 또는 안전한 Claim
3. PROCESSING 변경
4. DiscordBotClient 호출
5. 성공 시 DELIVERED
6. 실패 시 재시도 가능 여부 계산
7. 재시도 가능하면 PENDING + nextRetryAt
8. 한도 초과면 FAILED
9. Attempt 이력 저장

동시에 여러 서버 인스턴스가 실행돼도 같은 Delivery를 중복 처리하면 안 됩니다.

검토 가능한 방법:

- SELECT ... FOR UPDATE SKIP LOCKED
- 상태 기반 원자적 Claim
- 프로젝트의 Scheduler·ShedLock 정책과 조합

하나의 거대한 Transaction 안에서 외부 HTTP 호출을 수행하지 않는 방향을 권장합니다.

최소한 DB Row Lock을 잡은 채 Bot 응답을 오래 기다리지 않도록 설계합니다.

PROCESSING 상태에서 서버가 종료되는 경우를 고려해야 합니다.

예:

- processingStartedAt이 일정 시간 이상 지난 작업을 PENDING으로 복구
- 또는 FAILED 처리 후 재시도

정확한 Timeout은 설정값으로 둡니다.

==================================================
14. 재시도 정책
==================================================

자동 재시도:

- 최대 3회
- 권장 간격: 1분, 5분, 30분
- Discord 또는 Bot의 Retry-After가 있다면 우선 사용

수동 재시도:

- 자동 재시도가 끝난 FAILED 상태에서만 가능
- 최대 3회
- PROCESSING은 불가
- DELIVERED는 불가

재시도 시:

- 실패 당시 Payload를 그대로 재사용하지 않음
- 최신 GETI 원본 데이터를 공개 Query Port로 다시 조회
- 최신 내용으로 Payload 재생성
- 수정·마감·삭제·재시도에서는 Role 재멘션 금지

기존 messageId가 Discord에서 삭제된 경우:

- 자동으로 새 메시지를 게시하지 않음
- MESSAGE_NOT_FOUND로 FAILED 처리
- 향후 별도의 “새 메시지로 다시 게시” 기능을 검토
- 일반 재시도와 재게시는 구분

==================================================
15. Discord 상태 조회 및 재시도 API
==================================================

공고·프로그램 화면에서 Delivery 상태를 확인할 수 있어야 합니다.

도메인별 공개 API 예시:

GET /api/v1/admin/programs/{programId}/discord
GET /api/v1/admin/jobs/{jobId}/discord
GET /api/v1/admin/inquiries/{inquiryId}/discord

또는 기존 상세 응답에 요약 상태를 포함할 수 있습니다.

응답 예시:

{
  "targetType": "PROGRAM",
  "targetId": 123,
  "channelId": "String",
  "messageId": "String | null",
  "status": "PENDING | PROCESSING | DELIVERED | FAILED",
  "automaticRetryCount": 1,
  "manualRetryCount": 0,
  "maxAutomaticRetryCount": 3,
  "maxManualRetryCount": 3,
  "canRetry": false,
  "failureCode": "String | null",
  "failureReason": "String | null",
  "requestedAt": "LocalDateTime",
  "lastSyncedAt": "LocalDateTime | null"
}

기존 API 명세에 다음 재시도 API가 이미 존재한다면 계약을 유지하고 내부 구현만 Notification으로 연결합니다.

POST /api/v1/admin/jobs/{jobId}/discord/retry
POST /api/v1/admin/programs/{programId}/discord/retry

권한:

Job·Program:
- 등록자 또는 담당 교사
- DEVELOPER

Inquiry:
- DEVELOPER

재시도 API는 원본 Job·Program·Inquiry 상태를 변경하면 안 됩니다.

==================================================
16. 문의 Discord 개인정보 정책
==================================================

Discord 문의 Embed에 포함 가능:

- 문의 유형
- 문의 제목
- 작성자 이름
- 기수
- 학과
- 내용 앞부분 100~200자
- 첨부파일 개수
- 등록 시각
- Admin Web 상세 링크

포함 금지:

- 전화번호
- 이메일
- 문의 전문
- 첨부파일 직접 다운로드 URL
- Access Token
- Authorization Header
- 비공개 프로필 정보
- Secret
- 내부 오류 Stack Trace

내용 일부를 자를 때 개인정보가 포함될 수 있으므로 최소한의 길이 제한과 개행·Markdown 정리가 필요합니다.

Discord는 알림만 제공합니다.

다음 처리는 Admin Web에서만 수행합니다.

- 문의 전문 확인
- 첨부파일 다운로드
- 담당자 지정
- 답변 작성
- 상태 변경

==================================================
17. Notification 대상 접근 가능성
==================================================

알림 목록에서 targetAvailable을 계산해야 합니다.

예:

Program이 DELETED:
- 알림은 남김
- targetAvailable=false
- targetUnavailableReason=DELETED
- deepLink는 null 또는 대체 안내 경로

MOU Job에 접근 권한 없음:
- targetAvailable=false
- targetUnavailableReason=FORBIDDEN

비공개 또는 조회 불가:
- targetAvailable=false
- targetUnavailableReason=NOT_VISIBLE

Notification 도메인이 다른 도메인의 Repository를 직접 사용하지 않습니다.

필요하면 공개 Query Port를 정의합니다.

예:

interface NotificationTargetAvailabilityQueryPort {
    fun resolve(
        targetType: NotificationTargetType,
        targetId: Long,
        viewerMemberId: Long,
    ): NotificationTargetAvailability
}

하지만 하나의 거대한 공통 Port가 다른 모든 도메인을 알고 순환 의존을 만들지 않도록, 실제 프로젝트 구조에 맞춰 도메인별 공개 Port 또는 Resolver Registry를 검토합니다.

==================================================
18. Push 알림 범위
==================================================

현재 API 명세에는 PUSH_PLATFORM 값이 존재합니다.

PUSH_PLATFORM:
- IOS
- ANDROID

다만 이번 Notification 작업에서 FCM/APNs 실제 Push 연동이 요구사항이나 Issue 범위에 없다면 구현하지 않습니다.

권장 분리:

Phase 1:
- 인앱 알림
- 읽음 처리
- 미확인 개수
- 대상 접근 가능성
- 이벤트 수신 기반

Phase 2:
- Discord Delivery 기반
- Fake Bot Client
- Worker
- 재시도
- 상태 조회

Phase 3:
- 실제 Discord Bot 내부 API 연동

Phase 4:
- 모바일 Push Device·설정·FCM/APNs

Issue 범위를 벗어난 Push 구현을 임의로 추가하지 않습니다.

==================================================
19. 환경변수
==================================================

GETI Server:

DISCORD_DELIVERY_ENABLED=false
DISCORD_BOT_BASE_URL=
DISCORD_INTERNAL_API_KEY=
DISCORD_REQUEST_TIMEOUT_SECONDS=10
DISCORD_AUTOMATIC_RETRY_LIMIT=3
DISCORD_MANUAL_RETRY_LIMIT=3

DISCORD_SCHOOL_GUILD_ID=
DISCORD_ALLOWED_CHANNEL_1_ID=
DISCORD_ALLOWED_CHANNEL_1_NAME=
DISCORD_ALLOWED_CHANNEL_2_ID=
DISCORD_ALLOWED_CHANNEL_2_NAME=
DISCORD_ALLOWED_CHANNEL_3_ID=
DISCORD_ALLOWED_CHANNEL_3_NAME=
DISCORD_ALLOWED_CHANNEL_4_ID=
DISCORD_ALLOWED_CHANNEL_4_NAME=

DISCORD_GRADE_1_ROLE_ID=
DISCORD_GRADE_2_ROLE_ID=
DISCORD_GRADE_3_ROLE_ID=

DISCORD_INQUIRY_GUILD_ID=
DISCORD_INQUIRY_CHANNEL_ID=

주의:

- 실제 Secret 값을 코드, application.yml, 테스트 파일, Notion 또는 PR 본문에 기록하지 않습니다.
- Bot Token은 Discord Bot 서비스만 보유합니다.
- GETI Server는 Bot Token을 알지 않습니다.
- GETI Server에는 내부 API Key만 설정합니다.
- local/test에서는 실제 Discord 전송을 기본 비활성화합니다.
- dev는 테스트 Guild·Channel만 사용합니다.

==================================================
20. 에러 코드
==================================================

기존 프로젝트 예외 패턴과 HTTP Status 기준을 따릅니다.

필요 개념 예시:

Notification:
- NOTIFICATION_NOT_FOUND
- NOTIFICATION_ACCESS_DENIED

Discord:
- DISCORD_DELIVERY_NOT_FOUND
- DISCORD_RETRY_NOT_AVAILABLE
- DISCORD_DELIVERY_IN_PROGRESS
- DISCORD_RETRY_LIMIT_EXCEEDED
- DISCORD_CHANNEL_NOT_ALLOWED
- DISCORD_CHANNEL_NOT_CONFIGURED
- DISCORD_MESSAGE_NOT_FOUND
- DISCORD_BOT_UNAVAILABLE
- DISCORD_RATE_LIMITED
- DISCORD_DELIVERY_FAILED

외부 Bot·Discord 오류를 사용자에게 그대로 노출하지 않습니다.

failureReason에는 운영자가 이해할 수 있는 정제된 메시지만 저장합니다.

Bot 응답 Body 전체, Stack Trace, 내부 URL, Token은 저장하지 않습니다.

==================================================
21. 테스트 요구사항
==================================================

필수 Unit Test:

Notification:
- 알림 생성
- 내 알림 목록
- 읽지 않은 개수
- 단일 읽음
- 전체 읽음
- 다른 사용자 알림 접근 거부
- 이미 읽은 알림 멱등 처리
- targetAvailable 계산

Discord Delivery:
- CREATE 작업 생성
- UPDATE 작업 생성
- DRAFT에서는 생성되지 않음
- 최초 게시에만 mentionRoleIds 포함
- 수정·마감·삭제에는 mention 없음
- 성공 시 DELIVERED
- 실패 후 자동 재시도
- 자동 재시도 한도
- 수동 재시도 한도
- PROCESSING 재시도 거부
- DELIVERED 재시도 거부
- MESSAGE_NOT_FOUND
- 최신 원본 데이터 재조회
- 동일 idempotencyKey 중복 처리 방지
- inquiry 고정 채널
- 허용되지 않은 채널 거부
- 문의 개인정보 필드 미포함

필수 Integration Test 권장:

- Flyway Migration 전체 실행
- Notification Repository
- Discord Delivery Worker의 동시 Claim
- 두 Worker가 동일 Delivery를 중복 처리하지 않음
- PROCESSING 작업 복구
- PostgreSQL Unique Constraint
- Event 수신 후 Notification 생성
- Event 중복 수신 시 중복 알림 방지
- FakeDiscordBotClient 연결

Controller Test:

- 인증 없는 요청 401
- 다른 사용자 알림 접근
- Pagination
- size 최대값
- 공통 ApiResponse
- 잘못된 Enum 400
- 재시도 권한
- ErrorCode
- Swagger 계약

전체 검증:

./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew integrationTest
./gradlew clean test build

기존 프로젝트에 해당 Task가 실제 존재하는지 확인하고 실행합니다.

==================================================
22. 구현 순서
==================================================

한 PR에 너무 많은 범위를 넣지 않는 것을 권장합니다.

권장 PR 분리:

PR 1 — Notification Core
- Notification Entity
- Repository
- Service
- 목록 조회
- 읽음 처리
- unread count
- targetAvailable 기본 계약
- Unit·Controller Test
- Swagger

PR 2 — Discord Delivery 기반
- discord_deliveries
- discord_delivery_attempts
- Discord Enum
- DiscordBotClient Port
- FakeDiscordBotClient
- Worker
- 자동·수동 재시도
- 상태 조회
- Integration Test

PR 3 — 도메인 이벤트 연결
- Program Event
- Job Event
- Inquiry Event
- Application Event
- Notification 생성
- Discord Payload 생성
- 중복 방지
- 권한·대상 계산

PR 4 — 실제 Discord Bot 연결
- HttpDiscordBotClient
- 내부 REST API
- API Key
- Idempotency
- Timeout
- Test Guild 통합 검증

현재 담당 작업이 Notification Core만이라면 Discord 실연동까지 억지로 구현하지 않습니다.
다만 Discord가 이후 자연스럽게 연결되도록 타입과 Port의 소유권을 올바르게 설계합니다.

==================================================
23. 이번 작업에서 하지 말아야 할 것
==================================================

- discord.js를 Spring 프로젝트에 추가
- GETI Server에서 Bot Token 사용
- Job·Program·Inquiry에서 Discord REST API 직접 호출
- Notification에서 다른 도메인 Repository 직접 참조
- 실제 전송 없이 DELIVERED 반환
- Discord 실패로 Program·Job·Inquiry Transaction 롤백
- @Async만 사용하고 작업 유실 가능성을 방치
- Worker가 같은 Delivery를 중복 처리
- 무제한 재시도
- 문의 전문·전화번호·이메일을 Discord에 전송
- Discord에서 문의 답변 처리
- 마감·삭제 시 Discord 메시지 물리 삭제
- 수정·재시도 때 학년 Role 재멘션
- MESSAGE_NOT_FOUND에서 자동 재게시
- Kafka·RabbitMQ 등 현재 필요 없는 인프라 추가
- FCM/APNs를 Issue 범위 확인 없이 추가
- 새로운 공통 Global Service에 모든 비즈니스 로직 집중
- 기존 Migration 수정
- Secret Commit

==================================================
24. 담당자가 작업 전 확인해야 할 사항
==================================================

작업 시작 전에 다음을 확인하고, 불명확하면 임의 결정하지 말고 보고합니다.

1. 현재 Notification 관련 Entity·Migration·API 구현 상태
2. Program·Job·Inquiry가 현재 발행하는 Event 존재 여부
3. Program PR에서 자체 DiscordDeliveryStatus가 남아 있는지
4. 기존 Discord 상태가 SENDING/SENT인지
5. Program DTO가 Notification 소유 타입으로 교체 가능한지
6. SecurityConfig의 Notification 접근 규칙
7. 현재 API 명세의 Notification Endpoint
8. Push 관련 기존 코드 범위
9. Member 공개 Query Port
10. 삭제·비공개 대상의 접근 가능성 확인 방식
11. Scheduler와 ShedLock 기존 사용 방식
12. 프로젝트의 Retry·Resilience4j 사용 패턴
13. 기존 도메인의 Outbox 또는 Event 발행 패턴
14. 실제 DB Migration 최신 버전 번호
15. 테스트 환경에서 PostgreSQL·Redis 의존 여부

확인 결과에 따라 작업 계획을 먼저 짧게 공유한 뒤 구현합니다.

==================================================
25. 현재 추가 확정이 필요한 정책
==================================================

아래 사항은 아직 최종값이 없으므로 임의 구현하지 않습니다.

1. 학교 Discord 허용 채널 4개의 이름과 정확한 용도
2. 공고와 프로그램이 동일한 4개 채널을 공유하는지
3. 유형별 기본 채널을 서버가 자동 선택할지
4. 등록 화면에서 기본 채널을 변경할 수 있는지
5. Discord 메시지가 삭제된 경우 “새 메시지로 다시 게시” 기능 제공 여부
6. Discord Delivery Attempt 보관 기간
7. 실제 Notification Type 전체 목록
8. 어떤 Program 이벤트를 누구에게 인앱 알림으로 보낼지
9. 공고·프로그램 수정 시 어떤 필드 변경에서 알림을 생성할지
10. 모바일 Push 구현 시점

위 항목은 현재 구현을 막지 않는 범위에서 확장 가능한 구조로 두고 DECISION_REQUIRED로 보고합니다.

==================================================
26. 완료 조건
==================================================

- Notification 핵심 API가 명세와 일치한다.
- 사용자는 본인 알림만 조회·처리할 수 있다.
- 읽음 상태와 unread count가 정확하다.
- 알림 대상 리소스의 접근 가능 상태가 서버에서 계산된다.
- 다른 도메인 Entity·Repository를 직접 참조하지 않는다.
- Discord 전달 상태와 테이블을 Notification이 소유한다.
- Discord Bot이 없어도 Fake Client로 전체 흐름을 테스트할 수 있다.
- Discord 실패가 원본 기능에 영향을 주지 않는다.
- 전달 작업의 유실과 중복 실행을 방지한다.
- 자동·수동 재시도 제한이 동작한다.
- 최신 데이터를 기준으로 재시도한다.
- 문의 개인정보가 Discord Payload에 포함되지 않는다.
- Swagger가 실제 계약과 일치한다.
- Unit·Controller·Integration Test가 통과한다.
- 기존 전체 테스트와 빌드가 통과한다.
- Architecture·Modularity Test가 통과한다.
- Migration은 새 파일로 추가한다.
- Secret이나 실제 Discord ID를 코드에 넣지 않는다.
- 구현 범위와 미구현 후속 범위를 PR 본문에 명확히 작성한다.