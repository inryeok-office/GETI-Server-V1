현재 GETI-Server-V1에서 사용자-facing Notification 도메인 구현을 담당한다.

Repository:
- inryeok-office/GETI-Server-V1

기준 Branch:
- develop

중요:
현재 다른 작업자가 다음 기능을 병렬 개발 중이다.

1. Issue #171
   - Job/Search 응답에 사용자 bookmarked 상태 노출
   - 주 변경 영역:
     domain.job
     domain.search
     domain.recommendation 일부 공개 Contract

2. Portfolio 신규 도메인
   - PortfolioRequest
   - PortfolioTarget
   - PortfolioSubmission
   - Portfolio File 연동
   - Admin 제출 현황

Notification 작업은 위 두 작업과 최대한 독립적으로 진행해야 한다.

특히:
- Job/Search DTO 수정 금지
- Recommendation 수정 금지
- Portfolio 구현을 기다리지 않음
- 기존 Discord Notification Delivery를 불필요하게 리팩터링하지 않음
- 다른 작업자의 Branch에서 분기하지 않음
- 최신 origin/develop 기준 별도 Worktree 사용
- 자동 Merge 금지


======================================================================
0. 이번 작업의 목적
======================================================================

GETI에는 이미 "Notification"이라는 이름으로
Discord 메시지 전송/Delivery 관련 구현이 존재할 가능성이 높다.

하지만 이번 작업의 핵심은 Discord 전송이 아니다.

이번 작업에서 구현해야 하는 것은:

"로그인한 GETI 사용자가 Web/App에서 확인하는 개인 알림 Inbox"

이다.

Notion 기능명세 기준 사용자 알림은:

- 최신순 알림 목록
- 읽음 / 미읽음 상태
- 미읽음 개수
- 단건 읽음
- 전체 읽음
- 알림 삭제
- 알림 대상 화면 이동 정보
- 대상 접근 가능 여부
- Student App 진입점

을 제공해야 한다.

따라서 반드시 다음 개념을 분리한다.

A. Personal Notification
   GETI 사용자에게 저장되는 DB 기반 알림

B. Discord Delivery
   Discord Bot/Channel로 전달되는 외부 메시지

둘은 같은 "Notification" 이름을 사용할 수 있으나
서로 다른 책임이다.

이번 작업에서 Discord Delivery 구조를
Personal Notification 구조와 합치거나 대규모 재설계하지 않는다.


======================================================================
1. 시작 전 Repository 최신 상태 확인
======================================================================

반드시 먼저:

git status
git branch --show-current
git fetch origin
git log --oneline origin/develop -20

GitHub에서 확인:

- Notification 관련 OPEN/CLOSED Issue
- Notification 관련 OPEN/CLOSED PR
- Discord Notification 관련 Issue/PR
- Application Notification 관련 Issue/PR
- Program Notification 관련 Issue/PR
- Job Notification 관련 Issue/PR

특히 과거 Application Phase에서
Notification 작업이 이미 일부 구현됐는지 확인한다.

검색어:

Notification
notification
PersonalNotification
UserNotification
NotificationController
NotificationService
NotificationRepository
NotificationType
NotificationTargetType
unread
markRead
readAll
notificationCount
Discord
DiscordNotification
NotificationEvent

중복 구현이 이미 존재한다면
절대 같은 기능을 다시 만들지 않는다.

먼저 다음 Matrix를 작성한다.

기능 | Notion 요구 | 현재 develop 구현 | 상태
---- | ---------- | ---------------- | ----
개인 알림 저장 | YES | ? | MATCH/GAP
알림 목록 | YES | ? | MATCH/GAP
미읽음 수 | YES | ? | MATCH/GAP
단건 읽음 | YES | ? | MATCH/GAP
전체 읽음 | YES | ? | MATCH/GAP
삭제 | YES | ? | MATCH/GAP
targetType/targetId | YES | ? | MATCH/GAP
targetAvailable | YES | ? | MATCH/GAP
Push Device | 명세 존재 | ? | 별도 판단
Discord Delivery | 별도 기능 | ? | 기존 유지

실제 최신 코드가 Source of Truth다.


======================================================================
2. Worktree / Branch
======================================================================

현재 작업 디렉터리에 다른 Feature 작업이 있으면
반드시 별도 Worktree 사용.

권장:

git worktree add ../GETI-Server-V1-notification \
  -b feature/user-notification \
  origin/develop

실제 Branch Naming Convention과
새로 생성할 GitHub Issue 번호가 있다면:

feature/{issue-number}-user-notification

형식 우선.

다른 Feature Branch에서 분기 금지.


======================================================================
3. GitHub Issue 처리
======================================================================

Personal Notification 전체 구현을 추적하는
기존 OPEN Issue가 있는지 먼저 확인한다.

