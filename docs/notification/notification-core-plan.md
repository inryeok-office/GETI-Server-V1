# Notification Core 구현 명세 (PR 1)

[`Notification_domain_development.md`](./Notification_domain_development.md) 요구사항 문서를 저장소의 실제 상태(병합된 V2/V15 Migration, Spring Modulith 경계, 기존 Job/Program/Application 구현 패턴)에 맞춰 확정한 구현 명세다. 지시서 §24가 "작업 전 확인"하라고 남긴 15개 항목은 모두 아래 §1에서 코드로 확인했고, §25의 미확정 정책 중 이번 범위에 걸리는 것은 §11에 `DECISION_REQUIRED`로 남긴다.

이 문서는 지시서 §22의 **PR 1 (Notification Core) 하나**만 다룬다. PR 2~4는 §12에 방향만 적는다.

---

## 1. 작업 전 확인 결과 (지시서 §24)

| # | 확인 항목 | 실제 상태 |
| --- | --- | --- |
| 1 | Notification Entity·Migration·API 구현 상태 | `notifications` Table(V2:381)과 `Notification` Entity, 빈 `NotificationRepository`가 **이미 존재**. Service·Controller·DTO·Enum은 **전혀 없음**. 어떤 Production 코드도 이 Table에 쓰지 않아 **데이터가 비어 있다** |
| 2 | Program·Job·Inquiry의 Event 발행 여부 | `domain.job.event.JobChangedEvent`(`@NamedInterface`, `jobId`만 담는 최소 계약) 하나뿐. Program·Inquiry·Application은 Event를 **발행하지 않음** |
| 3 | Program에 자체 DiscordDeliveryStatus가 남아 있는지 | **남아 있음**. `domain.program.entity.type.DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED)`가 `ProgramCreateResponse`/`ProgramUpdateResponse`/`ProgramStatusUpdateResponse`의 `discordDelivery`로 **외부 노출 중**. PR #81 리뷰가 이미 교체 필요로 경고 |
| 4 | 기존 Discord 상태가 SENDING/SENT인지 | **그렇다**. `domain.collector.entity.type.JobNotificationDeliveryStatus(PENDING/SENDING/SENT/FAILED)`. 수집 공고용 **Discord Webhook** 발송이 재시도 Scheduler·재기동 복구 Runner까지 포함해 이미 동작 중 |
| 5 | Program DTO를 Notification 소유 타입으로 교체 가능한지 | 가능하지만 **API 응답 값 집합이 바뀌는 Breaking Change**. PR 2 범위 |
| 6 | SecurityConfig의 Notification 접근 규칙 | **없음**. 현재 `anyRequest permitAll`에 걸려 인증 없이 접근 가능 → 이번 PR에서 추가 필요 |
| 7 | API 명세의 Notification Endpoint | 저장소 문서에는 없음. 지시서 §4가 유일한 근거 |
| 8 | Push 관련 기존 코드 | **전무**. FCM/APNs·Device Token 관련 코드·설정·의존성 모두 없음 |
| 9 | Member 공개 Query Port | `domain.member.query.MemberApplicantSnapshotQueryPort` 존재(`application`·`program`이 사용). 이번 범위에서는 **불필요** |
| 10 | 삭제·비공개 대상 접근 가능성 확인 방식 | Job은 `PUBLIC_VISIBLE_STATUSES = {PUBLISHED, CLOSED}`(`JobVisibility.kt:11`)와 `findByIdAndDeletedAtIsNull`. Program도 동일한 Status Enum + Soft Delete |
| 11 | Scheduler와 ShedLock | 평범한 `@Scheduled`만 사용(collector 2개, search 1개). **ShedLock 미도입** |
| 12 | Retry·Resilience4j 패턴 | Resilience4j **미도입**. collector가 `next_retry_at` Column + Sweep Scheduler로 직접 구현 |
| 13 | Outbox·Event 발행 패턴 | Outbox 없음. `@TransactionalEventListener(AFTER_COMMIT)` + 전용 `TaskExecutor`가 유일한 패턴(`JobIndexSyncEventListener`) |
| 14 | 최신 Migration 버전 | **V15**(`V15__extend_program_applications_for_concurrency.sql`) → 이번 PR은 **V16** |
| 15 | 테스트 환경의 PostgreSQL·Redis 의존 | `src/test`는 Docker 없이 통과해야 함. Testcontainers가 필요한 것은 `src/integrationTest`(`./gradlew integrationTest`)에만. CI는 두 Task를 모두 실행 |