있으면 재사용.

없으면 신규 Issue를 생성한다.

권장 Issue:

[FEAT] 사용자 개인 알림 조회·읽음 처리 구현

Issue에는 최소 다음을 기록한다.

- Discord Notification Delivery와 별개임
- Notion 사용자 알림 API 구현
- Notification Inbox
- unreadCount
- 단건/전체 읽음
- 삭제
- targetType/targetId
- targetAvailable
- Push Notification은 별도 Phase 가능
- Job/Search/Portfolio 병렬 작업과 파일 충돌 최소화

State Label Convention:

ready
→ in progress
→ review

기존 Repository Convention을 따른다.


======================================================================
4. Notion Contract — 알림 목록
======================================================================

현재 Notion API:

GET /api/v1/notifications

권한:

STUDENT
TEACHER
DEVELOPER

Query Parameters:

unreadOnly: Boolean
notificationType: NotificationType | null
page: Int
size: Int

Response 구조:

{
  "success": true,
  "data": {
    "content": [
      {
        "notificationId": "Long",
        "notificationType": "NotificationType",
        "title": "String",
        "content": "String",
        "targetType": "NotificationTargetType | null",
        "targetId": "Long | null",
        "targetAvailable": "Boolean",
        "targetUnavailableReason": "NotificationTargetUnavailableReason | null",
        "read": "Boolean",
        "createdAt": "LocalDateTime"
      }
    ],
    "page": "Int",
    "size": "Int",
    "totalElements": "Long",
    "totalPages": "Int",
    "first": "Boolean",
    "last": "Boolean",
    "unreadCount": "Long"
  },
  "meta": {
    "requestId": "UUID"
  }
}

정렬:

최신순.

사용자 A의 요청에서
사용자 B의 Notification이 절대 노출되면 안 된다.


======================================================================
5. Notion Contract — 읽음 처리
======================================================================

현재 Notion API:

PATCH /api/v1/notifications/read

Request:

{
  "scope": "NotificationReadScope",
  "notificationId": "Long | null"
}

지원 개념:

단건 읽음
전체 읽음

Response:

{
  "success": true,
  "data": {
    "unreadCount": "Long",
    "updatedCount": "Long"
  },
  "meta": {
    "requestId": "UUID"
  }
}

존재하지 않는 Notification:

404 NOTIFICATION_NOT_FOUND

다른 사용자의 Notification ID:

보안상 자신의 Resource처럼 다뤄야 한다.

기존 프로젝트의 Ownership Error Convention을 확인해
404 또는 403 중 기존 정책을 따른다.

절대 다른 사용자의 Notification을 수정하지 않는다.


======================================================================
6. Notion Contract — 알림 삭제
======================================================================

Notion에는:

DELETE /api/v1/notifications/{notificationId}

알림 삭제 기능이 존재한다.

최신 상세 Contract를 Notion/Repository에서 확인한다.

삭제는 반드시 현재 로그인 사용자의 Notification만 가능.

다른 사용자의 알림 삭제 금지.

Hard Delete / Soft Delete 여부는
기존 Notification Schema와 프로젝트 정책을 확인한다.

특별한 Audit 요구가 없다면
개인 Inbox 알림은 Hard Delete가 자연스러울 수 있으나
임의 결정하지 않는다.

기존 Schema가 deletedAt/status를 가지고 있다면 재사용한다.


======================================================================
7. Personal Notification Entity
======================================================================

기존 Entity/Schema가 없다면 최소 다음 개념이 필요하다.

UserNotification 또는 Notification

- id
- recipientMemberId
- notificationType
- title
- content
- targetType
- targetId
- read
- readAt
- createdAt

정확한 Column 이름은 현재 Migration Convention을 따른다.

가능하면:

read: Boolean

보다

readAt: LocalDateTime?

를 Source of Truth로 두고

read = readAt != null

형태도 검토한다.

하지만 기존 Notion/Schema가 Boolean을 요구하거나
Repository Convention이 Boolean이면 그대로 따른다.

불필요하게 둘 다 저장하지 않는다.


======================================================================
8. NotificationType
======================================================================

현재 Repository/Notion의 실제 NotificationType Enum을 먼저 찾는다.

임의 Enum 생성 금지.

필요한 Category 후보는 제품 기능상 다음과 같을 수 있다.

JOB
APPLICATION
PROGRAM
PORTFOLIO
SYSTEM

또는 더 구체적으로:

JOB_PUBLISHED
JOB_CLOSED
APPLICATION_EDIT_ALLOWED
APPLICATION_REVISION_REQUESTED
APPLICATION_APPROVED
APPLICATION_REJECTED
PROGRAM_PUBLISHED
PROGRAM_APPLICATION_STATUS_CHANGED
PORTFOLIO_REQUEST_PUBLISHED

등.

하지만 이번 작업에서
새 Enum 목록을 마음대로 확정하지 않는다.

우선:

1. 기존 NotificationType
2. Notion Enum 정의
3. Domain Event 종류

순으로 조사한다.

Inbox Core 구현에 필요하지 않은 Event Type은
추후 추가 가능하도록 설계한다.


======================================================================
9. NotificationTargetType
======================================================================

알림을 클릭했을 때
Client가 어떤 화면으로 이동해야 하는지 알려준다.

Notion에서는:

targetType
targetId

를 사용한다.

가능 Target:

JOB
JOB_APPLICATION
PROGRAM
PROGRAM_APPLICATION
PORTFOLIO_REQUEST
MEMBER
NONE

등이 있을 수 있다.

정확한 Enum은 기존 Notion 정의 확인.

targetType == null
targetId == null

인 System Notification도 허용 가능한지 확인.

Client가 URL 문자열을 직접 저장하는 방식보다
Domain Target 정보로 이동하는 구조를 유지한다.

Notification에 Web URL을 DB에 하드코딩하지 않는다.


======================================================================
10. targetAvailable
======================================================================

Notion 계약상 중요한 요구다.

알림 생성 당시 Target이 존재했더라도
나중에는:

- 삭제됨
- 비공개됨
- 권한을 잃음
- 마감됨
- 접근 정책 변경

될 수 있다.

GET /api/v1/notifications 응답에는:

targetAvailable
targetUnavailableReason

을 포함해야 한다.

Client는 targetAvailable=false일 경우
원래 상세 화면으로 이동하지 않고
사유 안내를 보여준다.

중요:

targetAvailable 값을 Notification Table에 영구 저장하는 것보다
조회 시점 현재 Target 상태를 평가하는 것이 자연스럽다.

예:

Notification:
targetType = JOB
targetId = 12

조회 시:

Job Target Availability Resolver
→ 현재 사용자에게 조회 가능한 Job인지

판단.

하지만 모든 Domain Repository를
Notification에서 직접 참조하면 안 된다.


======================================================================
11. Target Availability Architecture
======================================================================

Notification Domain이 직접:

JobRepository
ApplicationRepository
ProgramRepository
PortfolioRepository

를 참조하지 않는다.

확장 가능한 Resolver Contract를 만든다.

예시 개념:

interface NotificationTargetAvailabilityResolver {

    val targetType: NotificationTargetType

    fun resolve(
        targetId: Long,
        requesterMemberId: Long,
    ): NotificationTargetAvailability
}

또는 Registry:

NotificationTargetAvailabilityResolverRegistry

각 Domain이 자기 Target의
접근 가능 여부를 판정하는 Adapter를 제공하는 구조를 검토한다.

하지만 현재 병렬 개발 중인:

Job/Search #171
Portfolio 신규 Domain

을 직접 수정해야 하는 구조를 이번 PR에서 강제하지 않는다.

따라서 Phase 1에서는
이미 안정적으로 조회 가능한 Target만 Resolver 구현하고,
미구현 Domain은 명확한 Fallback을 사용하는 방법을 검토한다.

Fallback은 무조건 true 금지.

안전한 기본은:

targetType 없음
→ available=true

알 수 없는/미구현 Target
→ available=false + UNKNOWN/UNAVAILABLE 계열 Reason

단 실제 Enum/Contract 확인 후 결정.


======================================================================
12. 병렬 작업 충돌 방지 — Job
======================================================================

현재 #171에서:

JobServiceImpl
JobDetailResponse
JobSearchServiceImpl
JobSummaryResponse
Recommendation Bookmark Accessor

등을 수정할 수 있다.

Notification 구현에서 위 파일을 수정하지 않는다.

Job Notification Target Resolver가 필요하다면
가능하면 기존 Job 공개 Query/Access Contract를 사용한다.

새 Job 파일 수정이 꼭 필요하면:

이번 PR에서 하지 않고
후속 Issue로 분리.

#171 Merge 후 별도 PR.


======================================================================
13. 병렬 작업 충돌 방지 — Portfolio
======================================================================

Portfolio Domain은 현재 신규 구현 중이다.

따라서 이번 Notification 작업에서:

PortfolioRequest Entity
PortfolioService
PortfolioRepository
Portfolio Controller

를 참조하거나 수정하지 않는다.

Portfolio Notification Event 연동도
Portfolio Core가 아직 안정되지 않았다면 하지 않는다.

대신 Notification Domain이 사용할 수 있는
공개 Creation Port/Event Consumer Contract만 준비한다.

Portfolio 작업 완료 후:

PortfolioRequestPublishedEvent
→ Personal Notification 생성

은 Follow-up으로 연결 가능.