---

## 2. 전제: 기존 스키마와 지시서 §3의 불일치

`notifications` Table과 `Notification` Entity는 **이미 `develop`에 병합되어 있다**. 지시서 §3의 필드 목록은 이와 다른 스키마를 전제로 쓰였다.

| 지시서 §3 필드 | V2 실제 Schema | 결정 |
| --- | --- | --- |
| `receiverMemberId` | `recipient_member_id BIGINT NOT NULL` | **유지**. 동의어이고 API에 노출되지 않는다. 이름만 바꾸면 FK 제약명까지 손대는 churn만 생긴다 |
| `targetType` / `targetId` | `resource_type` / `resource_id` | **V16에서 RENAME**. API 응답 필드명(`targetType`/`targetId`)과 DB 어휘를 일치시키고, 형제 다형 참조인 `async_operations.target_type`·`audit_logs.target_type`과도 같아진다 |
| `type` (Enum) | `type VARCHAR(100) NOT NULL` (Kotlin `String`) | **`NotificationType` Enum으로 매핑**. 최장 값이 30자(`JOB_APPLICATION_STATUS_CHANGED`)라 길이 100 그대로 두면 DDL 변경이 필요 없다 |
| `targetUrl` 또는 `deepLink` | Column 없음 | **저장하지 않고 계산**. §5 참고 |
| `updatedAt` | Column 없음 | **V16에서 추가**. 나머지 19개 Table 중 `files`를 제외한 전부가 `updated_at`을 갖는다 |
| (지시서에 없음) | `deleted_at TIMESTAMP` | **유지하되 사용하지 않음**. 알림 삭제 API는 요구사항에 없다(§4 "추가하지 않습니다"). 조회 Query는 방어적으로 `deletedAt IS NULL`로 거른다 |
| `eventId`/`idempotencyKey`/`sourceEvent*` (§6) | Column 없음 | **PR 3으로 미룸**. Unique 제약 범위가 §25.8/25.9(누구에게 어떤 이벤트를 보낼지)에 종속되어 지금은 추측이 된다 |

`notifications` Table은 **비어 있으므로** RENAME에 데이터 위험이 없다.

---

## 3. 확정된 결정 요약

| # | 항목 | 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | PR 범위 | **PR 1 (Notification Core)만**. Discord는 타입 소유권 설계만 문서로 남기고 코드 없음 | 사용자 확정 |
| 2 | 스키마 정합 수준 | 의미가 충돌하는 것만 V16으로 정리(`resource_*`→`target_*`, `updated_at` 추가) | 사용자 확정 |
| 3 | 대상 해석 구조 | **도메인별 공개 Query Port**. §17의 "Resolver Registry"(Notification이 Interface를 정의하고 각 도메인이 구현)는 **사용하지 않는다** | 아래 §4 |
| 4 | Resolver 구현 범위 | **JOB + PROGRAM만**. 나머지 `targetType`은 미등록 → 문서화된 기본값 | 사용자 확정 |
| 5 | Port 조회 단위 | **배치**(`findAllByIds(ids): Map<Long, Snapshot>`) | size=100 목록의 N+1 방지 |
| 6 | `deepLink` | **저장하지 않고 서버가 계산**, 대상 불가 시 `null` | §17이 뷰어별 계산값으로 규정 |
| 7 | `NotificationType` | **§3의 15개를 그대로 채택** | 현재 유일한 근거. §25.7은 `DECISION_REQUIRED`로 보고 |
| 8 | 쓰기 경로 | `NotificationService.create()`(REST 미노출, 내부용)**만** 포함. idempotency Column은 PR 3 | 사용자 확정 |
| 9 | 타인 알림 접근 | **403 `NOTIFICATION_ACCESS_DENIED`** (404 마스킹 안 함) | `APPLICATION_ACCESS_FORBIDDEN`·`NOT_FORM_OWNER`가 모두 403 |
| 10 | 목록 응답 형태 | 도메인 전용 `NotificationListResponse`(`content`/`page`/`size`/`totalElements`/`totalPages`/`first`/`last`) | `ProgramListResponse`·`FormListResponse`와 동일. `global.web.PageResponse`는 어느 도메인도 쓰지 않는다 |
| 11 | 정렬 | **서버 고정** `createdAt DESC, id DESC`. 클라이언트 `sort` 파라미터 무시 | 지시서 §4 |
| 12 | 전체 읽음 처리 | `@Modifying` **Bulk UPDATE** 1회, `readAt`은 요청당 단일 시각 | 건수 상한이 없어 Entity 로딩 방식은 부적절 |
| 13 | 검증 | 로컬에서 `clean test build` + `integrationTest`까지 모두 실행 | 사용자 확정 (Docker Desktop 필요) |

---

## 4. 대상 해석 구조가 "Resolver Registry"가 아닌 이유

지시서 §17은 `NotificationTargetAvailabilityQueryPort`를 Notification이 정의하고 각 도메인이 구현하는 형태를 예시로 든다. **이 저장소에서는 쓸 수 없다.**

Notification이 Interface를 소유하면 구현체를 가진 `domain.job`이 `domain.notification`에 의존하게 된다. 그런데 PR 3에서 Notification은 `domain.job.event`를 구독해야 하므로 `domain.notification → domain.job` 의존이 추가된다. 두 방향이 동시에 존재하는 순간 `ModularityTest`의 `modules.verify()`가 **순환 의존으로 실패한다**.

저장소의 기존 Port 5개(`job.query`, `job.upsert`, `member.query`, `application.query`, `company.query`)는 예외 없이 **"소유 도메인이 `@NamedInterface`로 공개 → 소비자가 import"** 방향이다. 이 방향을 그대로 따르면 `notification → job`, `notification → program` 단방향만 생겨 PR 3의 Event 구독과 충돌하지 않는다.

부수 효과로 **Port는 원시 상태만 반환하고 판단은 Notification이 한다.** Port가 `NotificationTargetUnavailableReason`을 반환하면 `job`이 Notification의 Enum을 알아야 해서 다시 순환이 생기기 때문이다. 이는 `JobApplicationSnapshotQueryPort`가 명시적으로 택한 설계("DRAFT를 포함한 실제 상태를 그대로 돌려주고 판단은 소비자가 수행")와 동일하다.

---

## 5. targetAvailable 판정 규칙

`resolve(targetType, targetId, viewerMemberId)`의 결과를 Notification이 계산한다.

```text
targetType이 JOB / PROGRAM 이 아님        → targetAvailable=false, reason=null,        deepLink=null
targetId == null                          → targetAvailable=false, reason=null,        deepLink=null
대상 Row 없음 | deletedAt != null | DELETED → targetAvailable=false, reason=DELETED,     deepLink=null
status == DRAFT                            → targetAvailable=false, reason=NOT_VISIBLE, deepLink=null
status in {PUBLISHED, CLOSED}              → targetAvailable=true,  reason=null,        deepLink="/jobs/{id}" | "/programs/{id}"
```

- `FORBIDDEN`은 **Enum에만 정의하고 이번 PR에서 사용하지 않는다.** §17의 "MOU Job 접근 권한 없음"에 대응하는 규칙이 코드에 없다 — `MouStatus`는 `companies`의 협약 상태일 뿐 Job 조회 권한이 아니고, `/api/v1/jobs/**`는 현재 `authenticated`만 요구한다. `DECISION_REQUIRED`로 보고한다.
- `deepLink`는 **상대 경로**다. 프론트엔드 base URL 설정이 저장소에 존재하지 않고, web/app 라우트 규약도 확정되지 않았다. 경로 규칙은 `NotificationDeepLink` 한 곳에만 두어 확정 시 한 파일만 고치면 되게 한다. **가정으로 명시 보고한다.**
- `viewerMemberId`는 시그니처에만 두고 이번 PR에서는 판정에 쓰지 않는다(소유권 기반 `FORBIDDEN`이 없으므로). PR 3에서 `JOB_APPLICATION` 등이 붙을 때 사용한다.