======================================================================
14. Discord Notification과 분리
======================================================================

현재 Repository의 Discord 관련 코드를 전수 조사한다.

예:

DiscordDelivery
DiscordNotification
DiscordMessage
NotificationDelivery
JobDiscordPayload
ProgramDiscordPayload
Inquiry Discord

등.

이번 작업에서 절대 하지 말 것:

Discord 전송 성공 여부를
Personal Notification의 read 상태와 연결.

Discord messageId를
User Notification targetId로 사용.

Discord Entity를
User Inbox Entity로 재사용.

Discord Delivery Retry를
Personal Notification Retry로 사용.

둘의 Lifecycle은 완전히 다르다.

Personal Notification:
DB에 생성되면 Inbox에 존재.

Discord Delivery:
외부 Bot 호출 성공/실패/재시도 관리.

필요하면 같은 Domain Event를 각각 소비할 수는 있다.

예:

ApplicationApprovedEvent
  ├─ Personal Notification Listener
  └─ Discord Listener

이 구조는 허용.


======================================================================
15. Notification 생성 공개 Contract
======================================================================

다른 Domain에서 User Notification을 만들 수 있도록
공개 Port를 제공한다.

예시 개념:

interface NotificationCreatePort {

    fun create(
        recipientMemberId: Long,
        type: NotificationType,
        title: String,
        content: String,
        targetType: NotificationTargetType?,
        targetId: Long?,
    ): Long
}

Batch 대상이 많은 경우:

fun createAll(...)

을 고려한다.

하지만 API 형태는 프로젝트 Convention 확인.

중요:

다른 Domain이 NotificationRepository를 직접 사용하지 않게 한다.

Application
Program
Portfolio
Job

→ Notification 공개 Port 또는 Domain Event

형태.


======================================================================
16. Domain Event 방식 우선 검토
======================================================================

GETI에서 이미 Domain Event Pattern을 사용한다면
Notification 생성은 Event Listener가 자연스럽다.

예:

ApplicationApprovedEvent
→ Notification Listener
→ Notification 생성

ApplicationRevisionRequestedEvent
→ Notification 생성

ProgramPublishedEvent
→ Notification 생성

PortfolioRequestPublishedEvent
→ Notification 생성

장점:

Application/Program/Portfolio에서
Notification 내부 구현을 알 필요가 없다.

기존 프로젝트의:

@EventListener
@TransactionalEventListener
AFTER_COMMIT

사용 Convention을 확인한다.

Notification 생성은 일반적으로
원본 Transaction이 Commit된 이후 실행하는 것이 적절하다.

원본 Application 승인 Transaction이 rollback됐는데
알림만 생성되는 상황을 방지한다.

다만 기존 Notification/Discord Event Pipeline이 있으면
그 Pattern을 최대한 재사용.


======================================================================
17. Notification 생성의 필수 속성
======================================================================

개인 Notification 생성 시 최소:

recipientMemberId
notificationType
title
content
targetType
targetId

필요.

createdAt은 Server 생성.

read 기본값 false.

readAt은 null.

Client 입력으로 Notification 생성하는 Public API는
이번 범위에 없다.

즉:

POST /api/v1/notifications

같은 사용자 임의 생성 API 만들지 않는다.

Notification은 System/Domain Event가 생성한다.


======================================================================
18. 알림 목록 Filtering
======================================================================

GET /api/v1/notifications

unreadOnly=true

이면 현재 사용자 + unread 알림만 반환.

notificationType가 있으면 해당 Type만.

둘 다 있으면 AND.

예:

recipient = currentUser
AND read = false
AND type = APPLICATION

정렬:

createdAt DESC

같은 createdAt을 가질 수 있으므로
안정적인 Pagination이 필요하면:

createdAt DESC, id DESC

같은 Tie-breaker 검토.


======================================================================
19. unreadCount
======================================================================

unreadCount는 현재 Filter된 Page의 unread 개수가 아니다.

"현재 로그인 사용자 전체 미읽음 알림 수"

로 구현하는 것을 우선한다.

예:

notificationType=JOB Filter로 목록을 조회해도
unreadCount는 Header Badge에서 사용할
전체 Inbox 미읽음 수여야 하는지 Notion/Client 요구 확인.

Notion 기능명세는 Header 미읽음 개수를 요구하므로
전체 User unread count가 자연스럽다.

정확한 Contract가 다르면 문서를 우선한다.

Page content를 가져와 Memory count 하지 않는다.

DB COUNT Query 사용.


======================================================================
20. 단건 읽음
======================================================================

scope가 SINGLE 계열이면:

notificationId 필수.

검증:

- Notification 존재
- currentUser 소유

이미 읽은 알림을 다시 읽음 처리할 경우:

멱등 동작 우선 검토.