---

## 6. 범위

### 구현하는 Endpoint (4개)

```text
GET   /api/v1/notifications                        200  내 알림 목록 (isRead, type, page, size)
GET   /api/v1/notifications/unread-count           200  읽지 않은 개수
PATCH /api/v1/notifications/{notificationId}/read  200  단일 읽음 (멱등)
PATCH /api/v1/notifications/read-all               200  전체 읽음
```

전부 `authenticated`. `authentication.principal as Long`으로 회원 ID를 얻는다(기존 Controller 관례).

### 이번 PR에서 제외 (PR 본문에 사유와 함께 명시)

```text
Discord 전체                discord_deliveries / discord_delivery_attempts / DiscordBotClient Port
                            / FakeDiscordBotClient / Worker / 재시도 / 상태 조회 API / 허용 채널 정책
                            / 환경변수(DISCORD_*)                                        → PR 2
Program·Collector의 기존     domain.program.entity.type.DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED)
Discord 자산 정리            domain.collector...JobNotificationDeliveryStatus(SENDING/SENT)  → PR 2
도메인 Event 연결            Program/Job/Inquiry/Application/Member Event 발행과 수신,
                            중복 알림 방지(idempotencyKey Column·Unique)                  → PR 3
targetAvailable Resolver     JOB_APPLICATION / INQUIRY / PORTFOLIO_REQUEST / MEMBER_APPROVAL → PR 3
                            (inquiry·portfolio는 Service 계층 자체가 없다)
FORBIDDEN 판정               근거 규칙 부재                                              → DECISION_REQUIRED
알림 삭제 API                요구사항에 없음(§4)
모바일 Push                 PUSH_PLATFORM / Device / FCM / APNs                        → PR 4
알림 상세 조회 API           요구사항에 없음. 목록이 상세 필드를 모두 담는다
```

---

## 7. 파일 목록

### `domain/notification` (신규 14, 수정 2)

```text
entity/Notification.kt                                수정  type을 NotificationType으로, resourceType/Id → targetType/Id,
                                                            updatedAt(@UpdateTimestamp) 추가
entity/type/NotificationType.kt                       신규  §3의 15개 값
entity/type/NotificationTargetType.kt                 신규  JOB, JOB_APPLICATION, PROGRAM, PORTFOLIO_REQUEST,
                                                            INQUIRY, MEMBER_APPROVAL
entity/type/NotificationTargetUnavailableReason.kt    신규  DELETED, NOT_VISIBLE, FORBIDDEN
repository/NotificationRepository.kt                  수정  목록(필터 조합)·unread count·bulk read-all Query
service/NotificationService.kt                        신규  Interface
service/impl/NotificationServiceImpl.kt               신규
service/NotificationTargetResolver.kt                 신규  targetType별 그룹핑 후 Port 배치 호출 → 판정
service/NotificationDeepLink.kt                       신규  경로 규칙 단일 지점
dto/NotificationSummaryResponse.kt                    신규
dto/NotificationListResponse.kt                       신규
dto/UnreadNotificationCountResponse.kt                신규
dto/NotificationReadResponse.kt                       신규
dto/NotificationReadAllResponse.kt                    신규
dto/NotificationCreateCommand.kt                      신규  내부 생성용(REST 미노출)
controller/NotificationController.kt                  신규
exception/NotificationErrorCode.kt                    신규
exception/NotificationNotFoundException.kt            신규
exception/NotificationAccessDeniedException.kt        신규
```

### `domain/job` (신규 2)

```text
query/JobNotificationTargetQueryPort.kt          신규  @NamedInterface + JobNotificationTargetSnapshot
service/impl/JobNotificationTargetQueryPortImpl.kt  신규  (JobIndexQueryPortImpl과 같은 이유로 분리)
```

### `domain/program` (신규 2 — `query` Package 자체가 신규)

```text
query/ProgramNotificationTargetQueryPort.kt              신규  @NamedInterface + ProgramNotificationTargetSnapshot
service/impl/ProgramNotificationTargetQueryPortImpl.kt   신규
```

### `global` (수정 1)

```text
security/SecurityConfig.kt   수정  authorize("/api/v1/notifications", authenticated)
                                   authorize("/api/v1/notifications/**", authenticated)
                                   (기존 KDoc의 경로 목록도 함께 갱신)
```

### Migration (신규 1)

```text
src/main/resources/db/migration/V16__align_notifications_for_in_app_api.sql
```

### 문서 (수정 2)

```text
docs/architecture/erd.md   수정  notifications.resource_type/resource_id 언급 2곳(:67, Test 검증 블록)
docs/Notification/notification-core-plan.md   신규  (이 문서)
```

---

## 8. V16 Migration

```sql
ALTER TABLE notifications RENAME COLUMN resource_type TO target_type;
ALTER TABLE notifications RENAME COLUMN resource_id   TO target_id;
ALTER TABLE notifications ADD COLUMN updated_at TIMESTAMP;
UPDATE notifications SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE notifications ALTER COLUMN updated_at SET NOT NULL;
```

- 기존 `V2` Migration은 **수정하지 않는다**.
- `idx_notifications_recipient_read_created (recipient_member_id, is_read, created_at)`가 이미 존재해 목록·unread count 조회를 커버한다. 새 Index는 추가하지 않는다.
- `type` Column은 이미 `VARCHAR(100)`이고 최장 Enum 값이 `JOB_APPLICATION_STATUS_CHANGED`(30자)라 **DDL 변경이 필요 없다.**
- `deleted_at`은 그대로 둔다.
- `UPDATE`가 비어 있는 Table에서 0건을 갱신하는 것은 정상이며, 향후 데이터가 있는 환경에서도 안전하도록 3단계로 나눴다.

---

## 9. 공개 Query Port 계약

```kotlin
// domain/job/query/JobNotificationTargetQueryPort.kt
@NamedInterface
interface JobNotificationTargetQueryPort {
    /** 존재하지 않는 id는 결과 Map에서 빠진다. 삭제된 공고도 포함해서 돌려준다. */
    fun findAllByIds(jobIds: Set<Long>): Map<Long, JobNotificationTargetSnapshot>
}

@NamedInterface
data class JobNotificationTargetSnapshot(
    val jobId: Long,
    /** `JobStatus.name`. DRAFT·DELETED를 포함한 실제 상태 그대로다. */
    val status: String,
    val deleted: Boolean,
)
```

`ProgramNotificationTargetQueryPort`도 동일한 형태(`programId`, `status`=`ProgramStatus.name`, `deleted`)다.

- 구현은 `findAllById`(JpaRepository 기본, Soft Delete를 거르지 않음)를 쓴다. `findByIdAndDeletedAtIsNull`은 삭제 대상을 `DELETED`로 판정해야 하므로 쓸 수 없다.
- `@Transactional(readOnly = true)`.
- `id`가 비어 있으면 조회하지 않고 빈 Map을 반환한다.

`NotificationTargetResolver`는 목록의 항목들을 `targetType`으로 그룹핑해 도메인당 **최대 1회**만 Port를 호출한다 → size=100 목록에서도 추가 Query 2회.

---

## 10. API 상세

### GET /api/v1/notifications

Query: `isRead: Boolean?`, `type: NotificationType?`, `page: Int = 0`, `size: Int = 20` (최대 100은 `WebPageableConfig`가 이미 강제).

응답(`ApiResponse<NotificationListResponse>`):