updatedCount:

실제로 상태가 변경된 Row 수.

예:

unread → read
updatedCount=1

already read
updatedCount=0

단 기존 Notion Enum/정책 확인.


======================================================================
21. 전체 읽음
======================================================================

scope가 ALL 계열이면:

현재 사용자 unread 알림 전체를 읽음 처리.

다른 사용자는 영향 없음.

N개 Notification Entity를 모두 조회해서:

forEach {
    it.read()
}

하는 것보다
Repository Bulk Update가 자연스러운지 검토.

다만 Entity Lifecycle/Audit Hook이 필요한 프로젝트라면
Convention 우선.

updatedCount를 반환할 수 있어야 한다.


======================================================================
22. 삭제
======================================================================

DELETE /api/v1/notifications/{notificationId}

현재 사용자 소유 확인.

삭제 후 unreadCount를 Response에서 반환하는지
Notion 상세 API를 확인.

명세가 204라면 Body 추가 금지.

이미 삭제된 알림 재삭제:

멱등 204
vs
404

기존 DELETE API Convention 우선.

임의 정책 만들지 않는다.


======================================================================
23. Pagination
======================================================================

프로젝트 공통 Page Response 사용.

Notion Response:

content
page
size
totalElements
totalPages
first
last
unreadCount

Spring Page 구조와 맞춰 구현.

size 상한은 기존 Controller Convention 사용.

새 Notification 전용 Pagination 규칙 만들지 않는다.


======================================================================
24. Push Notification
======================================================================

Notion에는 별도로:

푸시 기기 등록·갱신
푸시 기기 해제
푸시 알림 설정 조회
푸시 알림 설정 변경

API가 존재한다.

하지만 이번 병렬 작업에서
FCM/APNs 같은 Push Provider까지 한 번에 구현하면
범위가 커질 수 있다.

먼저 Repository에 Push 관련 구현이 존재하는지 조사한다.

있다면 Integration.

없다면 이번 Core PR의 P0 범위에서는:

- 개인 Notification DB
- 목록
- 읽음
- 삭제
- 생성 Port/Event

까지만 우선 완료.

Push Device / Push Provider는 별도 Issue로 분리한다.

단 현재 Notion에 계약이 있으므로
누락 자체는 반드시 Follow-up Issue로 남긴다.


======================================================================
25. Push Device를 구현해야 하는 경우
======================================================================

Repository에 이미 기반이 있거나
담당 범위에 포함시키기로 결정한 경우만.

최소 개념:

NotificationDevice

- id
- memberId
- deviceToken
- platform
- enabled
- updatedAt

Token Unique 정책 확인.

다른 회원이 동일 Token 재등록할 때
소유권 이전 정책 확인.

Client에서 Push Token을 저장하지만
Provider Secret은 Client로 노출 금지.

이번 Core와 분리 가능하면 별도 PR 권장.


======================================================================
26. 기존 Application Event 연동
======================================================================

Application은 사용자 알림이 가장 필요한 안정된 Domain이다.

현재 develop에서 Application 관련 Event를 조사한다.

최소 후보:

수정 허용
수정 요청
승인
거절

이미 Event가 존재한다면
Personal Notification Listener 추가를 검토.

하지만 Application 담당자의 OPEN PR과
같은 파일을 건드려 충돌한다면 연동은 별도 PR로 분리한다.

우선 Notification Core를 완성하는 것이 중요하다.

Core PR에서 Application Service를 직접 수정하지 않는다.


======================================================================
27. Program Event 연동
======================================================================

Program도 다음 Event 후보가 있다.

Program 공개
신청 상태 변화
취소/마감

기존 Event 존재 여부 확인.

없으면 이번 Core에서 Program Service를 수정해
억지로 Event를 만들지 않는다.

Notification 생성 Port가 준비된 뒤
Program Follow-up Issue로 분리 가능.


======================================================================
28. Notification Target Availability Reason
======================================================================

Notion Response:

targetUnavailableReason

이 존재한다.

기존 Enum 확인.

가능 개념:

NOT_FOUND
DELETED
NOT_VISIBLE
ACCESS_DENIED
CLOSED
UNAVAILABLE

하지만 정확한 값은 임의 생성 금지.

Client가 Reason을 직접 분기할 수 있으므로
API Contract 영향이 크다.

Notion Enum 정의 없으면
최소 정책을 사용자에게 DECISION_REQUIRED로 보고.


======================================================================
29. DB Migration
======================================================================

기존 개인 Notification Table이 없다면
신규 Migration 생성.

기존 Migration 수정 금지.

Migration Version은 최신 develop 기준 다음 번호.

최소 Index 검토:

recipient_member_id
recipient_member_id + read
recipient_member_id + created_at
recipient_member_id + notification_type + created_at