```jsonc
{
  "success": true,
  "data": {
    "content": [{
      "notificationId": 1, "type": "PROGRAM_PUBLISHED", "title": "...", "content": "...",
      "targetType": "PROGRAM", "targetId": 123,
      "targetAvailable": true, "targetUnavailableReason": null,
      "deepLink": "/programs/123",
      "isRead": false, "readAt": null, "createdAt": "2026-08-07T10:00:00"
    }],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
  },
  "meta": { "requestId": "..." }
}
```

정렬은 `createdAt DESC, id DESC` 고정이며 클라이언트 `sort`는 무시한다. `deletedAt IS NULL`만 조회한다.

### GET /api/v1/notifications/unread-count

`{ "unreadCount": 3 }`. `countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull`. 대상 해석을 하지 않는다(읽음 여부와 무관).

### PATCH /api/v1/notifications/{notificationId}/read

- 없음 → 404 `NOTIFICATION_NOT_FOUND`
- 타인 소유 → 403 `NOTIFICATION_ACCESS_DENIED`
- 이미 읽음 → **200, `readAt` 유지**(멱등, 재갱신하지 않음)
- 응답 `{ "notificationId": 1, "isRead": true, "readAt": "..." }`

### PATCH /api/v1/notifications/read-all

```kotlin
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    UPDATE Notification n SET n.isRead = true, n.readAt = :readAt, n.updatedAt = :readAt
    WHERE n.recipientMemberId = :memberId AND n.isRead = false AND n.deletedAt IS NULL
""")
fun markAllAsRead(memberId: Long, readAt: LocalDateTime): Int
```

응답 `{ "updatedCount": 3, "readAt": "..." }`. 0건이어도 200. Bulk JPQL은 `@UpdateTimestamp`를 우회하므로 `updatedAt`을 **명시적으로 함께 갱신**한다.

### 내부 생성 (REST 미노출)

```kotlin
fun create(command: NotificationCreateCommand): Long
```

PR 3의 Event Listener가 호출할 계약이다. **PR 1에서는 Test만 호출한다** — spring-boot.md의 "미래 기능 Placeholder 금지"와 지시서 §21의 "알림 생성 Unit Test 필수"가 충돌하는 지점이며, 사용자 확인을 거쳐 포함하기로 결정했다. PR 본문에 명시한다.

---

## 11. 에러 코드

`domain/notification/exception/NotificationErrorCode.kt` (`ErrorCode` 구현, `BusinessException` 상속 예외):

```kotlin
NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND,  "요청한 알림을 찾을 수 없습니다."),
NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 알림만 접근할 수 있습니다."),
```

지시서 §20의 `DISCORD_*` 10개는 **추가하지 않는다** — 이번 PR에 처리하는 오류가 없다(spring-boot.md "새 Error Code는 실제로 처리하는 오류에만 추가한다").

인증 없음 401 / 잘못된 Enum 400은 각각 `SecurityConfig`의 `AuthenticationEntryPoint`와 `GlobalExceptionHandler`가 이미 처리한다.

---

## 12. 테스트 계획

### `src/test` — Docker 없이 통과해야 함

```text
service/impl/NotificationServiceImplTest.kt      Mock Repository·Port 기반
  알림 생성 / 목록(필터 조합·정렬) / unread count / 단일 읽음 / 이미 읽은 알림 멱등
  / 타인 알림 403 / 없는 알림 404 / 전체 읽음(0건 포함)
service/NotificationTargetResolverTest.kt
  DELETED / NOT_VISIBLE(DRAFT) / 정상(PUBLISHED·CLOSED) / targetId null
  / 미등록 targetType / 도메인당 Port 호출 1회(배치) 검증
controller/NotificationControllerTest.kt         @WebMvcTest + MockMvc
  4개 Endpoint 성공 / 인증 없음 401 / 잘못된 type Enum 400 / size 상한
  / 403·404 오류 Contract(필드명·HTTP Status·ErrorCode·내부 정보 미노출)
OpenApiDocumentationTest                          기존 Test가 새 Controller를 자동 검사
ModularityTest / PackageArchitectureTest          기존 Test로 순환 의존·배치 규칙 검증
```

### `src/integrationTest` — Docker 필요

```text
persistence/NotificationRepositoryIntegrationTest.kt   신규
  V16 포함 Flyway 전체 실행 / ddl-auto=validate로 Entity Mapping 일치
  / 필터 조합 Query / bulk read-all의 실제 갱신 건수와 updated_at
persistence/CoreDomainSchemaIntegrationTest.kt         수정 (필수)
  (a) Notification(type = "JOB_APPLICATION_APPROVED") → NotificationType Enum 값으로 교체
      ※ 현재 값은 §3의 15개 목록에 없다 → JOB_APPLICATION_STATUS_CHANGED
  (b) 다형 참조 FK 부재 검사의 'resource_id' → 'target_id'
  (c) 관련 주석
```

### 검증 명령 (Docker Desktop 실행 후)

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean test build
.\gradlew.bat integrationTest
```

`check`(= `clean test build`에 포함)가 `spotlessCheck`·`detekt`·`koverVerify`를 함께 돌린다.

---

## 13. DECISION_REQUIRED

구현을 막지는 않지만 확정되지 않아 가정으로 진행하는 항목이다. PR 본문에 그대로 옮긴다.

| # | 항목 | 이번 PR의 처리 | 지시서 |
| --- | --- | --- | --- |
| 1 | `NotificationType` 전체 목록 | §3의 15개를 그대로 채택. 값 추가는 하위호환 | §25.7 |
| 2 | `deepLink` 경로 규약 (web/app, 절대/상대) | 상대 경로 `/jobs/{id}`·`/programs/{id}`. `NotificationDeepLink` 한 곳에 집약 | (미기재) |
| 3 | `FORBIDDEN` 판정 근거 | Enum에만 정의하고 사용하지 않음. "MOU Job 접근 제한"에 대응하는 규칙이 코드에 없다 | §17 |
| 4 | 어떤 이벤트를 누구에게 인앱 알림으로 보낼지 | PR 3 범위. PR 1은 저장·조회 계약만 확정 | §25.8, §25.9 |
| 5 | Program API의 `discordDelivery` 값 집합 교체 | PR 2에서 Breaking Change로 진행 예정 | §7, PR #81 리뷰 |
| 6 | 알림 보관·정리 정책 (`deleted_at` 활용 여부) | Column을 유지만 하고 쓰지 않음 | (미기재) |

---

## 14. 후속 PR 방향

- **PR 2 — Discord Delivery 기반.** `discord_deliveries`/`discord_delivery_attempts`, `DiscordDeliveryStatus(PENDING/PROCESSING/DELIVERED/FAILED)` 등 4개 Enum을 `domain.notification`이 소유, `DiscordBotClient` Port + `FakeDiscordBotClient`(Profile 분리), Worker(`SELECT ... FOR UPDATE SKIP LOCKED`), 자동·수동 재시도, 상태 조회 API, 허용 채널 검증, `DISCORD_*` 환경변수. **동시에 기존 자산을 정리해야 한다** — `domain.program.entity.type.DiscordDeliveryStatus`(API 노출 중, Breaking Change)와 `domain.collector...JobNotificationDeliveryStatus`(`SENDING`/`SENT`). 후자는 수집 공고용 **Webhook** 발송이라 Bot 기반 신규 구조와 전달 방식 자체가 달라, 통합할지 그대로 둘지 별도 결정이 필요하다.
- **PR 3 — 도메인 Event 연결.** `JobChangedEvent` 외에 Program·Inquiry·Application Event 신설(`@NamedInterface`, 최소 Snapshot), `@TransactionalEventListener(AFTER_COMMIT)` + 전용 `TaskExecutor`(`JobIndexSyncEventListener` 패턴), `idempotency_key` Column + Unique(V17), 나머지 `targetType` Resolver.
- **PR 4 — 실제 Discord Bot 연동.** `HttpDiscordBotClient`, 내부 API Key, Idempotency, Timeout.
- **PR 5(가칭) — 모바일 Push.** `PUSH_PLATFORM`/Device/FCM/APNs. Issue 범위 확정 전에는 착수하지 않는다.