실제 Query Plan을 고려하되
불필요한 Index 남발 금지.


======================================================================
30. Repository Query
======================================================================

필요 Query 후보:

- current user notifications pageable
- current user unread notifications pageable
- current user + type pageable
- current user + unread + type pageable
- unread count
- notification by id + member
- bulk mark read

Specification / QueryDSL / JPQL 등
현재 프로젝트 Convention을 따른다.

조건 조합 때문에 Repository Method 이름이 지나치게 길어지면
현재 프로젝트 Dynamic Query Pattern 조사.


======================================================================
31. 인증 / Security
======================================================================

현재 SecurityConfig가
개발 편의상 global permitAll일 수 있다.

이번 작업에서 global permitAll을 제거하지 않는다.

하지만 Notification Service는
현재 로그인 사용자 ID를 기준으로 동작해야 한다.

memberId를:

Query Parameter
Request Body

로 받지 않는다.

예:

GET /api/v1/notifications?memberId=1

금지.

Authentication Principal 기준.

정상 Security Rules가 복구됐을 때:

STUDENT
TEACHER
DEVELOPER

모두 자신의 Notification API 사용 가능해야 함.

필요하면 정상 Rule에만 최소 Endpoint 추가.

전역 Security 구조 리팩터링 금지.


======================================================================
32. Privacy / 데이터 격리
======================================================================

Notification은 사용자 개인 데이터다.

다음 테스트 필수:

User A:
notification #1

User B:
notification #2

A가:

GET 목록
→ #2 안 보임

PATCH #2 read
→ 실패

DELETE #2
→ 실패

Target Availability 계산에서도
다른 사용자의 권한으로 계산하지 않는다.


======================================================================
33. 상태 변경 Transaction
======================================================================

읽음 처리:

Transaction 필요.

단건:

select/lock이 필요한지 검토.

단순 멱등 update라면
과도한 pessimistic lock 불필요.

전체 읽음:

Bulk Update Transaction.

Concurrent:

전체 읽음
vs
새 Notification 생성

Race에서
읽음 처리 시작 이후 새로 생성된 Notification까지
읽음으로 만들어야 하는지 정책 확인.

일반적으로:
UPDATE WHERE read=false
실행 시점 이전 존재 Row만 영향을 받는 정도면 충분.

불필요한 전역 Lock 금지.


======================================================================
34. Personal Notification 생성 Transaction
======================================================================

Domain Event Listener에서 생성한다면:

@TransactionalEventListener(AFTER_COMMIT)

또는 기존 Event Convention 우선.

원본 Business Transaction이 실패했는데
Notification이 남는 문제 방지.

Notification DB 저장 실패가
원본 Application/Program/Job Transaction을
rollback시키지 않는 구조를 우선 검토.

단 중요한 Transactional Consistency 정책이 이미 있으면
그것을 따른다.


======================================================================
35. Error Handling
======================================================================

최소 확인:

NOTIFICATION_NOT_FOUND

추가 ErrorCode가 필요한지 확인:

NOTIFICATION_ACCESS_DENIED
NOTIFICATION_READ_REQUEST_INVALID

등.

같은 의미의 기존 Global ErrorCode가 있으면 재사용.

scope=SINGLE인데 notificationId=null:

400 Validation

scope=ALL인데 notificationId가 들어온 경우:

무시
vs
400

Notion/기존 Request Validation Convention 확인.


======================================================================
36. API DTO
======================================================================

권장 DTO 역할:

NotificationResponse
NotificationListResponse
NotificationReadRequest
NotificationReadResponse

정확한 Naming은 프로젝트 Convention.

Entity 직접 반환 금지.

target availability는 DTO 조립 시 계산.

Repository Entity에:
targetAvailable
targetUnavailableReason

을 영구 저장하지 않는 방향 우선.


======================================================================
37. 테스트 — Repository
======================================================================

최소:

- member별 격리
- unread filter
- type filter
- unread + type filter
- 최신순 정렬
- unread count
- bulk mark read
- pagination

가능하면 PostgreSQL Testcontainers.


======================================================================
38. 테스트 — Service
======================================================================

목록:

1.
현재 사용자의 알림만 반환

2.
최신순

3.
unreadOnly

4.
notificationType

5.
두 Filter 조합

6.
unreadCount 정확

7.
targetAvailable 계산

8.
target null System Notification

읽음:

9.
단건 unread → read

10.
이미 read → 멱등

11.
전체 읽음

12.
다른 사용자 알림 수정 불가

13.
없는 ID

삭제:

14.
본인 알림 삭제

15.
다른 사용자 알림 삭제 불가


======================================================================
39. 테스트 — Controller
======================================================================

GET /api/v1/notifications

검증:

- Query Parameter
- Pagination
- Response JSON
- unreadCount
- target fields
- false/null Field가 필요 이상으로 누락되지 않는지

PATCH /api/v1/notifications/read

검증:

- SINGLE
- ALL
- Validation
- Status Code
- Error Code

DELETE:

- 정상
- 없는 ID
- 다른 사용자


======================================================================
40. 테스트 — Creation Port
======================================================================

NotificationCreatePort 또는 Event Listener:

recipient=A
→ A Inbox 생성

recipient=B
→ B Inbox 생성

title/content/type/target 정확.

read 기본 false.

동일 Event 재처리 시
중복 Notification 허용 여부 확인.

기존 Domain Event에 eventId/idempotency key가 없다면
억지로 대규모 Dedup Infra 추가하지 않는다.


======================================================================
41. 테스트 — Architecture
======================================================================

필수:

./gradlew test --tests "*Notification*"

./gradlew test --tests "*ModularityTest*"
./gradlew test --tests "*PackageArchitectureTest*"
./gradlew test --tests "*ModuleDocumentationTest*"
./gradlew test --tests "*OpenApiDocumentationTest*"

그리고:

./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew clean test build

관련 Integration Test도 실행.


======================================================================
42. OpenAPI
======================================================================

반드시 문서화:

GET /api/v1/notifications
PATCH /api/v1/notifications/read
DELETE /api/v1/notifications/{notificationId}

설명에:

"현재 로그인한 사용자의 알림"

임을 명시.

unreadCount 의미 명시.

targetType/targetId/targetAvailable 의미 명시.

NotificationReadScope Enum 문서화.


======================================================================
43. 이번 병렬 작업의 P0 Scope
======================================================================

반드시 이번 Core에서 완료:

- Personal Notification Entity/Schema
- Repository
- Notification 생성 공개 Contract
- GET 목록
- Filter
- Pagination
- unreadCount
- 단건 읽음
- 전체 읽음
- 삭제
- 본인 소유권 검증
- targetType / targetId Response
- 기본 targetAvailable Architecture
- Unit Test
- Controller Test
- Persistence Integration
- Architecture Test
- Swagger


======================================================================
44. P1 Scope
======================================================================

Core가 안정되면 같은 담당자가 후속으로:

- Application Event → Personal Notification
- Program Event → Personal Notification
- 기존 안정 Domain TargetAvailability Resolver
- 알림 삭제 정책 보강

진행 가능.


======================================================================
45. P2 / 별도 병렬 Scope
======================================================================

다음은 Core와 분리 가능:

- Push Device 등록
- Push Device 해제
- Push 설정 조회
- Push 설정 변경
- FCM/APNs 실제 Provider
- Portfolio Notification Event
- Job #171과 충돌할 Target Resolver
- 대량 Notification 성능 최적화
- Notification Retention/Cleanup

각각 별도 Issue 검토.


======================================================================
46. 이번 작업에서 절대 하지 말 것
======================================================================

- #171 Job/Search 파일 수정
- JobSummaryResponse 수정
- JobDetailResponse 수정
- JobSearchServiceImpl 수정
- Recommendation 수정
- Portfolio 구현 파일 수정
- Discord Notification 전체 리팩터링
- Discord messageId를 Personal Notification ID로 재사용
- MemberRepository 직접 참조
- JobRepository 직접 참조
- ApplicationRepository 직접 참조
- ProgramRepository 직접 참조
- PortfolioRepository 직접 참조
- 다른 사용자의 Notification 조회/수정 허용
- Notification 생성 Public 사용자 API 추가
- Push Provider까지 무조건 한 PR에 구현
- Product Contract 없이 Enum 값 대량 생성
- 기존 Migration 수정
- Security global permitAll 제거
- develop/main 직접 Commit
- Force Push
- 자동 Merge


======================================================================
47. 병렬 충돌 확인
======================================================================

작업 완료 전:

git diff --name-only origin/develop...HEAD

확인.

현재 #171에서 예상되는:

domain/job/**
domain/search/**
domain/recommendation/** 일부

변경과 겹치는지 확인.

Portfolio 작업의:

domain/portfolio/**
FilePurpose/FileOwnerType 일부

와 겹치는지도 확인.

가능하면 Notification Core 변경은:

domain/notification/**
migration
global error
security normal rules
architecture docs
tests

범위 중심으로 유지.

공용 Enum/Error 파일 변경은
필요 최소한.


======================================================================
48. Commit
======================================================================

Commit Convention:

<type>: <한글 작업 내용>

권장:

feat: 사용자 개인 알림 조회 및 읽음 처리 구현

필요하면:

feat: 개인 알림 저장 및 생성 구조 구현
feat: 개인 알림 조회 및 읽음 API 구현
test: 개인 알림 기능 검증 추가

정도로 분리.

Commit 과도하게 쪼개지 않는다.


======================================================================
49. PR
======================================================================

Base:

develop

Title:

[FEAT] 사용자 개인 알림 조회·읽음 처리 구현

Body:

Closes #{실제 Issue 번호}

반드시 포함:

## 배경

Discord Delivery는 존재하지만
Web/App 사용자 Inbox용 Personal Notification 기능이
별도 요구됨.

## 기존 상태

실제 조사 결과 작성.

## 구현 범위

- Storage
- Creation Port/Event
- 목록
- 미읽음
- 읽음
- 삭제
- Target Contract

## Discord와의 책임 분리

명확히 설명.

## API Contract

GET
PATCH
DELETE

## Target Availability

현재 구현 범위와 Follow-up 범위.

## Architecture

어떤 공개 Port/Event를 사용하는지.

## Security

현재 사용자 기준 데이터 격리.

## Test

Unit
Controller
Persistence
Architecture
OpenAPI
Build

## 병렬 작업 영향

- #171과 변경 파일 중복 여부
- Portfolio 작업과 변경 파일 중복 여부

## 제외 범위

- Push Provider
- Portfolio Event 연동
- Job/Search 수정
- Discord Delivery 리팩터링

## DECISION_REQUIRED

남은 제품 결정만 기록.

자동 Merge 금지.


======================================================================
50. 완료 후 Issue 상태
======================================================================

작업 시작:

ready
→ in progress

PR 생성:

in progress
→ review

develop Merge 후:

Issue가 자동 Close되지 않으면
수동 Close 필요.

이번 작업에서는 Merge하지 않는다.


======================================================================
51. 최종 보고
======================================================================

다음 형식으로 보고:

# Personal Notification 작업 결과

## 1. 기존 Repository 상태

- 기존 Discord Notification 구조
- 기존 Personal Notification 구조 존재 여부
- 재사용한 코드

## 2. 구현 범위

- Entity
- Repository
- Service
- Controller
- Creation Port/Event
- Target Resolver

## 3. API

GET /api/v1/notifications
PATCH /api/v1/notifications/read
DELETE /api/v1/notifications/{notificationId}

각 Request/Response/Status.

## 4. Security

사용자 데이터 격리 방법.

## 5. unreadCount

정확한 정의.

## 6. Target

targetType
targetId
targetAvailable
targetUnavailableReason

현재 지원 범위.

## 7. Discord와의 관계

어떤 코드를 재사용했고
무엇을 분리했는지.

## 8. Test

Unit
Controller
Persistence
Architecture
OpenAPI
Build

## 9. 병렬 충돌

#171 Changed Files와 겹침 여부.
Portfolio 작업과 겹침 여부.

## 10. Git

Issue
Branch
Commit
PR
CI

## 11. Follow-up

Push Device
Push Provider
Application Event Integration
Program Event Integration
Portfolio Event Integration
추가 Target Resolver

## 12. Merge

Merge하지 않음.
사용자 승인 대기.


======================================================================
52. 핵심 설계 원칙
======================================================================

이번 작업에서 가장 중요한 것은:

"Notification"이라는 이름이 같다고 해서
기존 Discord Delivery와 사용자 Inbox를 하나로 뭉치지 않는 것.

Personal Notification은:

사용자별 저장 데이터
읽음 상태
목록
Badge
화면 이동

을 담당한다.

Discord Notification은:

외부 메시지 전달
messageId
Retry
Discord Bot

을 담당한다.

Domain Event가 발생하면:

                    ┌→ Personal Notification
Business Event ─────┤
                    └→ Discord Delivery

처럼 각각 독립적으로 소비할 수 있는 구조가 적절하다.

그리고 현재 병렬 작업 중인:

#171 Job/Search
Portfolio 신규 Domain

을 직접 수정하지 않는 범위에서
Notification Core를 먼저 완성한다.

즉 이번 담당자의 가장 중요한 목표는:

"다른 도메인이 알림을 생성할 수 있는 안정적인 기반"
+
"Client가 실제 Inbox 화면을 구현할 수 있는 API"

를 완성하는 것이다.


======================================================================
53. 구현 우선순위
======================================================================

P0:

Notification Core
→ Entity/Schema
→ Creation Contract
→ 목록
→ unreadCount
→ 단건/전체 읽음
→ 삭제
→ Security
→ Tests

P1:

기존 안정 Domain Event 연동
→ Application
→ Program

P2:

병렬 개발 종료 후
→ Portfolio Event
→ Job Target Resolver

P3:

Push
→ Device
→ Settings
→ Provider

마감이 가까우므로
P0를 먼저 하나의 완결된 PR로 끝내고
P1~P3를 독립 Follow-up Issue/PR로 병렬 처리한다.