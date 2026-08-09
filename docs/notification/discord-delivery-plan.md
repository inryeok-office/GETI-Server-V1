# Discord Delivery 구현 명세 (Notification 후속 PR A+C)

[`notification_after_development.md`](./notification_after_development.md) 요구사항 문서를, **실제 GETI-Bot-V1 계약(`develop` 실측)** 과 **현재 저장소 상태(V18 시점)** 에 맞춰 확정한 구현 명세다.

요구사항 문서 §61이 "작업 전 반드시 확인"하라고 남긴 20개 항목은 모두 아래 §1에서 코드로 확인했다. §4가 "실제 Bot Phase 1~4 완료 후 Request/Response Schema를 반드시 확인하고 그 계약과 1:1로 맞춘다. 추측 DTO를 만들지 않는다"고 요구한 계약은 §2에 실측 결과를 그대로 옮겼다.

이 문서는 요구사항 문서 §60의 **PR A(Persistence + Worker)와 PR C(실제 HTTP 연동)를 하나로 합친 범위**만 다룬다. PR B(Domain Event 연결)와 PR D는 §16에 방향만 적는다.

---

## 1. 작업 전 확인 결과 (요구사항 §61)

| # | 확인 항목 | 실제 상태 |
| --- | --- | --- |
| 1 | Notification Core 최신 상태 | **구현 완료**. Entity·Repository·Service·Controller·DTO·Exception·`NotificationTargetResolver`·`NotificationDeepLink` 존재. Discord 관련 코드는 **전무** |
| 2 | 최신 Migration 번호 | **V18**(`V18__restructure_inquiries_for_multi_answer.sql`) → 이번 PR은 **V19** |
| 3 | Notification Entity/Repository 변경 여부 | V16에서 `target_type`/`target_id` RENAME + `updated_at` 추가 완료. 이번 PR은 `notifications`를 **건드리지 않는다** |
| 4 | Program DiscordDeliveryStatus 존재 여부 | **존재**. `domain.program.entity.type.DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED)`가 `DiscordDeliveryResult` DTO를 통해 `ProgramCreateResponse`/`ProgramUpdateResponse`/`ProgramStatusUpdateResponse`에 **외부 노출 중**. 항상 `SKIPPED` 고정 |
| 5 | Collector Webhook 구조 | `domain.collector.notification.*`에 Webhook 발송·재시도 Scheduler·재기동 복구 Runner가 **이미 동작 중**. `JobNotificationDeliveryStatus(PENDING/SENDING/SENT/FAILED)`. 요구사항 §34대로 **이번 범위에서 건드리지 않는다** |
| 6 | JobChangedEvent Listener 목록 | 구독자는 `domain.search.event.JobIndexSyncEventListener` **하나**. 발행처는 `JobServiceImpl` 3곳 + `CollectedJobUpsertUseCaseImpl` 1곳. `jobId`만 담는 최소 계약 |
| 7 | Program Event 존재 여부 | **없음**. Program은 어떤 Domain Event도 발행하지 않는다 |
| 8 | Inquiry 구현/이벤트 상태 | `InquiryCreatedEvent`(발행 중, **구독자 없음**), `InquiryAnsweredEvent`(`domain.notification`이 구독). `InquiryDiscordDeliveryStatus(PENDING만)` + `InquiryDiscordDeliveryQueryPort`가 항상 `PENDING` 반환 |
| 9~15 | GETI-Bot-V1 Internal API / DTO / ErrorCode / Idempotency / Template / Mention / Header | **§2에 실측 결과 기록**. Bot PR #5·#7·#9·#11이 `develop`에 Merge되어 계약이 확정됨(`main`은 초기 세팅 상태라 참조하면 안 된다) |
| 16 | Server configuration convention | `app.{domain}.{feature}.*` + `${ENV_NAME:기본값}`. `@ConfigurationProperties` data class. Secret은 기본값 없음(빈 문자열) + `isConfigured()` 게이트 |
| 17 | 기존 HTTP Client 사용 여부 | **`RestClient`** 확립. `RestClient.Builder`를 생성자로 주입받아 `baseUrl`을 각자 설정(`DgOAuthProviderClient`, `DiscordWebhookClient`, Collector Provider 4종) |
| 18 | Scheduler convention | 평범한 `@Scheduled(fixedDelayString = "\${...}")`. **ShedLock 미도입**. Collector는 `ApplicationRunner`로 재기동 복구 |
| 19 | TransactionalEventListener pattern | `@TransactionalEventListener(phase = AFTER_COMMIT)` + 도메인 전용 `TaskExecutor`(`SearchTaskExecutorConfig`). `runCatching`으로 예외를 삼키고 로그만 남김 |
| 20 | ModularityTest dependency graph | 현재 `notification → job.query`, `notification → program.query`, `notification → inquiry.event` **단방향**. 역방향(`job/program/inquiry → notification`)을 추가하면 즉시 순환 |

### 1.1 동시성 제어 실측

- Collector Delivery 재시도(`JobNotificationServiceImpl.processDueRetries`)는 **Lock이 전혀 없다**. `findDueForRetry` 후 순차 처리하는 단일 인스턴스 전제 구현이다.
- 저장소 전체에서 `SELECT ... FOR UPDATE SKIP LOCKED`는 **사용처가 없다**. `@Lock(PESSIMISTIC_WRITE)`는 3곳(`StoredFileRepository`, `InquiryRepository`, `ProgramRepository`)에서 짧은 DB 작업에만 쓰인다.
- Bulk `@Modifying` JPQL 전례는 `NotificationRepository.markAllAsRead`.

---

## 2. GETI-Bot-V1 실제 계약 (실측, `develop` 기준)

요구사항 §4가 Source of Truth로 지정한 내용이다. **추측이 아니라 Bot 저장소 코드에서 그대로 옮겼다.**

### 2.1 Endpoint

```text
POST  /internal/v1/discord/messages               → 201 { "messageId": "...", "requestId": "..." }
PATCH /internal/v1/discord/messages/{messageId}   → 200 { "messageId": "...", "requestId": "..." }
```

`/close`, `/delete-notice` 같은 별도 Endpoint는 **없다**(요구사항 §4와 일치).

### 2.2 Header

| Header | POST | PATCH | 비고 |
| --- | --- | --- | --- |
| `X-Internal-Api-Key` | **필수** | **필수** | 불일치 시 `401 UNAUTHORIZED`. Route Plugin `preHandler`에서 검증 |
| `X-Idempotency-Key` | **필수** (1~200자) | **사용 안 함** | `createHeadersSchema`가 POST에만 적용. PATCH 요청에 넣어도 무시된다 |
| `X-Request-Id` | 선택 | 선택 | 없으면 Bot이 `randomUUID()` 생성. 응답 `requestId`로 되돌아온다 |

### 2.3 Request Body

두 Schema 모두 **`.strict()`** 다 — 선언되지 않은 필드가 하나라도 있으면 `400 INVALID_REQUEST`.

```jsonc
// POST (createBodySchema)
{
  "targetType": "JOB" | "PROGRAM" | "INQUIRY",
  "action": "CREATE",                   // literal
  "template": "<9개 중 하나>",
  "channelId": "string",                // trim, 1~64자
  "roleIds": ["string"],                // 최대 10개, 기본 []
  "data": { }                           // template별 strict schema
}

// PATCH (patchBodySchema)
{
  "targetType": "JOB" | "PROGRAM" | "INQUIRY",
  "action": "UPDATE" | "CLOSE_NOTICE" | "DELETE_NOTICE",
  "template": "<9개 중 하나>",
  "channelId": "string",
  "data": { }
}
```

**PATCH Body에는 `roleIds` 필드 자체가 없다.** `.strict()`이므로 빈 배열이라도 보내면 `400`이다.

### 2.4 targetType × action × template 정합성

Bot이 `refineTemplateConsistency`로 **세 값의 조합을 강제 검증**한다. 서버도 같은 표를 코드로 고정해 불일치 요청을 애초에 만들지 않는다.

| template | targetType | action |
| --- | --- | --- |
| `JOB_PUBLISHED` | JOB | CREATE |
| `JOB_UPDATED` | JOB | UPDATE |
| `JOB_CLOSED` | JOB | CLOSE_NOTICE |
| `JOB_DELETED` | JOB | DELETE_NOTICE |
| `PROGRAM_PUBLISHED` | PROGRAM | CREATE |
| `PROGRAM_UPDATED` | PROGRAM | UPDATE |
| `PROGRAM_CLOSED` | PROGRAM | CLOSE_NOTICE |
| `PROGRAM_DELETED` | PROGRAM | DELETE_NOTICE |
| `INQUIRY_CREATED` | INQUIRY | CREATE |

### 2.5 `data` Schema (template별, 전부 `.strict()`)

모든 문자열은 `trim` 후 검증되며 **최소 길이 1**이다 → 빈 문자열을 보내면 `400`. 값이 없으면 **필드를 아예 생략**해야 한다.

| template | 필수 | 선택 (최대 길이) |
| --- | --- | --- |
| `JOB_PUBLISHED` | `jobId`(≤64), `title`(≤200) | `companyName`(≤100), `location`(≤100), `employmentType`(≤50), `deadline`(≤50), `url`(≤500, **유효 URL**) |
| `JOB_UPDATED` | `jobId`, `title` | `changes`(≤300), `url` |
| `JOB_CLOSED` | `jobId`, `title` | `reason`(≤300), `url` |
| `JOB_DELETED` | `jobId`, `title` | `reason`(≤300) |
| `PROGRAM_PUBLISHED` | `programId`(≤64), `title`(≤200) | `description`(≤500), `startAt`(≤50), `endAt`(≤50), `url` |
| `PROGRAM_UPDATED` | `programId`, `title` | `changes`(≤300), `url` |
| `PROGRAM_CLOSED` | `programId`, `title` | `reason`(≤300), `url` |
| `PROGRAM_DELETED` | `programId`, `title` | `reason`(≤300) |
| `INQUIRY_CREATED` | `inquiryId`(≤64) | `category`(≤50), `requesterName`(≤100), `submittedAt`(≤50) |

`INQUIRY_CREATED`에 이메일·전화번호·문의 본문 필드는 **존재하지 않는다** — `.strict()`가 요구사항 §40(개인정보 최소화)을 Bot 쪽에서 이미 강제한다.

### 2.6 Error Contract

```jsonc
{ "code": "...", "message": "...", "retryable": true|false, "requestId": "..." }
```

| code | HTTP | 기본 `retryable` |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | false |
| `UNAUTHORIZED` | 401 | false |
| `MISSING_PERMISSION` | 403 | false |
| `CHANNEL_NOT_FOUND` | 404 | false |
| `MESSAGE_NOT_FOUND` | 404 | false |
| `RATE_LIMITED` | 429 | **true** |
| `DISCORD_UNAVAILABLE` | 503 | **true** |
| `DISCORD_API_ERROR` | 502 | false |
| `INTERNAL_ERROR` | 500 | false |

Bot은 자체적으로 **10초 Command Timeout**을 걸고, 초과 시 `DISCORD_UNAVAILABLE`(`retryable=true`)로 응답한다. Route Handler 도달 전 오류(잘못된 JSON, Body 크기 초과 등)는 Fastify가 판단한 Status(예: 413)에 `INVALID_REQUEST` Body를 실어 보낸다 → **Status와 code가 표와 어긋날 수 있으므로 서버는 Body의 `code`를 우선한다.**

### 2.7 Bot의 Idempotency와 Mention

- `X-Idempotency-Key`가 같은 **CREATE**는 Discord 재전송 없이 기존 결과를 재사용한다. **InMemory**라 Bot 재시작 시 사라진다(요구사항 §38 → 서버 DB가 Source of Truth).
- PATCH는 Idempotency 대상이 아니다.
- Mention: CREATE만 `roleIds`로 `<@&id>` content를 만든다. PATCH는 `content: undefined`, `allowedMentions.roles: []` 고정. `allowedMentions.parse`는 항상 `[]`라 `@everyone`/`@here`가 차단된다.

---

## 3. 요구사항 문서 ↔ 실제 계약 불일치

요구사항 문서가 Bot 구현 **이전**에 작성되어 생긴 차이다. 요구사항 §4·§61("현재 문서보다 실제 코드가 최신 Source of Truth")에 따라 **전부 실제 계약을 따른다.**

| # | 요구사항 문서 | 실제 계약 | 이번 PR의 처리 |
| --- | --- | --- | --- |
| 1 | §3·§8 `mentionRoleIds` | `roleIds` | HTTP 필드명은 `roleIds`. 서버 내부 이름도 `roleIds`로 통일 |
| 2 | §24 "CREATE 성공 이후 PATCH에는 `mentionRoleIds`를 **비워서** 전달" | PATCH Body에 `roleIds` 필드가 **없음**(`.strict()`) | **빈 배열조차 보내지 않는다.** 보내면 `400 INVALID_REQUEST` → 즉시 FAILED |
| 3 | §5 3개 Header를 공통 사용 | `X-Idempotency-Key`는 **POST 전용** | CREATE에만 부착. PATCH에는 `X-Internal-Api-Key`·`X-Request-Id`만 |
| 4 | §46 "`targetId`를 String으로 직렬화" | top-level에 `targetId` 필드 **없음**. `data.jobId`/`programId`/`inquiryId`가 String | Entity의 `targetId`는 `Long`으로 두고, **HTTP 경계에서만** `data.*Id`에 String으로 넣는다 |
| 5 | §20 `discord:{deliveryId}:{action}:{revision}` | (Bot은 포맷을 검증하지 않음, 1~200자) | §49의 Row 중복 방지와 양립 불가 → **§6.3에서 재설계** |
| 6 | §16 "예상 ErrorCode" 9개 | 실제 9개와 **완전 일치** | 그대로 사용 |
| 7 | §11 Template 9개 | 실제 9개와 **완전 일치** | 그대로 사용 |

### 3.1 길이 제약 — 반드시 처리해야 하는 위험

`Job.title`과 `Program.title`은 DB에서 **`length = 500`** 인데 Bot의 `titleSchema`는 **최대 200자**다. 자르지 않고 보내면 `400 INVALID_REQUEST`(`retryable=false`) → **자동 재시도 없이 즉시 FAILED**가 되어 알림이 영구히 누락된다.

따라서 Payload를 만들 때 **모든 문자열 필드를 §2.5의 상한으로 자른다.** 자를 때는 말줄임(`…`)을 붙여 잘렸음을 드러내되, 말줄임 포함 길이가 상한을 넘지 않게 한다. 동시에 **`trim` 후 빈 문자열이 되는 선택 필드는 `null`로 만들어 JSON에서 생략**한다(`min(1)` 위반 방지).

---

## 4. 확정된 결정

| # | 항목 | 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | PR 범위 | **PR A + C 통합**(Persistence·Worker·Retry·Port·Fake·Http·Contract Test) | 사용자 확정. A만 하면 Port 계약을 검증할 실물이 없어 C에서 재설계하게 된다 |
| 2 | REST API | **없음**. 내부 Service 계약만 | 사용자 확정. Event(PR B)가 없어 운영 중 Row가 생기지 않으므로 상태 조회 API는 영원히 빈 결과, 수동 Retry API는 검증 불가 |
| 3 | Payload 해석 | **Job·Program·Inquiry 공개 Query Port를 이번 PR에 포함** | 사용자 확정. 요구사항 §19가 "재시도 시 최신 원본 재조회"를 요구하므로 Port 없이는 Worker가 껍데기 |
| 4 | Worker Claim | **조건부 Atomic UPDATE**(`WHERE id=:id AND status=PENDING`, 영향 행 1일 때만 처리) | 사용자 확정. 새 의존성 없이 다중 인스턴스 안전. HTTP 호출은 Lock 밖 |
| 5 | 수동 Retry 시 자동 카운터 | **초기화한다**(`automaticRetryCount=0`, `nextRetryAt=null`). `manualRetryCount`는 절대 초기화하지 않음 | 사용자 확정. 전체 이력은 `discord_delivery_attempts`에 보존되므로 감사 정보 손실 없음 |
| 6 | `idempotencyKey` | **Action별 분기**(§6.3) | 사용자 확정. §20의 `deliveryId` 기반 포맷은 §49의 Row 중복 방지와 양립 불가 |
| 7 | Stale PROCESSING 복구 | **시각 기반**(`processing_started_at` + 임계값). `ApplicationRunner` 미사용 | 사용자 확정. 다중 인스턴스에서 타 인스턴스의 진행 중 Row를 침범하지 않음 |
| 8 | 운영 기본값 | read **12초** / connect **2초** / Sweep **60초** / 미설정 시 PENDING 축적 | 사용자 확정. read timeout을 Bot의 10초보다 길게 잡아, Bot이 판단한 `retryable`을 받아 쓰고 애매한 클라이언트 Timeout 구간을 없앤다 |
| 9 | Discord 상태 API의 Controller 위치 | (PR B) URL은 명세대로 `/api/v1/admin/{jobs\|programs}/...`, **Controller는 `domain.notification`에 배치** | `JobAdminController`에 두면 `job → notification` 역방향 의존이 생겨 `ModularityTest` 순환 실패 |

---

## 5. 범위

### 이번 PR에 포함

```text
discord_deliveries / discord_delivery_attempts Table (V19)
DiscordDeliveryStatus / TargetType / Action / Template / AttemptType / AttemptResult Enum
DiscordDelivery / DiscordDeliveryAttempt Entity + Repository
DiscordDeliveryService (내부 계약: enqueue / retryManually)
DiscordDeliveryWorker (@Scheduled Sweep + Claim + Stale 회수)
자동 Retry (3회, 1분/5분/30분) / 수동 Retry (3회, FAILED에서만)
DiscordBotClient Port + DiscordBotCommand/Result 계약
FakeDiscordBotClient (Test 전용)
HttpDiscordBotClient (RestClient, Header, Timeout, Error 매핑)
DiscordBotProperties (app.discord.bot.*)
DiscordPayloadFactory (template별 data 생성 + 길이 절단)
Job/Program/Inquiry Discord Payload Query Port (@NamedInterface, 순수 추가)
Contract Test (MockRestServiceServer 기반)
```

### 이번 PR에서 제외 (PR 본문에 사유와 함께 명시)

```text
Job/Program/Inquiry Domain Event 발행·구독                     → PR B
Program의 DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED) 교체    → PR B (Breaking Change)
Program.discordChannelId / discordMessageId Column 정리         → PR B
InquiryDiscordDeliveryQueryPortImpl의 PENDING 고정 해소          → PR B
Discord 상태 조회 API / 수동 Retry REST API (요구사항 §36·§37)   → PR B
허용 채널 4개 정책과 targetGrades → Role ID 매핑 (§25·§10)       → PR B (채널 목록 미확정)
Collector Discord Webhook 이관 (§34)                            → 별도 Issue
Resilience4j / Redis / Kafka (§63)                              → 도입하지 않음
```

---

## 6. 데이터 설계

### 6.1 Enum (`domain/notification/entity/type/`)

```kotlin
enum class DiscordDeliveryStatus { PENDING, PROCESSING, DELIVERED, FAILED }

enum class DiscordDeliveryTargetType { JOB, PROGRAM, INQUIRY }

enum class DiscordDeliveryAction { CREATE, UPDATE, CLOSE_NOTICE, DELETE_NOTICE }

enum class DiscordMessageTemplate(
    val targetType: DiscordDeliveryTargetType,
    val action: DiscordDeliveryAction,
) { /* §2.4 표 9개 */ }

enum class DiscordDeliveryAttemptType { AUTOMATIC, MANUAL }

enum class DiscordDeliveryAttemptResult { SUCCESS, FAILURE }
```

- `DiscordMessageTemplate`이 `targetType`/`action`을 **자기 안에 들고 있어** Bot의 `TEMPLATE_INFO` 검증과 어긋난 조합을 만들 수 없다. Delivery 생성 시 세 값의 정합성을 Entity가 강제한다.
- Enum 이름과 **직렬화 문자열이 Bot과 정확히 일치**한다(요구사항 §11).
- Collector의 `JobNotificationDeliveryStatus`를 **절대 import하지 않는다**(요구사항 §35).

### 6.2 `DiscordDelivery` Entity

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | `Long?` | IDENTITY |
| `targetType` | `DiscordDeliveryTargetType` | VARCHAR(20) |
| `targetId` | `Long` | 내부는 Long, HTTP 경계에서만 String |
| `action` | `DiscordDeliveryAction` | VARCHAR(20) |
| `template` | `DiscordMessageTemplate` | VARCHAR(30) |
| `status` | `DiscordDeliveryStatus` | VARCHAR(20), 초기 `PENDING` |
| `channelId` | `String` | VARCHAR(64) — Bot `idSchema` 상한과 일치 |
| `roleIds` | `String?` | CSV. CREATE에만 의미. 최대 10개(§2.3) |
| `discordMessageId` | `String?` | VARCHAR(64). CREATE 성공 시 저장 |
| `idempotencyKey` | `String` | VARCHAR(200) **UNIQUE** — Bot 상한과 일치 |
| `automaticRetryCount` | `Int` | 기본 0 |
| `manualRetryCount` | `Int` | 기본 0 |
| `manualRetryPending` | `Boolean` | 다음 시도가 수동 재시도인지. 첫 시도가 끝나면 해제 |
| `nextRetryAt` | `LocalDateTime?` | |
| `processingStartedAt` | `LocalDateTime?` | **Stale 판정용**(결정 7) |
| `lastAttemptAt` | `LocalDateTime?` | |
| `deliveredAt` | `LocalDateTime?` | |
| `lastErrorCode` | `String?` | VARCHAR(50) |
| `lastErrorMessage` | `String?` | VARCHAR(1000) |
| `createdAt` / `updatedAt` | `LocalDateTime` | `@CreationTimestamp` / `@UpdateTimestamp` |

**Payload Snapshot을 저장하지 않는다**(요구사항 §19·§39). `targetType`+`targetId`로 전송 직전에 최신 데이터를 재조회한다.

`roleIds`를 CSV `String`으로 두는 이유: 최대 10개의 Snowflake만 담고 검색 대상이 아니라 별도 Table이나 `jsonb`가 과하다. Job의 `required_skills`가 `jsonb`인 것과 달리 조회 조건으로 쓰이지 않는다.

### 6.3 `idempotencyKey` 설계 (결정 6)

요구사항 §20의 `discord:{deliveryId}:{action}:{revision}`은 **쓸 수 없다.** §49는 같은 키의 UNIQUE 제약으로 **Row 생성 자체를 막으라**고 하는데, `deliveryId`는 Row를 만들어야 생기기 때문이다(닭-달걀).

Bot 쪽 실측상 `X-Idempotency-Key`는 **CREATE에서만** 쓰이고 InMemory라 신뢰할 수 없으므로, 이 키의 실질 역할은 **서버 DB의 중복 방지**다. 따라서 Event에서 유도한다.

```text
CREATE / CLOSE_NOTICE / DELETE_NOTICE   {targetType}:{targetId}:{action}
UPDATE                                  {targetType}:{targetId}:UPDATE:{원본 updatedAt epochMilli}
```

예: `PROGRAM:123:CREATE`, `PROGRAM:123:UPDATE:1786000000000`, `JOB:45:DELETE_NOTICE`

- CREATE/CLOSE_NOTICE/DELETE_NOTICE는 **대상당 영구히 최대 1건**이다. 요구사항 §23("삭제된 메시지를 자동 재생성 금지")과 §49를 DB 제약으로 강제한다.
- UPDATE만 원본 `updatedAt`으로 구분한다. 같은 Event가 중복 도착하면 `updatedAt`이 같아 자동 dedup되고, 실제 재수정이면 새 Row가 생긴다. **별도 카운터 조회가 없어 경합이 발생하지 않는다.**
- 재시도해도 Row가 그대로이므로 키가 바뀌지 않는다(요구사항 §20 핵심 요구 충족).
- Enqueue는 UNIQUE 위반을 **정상 흐름**으로 처리한다 — `DataIntegrityViolationException`의 제약조건 이름을 확인해 `uk_discord_deliveries_idempotency_key`면 조용히 건너뛴다. 다른 원인의 위반을 "이미 존재함"으로 오판하지 않도록 제약명을 확인하는 것은 `JobNotificationServiceImpl.createDeliveryOrNull`과 같은 방식이다.

### 6.4 `DiscordDeliveryAttempt` Entity

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | `Long?` | |
| `discordDeliveryId` | `Long` | FK. **연관 객체가 아닌 ID 참조** |
| `attemptType` | `DiscordDeliveryAttemptType` | AUTOMATIC / MANUAL. Delivery의 `manualRetryPending`으로 판별한다 |
| `attemptNumber` | `Int` | 해당 Delivery의 누적 시도 순번 |
| `requestId` | `String` | VARCHAR(64). `X-Request-Id`로 보낸 값 |
| `startedAt` / `finishedAt` | `LocalDateTime` | |
| `result` | `DiscordDeliveryAttemptResult` | |
| `errorCode` | `String?` | VARCHAR(50) |
| `retryable` | `Boolean?` | Bot 응답의 `retryable` |
| `createdAt` | `LocalDateTime` | |

요구사항 §12에 따라 **저장하지 않는 것**: Internal API Key, Authorization Header, 전체 Request Payload, 문의 본문, Discord Token, Stack Trace. `errorMessage`도 Attempt에는 남기지 않는다 — 최신 실패 사유는 Delivery의 `lastErrorMessage` 한 곳으로 충분하고, 시도마다 복제하면 개인정보가 섞여 들어갈 표면만 넓어진다.

### 6.5 V19 Migration

기존 Migration은 **수정하지 않는다**. Database Enum 대신 `VARCHAR` + Enum mapping(요구사항 §13, 저장소 관례).

```sql
CREATE TABLE discord_deliveries (
    id                    BIGSERIAL PRIMARY KEY,
    target_type           VARCHAR(20)  NOT NULL,
    target_id             BIGINT       NOT NULL,
    action                VARCHAR(20)  NOT NULL,
    template              VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    channel_id            VARCHAR(64)  NOT NULL,
    role_ids              VARCHAR(700),
    discord_message_id    VARCHAR(64),
    idempotency_key       VARCHAR(200) NOT NULL,
    automatic_retry_count INTEGER      NOT NULL DEFAULT 0,
    manual_retry_count    INTEGER      NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMP,
    processing_started_at TIMESTAMP,
    last_attempt_at       TIMESTAMP,
    delivered_at          TIMESTAMP,
    last_error_code       VARCHAR(50),
    last_error_message    VARCHAR(1000),
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT uk_discord_deliveries_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_discord_deliveries_status_next_retry ON discord_deliveries (status, next_retry_at);
CREATE INDEX idx_discord_deliveries_target           ON discord_deliveries (target_type, target_id);

CREATE TABLE discord_delivery_attempts (
    id                  BIGSERIAL PRIMARY KEY,
    discord_delivery_id BIGINT      NOT NULL REFERENCES discord_deliveries (id),
    attempt_type        VARCHAR(20) NOT NULL,
    attempt_number      INTEGER     NOT NULL,
    request_id          VARCHAR(64) NOT NULL,
    started_at          TIMESTAMP   NOT NULL,
    finished_at         TIMESTAMP   NOT NULL,
    result              VARCHAR(20) NOT NULL,
    error_code          VARCHAR(50),
    retryable           BOOLEAN,
    created_at          TIMESTAMP   NOT NULL
);

CREATE INDEX idx_discord_delivery_attempts_delivery_created
    ON discord_delivery_attempts (discord_delivery_id, created_at);
```

Index는 요구사항 §13이 지정한 4종과 정확히 대응한다(`status+nextRetryAt`, `targetType+targetId`, `idempotencyKey` UNIQUE, `discordDeliveryId+createdAt`).

`role_ids VARCHAR(700)`: Snowflake 최대 64자 × 10개 + 구분자 9개 = 649자에 여유를 둔 값이다.

---

## 7. Port 계약

### 7.1 `DiscordBotClient` (Notification 소유, Outbound)

```kotlin
// domain/notification/service/DiscordBotClient.kt
interface DiscordBotClient {
    fun create(command: DiscordCreateCommand): DiscordBotResult
    fun update(command: DiscordPatchCommand): DiscordBotResult
}

data class DiscordCreateCommand(
    val targetType: DiscordDeliveryTargetType,
    val template: DiscordMessageTemplate,
    val channelId: String,
    val roleIds: List<String>,
    val data: Map<String, String>,
    val requestId: String,
    val idempotencyKey: String,
)

data class DiscordPatchCommand(
    val targetType: DiscordDeliveryTargetType,
    val action: DiscordDeliveryAction,   // UPDATE / CLOSE_NOTICE / DELETE_NOTICE
    val template: DiscordMessageTemplate,
    val channelId: String,
    val messageId: String,
    val data: Map<String, String>,
    val requestId: String,
)

sealed interface DiscordBotResult {
    data class Success(val messageId: String) : DiscordBotResult
    data class Failure(
        val code: String,
        val retryable: Boolean,
        val message: String?,
    ) : DiscordBotResult
}
```

- **Port는 Spring HTTP 타입을 노출하지 않는다**(요구사항 §14). `ResponseEntity`, `RestClient.ResponseSpec` 금지.
- `DiscordPatchCommand`에 `roleIds`가 **없다** — §3의 불일치 2를 타입으로 강제해, 실수로도 Mention 필드를 PATCH에 실을 수 없다.
- 실패를 예외가 아닌 `Failure`로 돌려주는 이유: Worker가 `retryable`을 보고 상태를 계산해야 하는데, 예외로 던지면 호출부가 `catch`에서 분기하게 되어 정상 흐름과 뒤섞인다.

### 7.2 도메인 공개 Query Port (결정 3)

방향은 **`notification → {job, program, inquiry}` 단방향**이다(§1 #20). 기존 `JobNotificationTargetQueryPort`와 같은 방식으로 소유 도메인이 `@NamedInterface`로 공개한다.

```kotlin
// domain/job/query/JobDiscordPayloadQueryPort.kt
@NamedInterface
interface JobDiscordPayloadQueryPort {
    fun findById(jobId: Long): JobDiscordPayloadSnapshot?
}

@NamedInterface
data class JobDiscordPayloadSnapshot(
    val jobId: Long,
    val title: String,
    val companyName: String?,
    val location: String?,
    val employmentType: String?,
    val recruitmentEndedAt: LocalDateTime?,
)
```

`ProgramDiscordPayloadQueryPort`(`programId`, `title`, `description`, `startAt`, `endAt`), `InquiryDiscordPayloadQueryPort`(`inquiryId`, `category`, `requesterName`, `createdAt`)도 같은 형태다.

- **Inquiry Snapshot에는 이메일·전화번호·문의 본문을 넣지 않는다**(요구사항 §40). Bot의 `.strict()` schema에 해당 필드가 없어 보낼 수도 없다.
- Soft Delete된 대상도 조회해야 한다(`DELETE_NOTICE`는 삭제된 대상의 제목을 써야 한다) → `findAllById` 계열을 쓰고 `deletedAt`으로 거르지 않는다.
- 대상이 없으면 `null`을 반환하고, Worker는 이를 **재시도 불가 실패**로 처리한다(§8.4).
- 조회 단위는 **단건**이다. Worker가 Delivery를 하나씩 처리하므로 `NotificationTargetResolver`의 배치 조회와 달리 N+1이 발생하지 않는다.

### 7.3 `DiscordPayloadFactory` (Notification 내부)

Snapshot → Bot `data` Map 변환을 한 곳에 모은다.

- §2.5의 **길이 상한으로 절단**하고, 말줄임 포함 길이가 상한을 넘지 않게 한다.
- `trim` 후 빈 값이 되는 선택 필드는 **Map에 넣지 않는다**(`min(1)` 위반 방지).
- 시각은 `ZoneId.systemDefault()`로 `OffsetDateTime`을 만들어 ISO-8601(Offset 포함)로 직렬화한다(요구사항 §47). `DiscordJobEmbedBuilder`·`DiscordCollectionRunEmbedBuilder`가 이미 쓰는 방식이다.
- `url`은 **절대 URL**이어야 하는데(`z.url()`) 저장소에 프론트 Base URL 설정이 없다. 세 schema 모두 `url`이 optional이므로 **이번 PR에서는 보내지 않는다**(§15 DECISION_REQUIRED 1).
- `changes`/`reason`은 값의 출처(무엇이 바뀌었는지, 왜 마감했는지)가 Event에 없으므로 **이번 PR에서 보내지 않는다**. PR B에서 Event가 붙을 때 결정한다.

---

## 8. Worker

### 8.1 구성

```kotlin
@Scheduled(fixedDelayString = "\${app.discord.bot.sweep-interval-ms:60000}")
fun sweep()
```

`DiscordDeliveryWorker`(Scheduler 진입점)와 `DiscordDeliveryService`(처리 로직)를 분리한다 — `JobNotificationRetryScheduler` → `JobNotificationService`와 같은 구조다.

### 8.2 Sweep 절차

```text
0. properties.isConfigured()가 false면 즉시 반환 (Delivery는 PENDING으로 축적)
1. Stale 회수: status=PROCESSING AND processing_started_at < now - staleThreshold
              → status=PENDING, processing_started_at=null  (Bulk UPDATE)
2. 대상 조회: status=PENDING AND (next_retry_at IS NULL OR next_retry_at <= now)
              → ID 목록만, 배치 상한(기본 50)
3. 각 ID에 대해:
   a. Claim: UPDATE ... SET status=PROCESSING, processing_started_at=now
             WHERE id=:id AND status=PENDING       → 영향 행 0이면 건너뜀
   b. 최신 Snapshot 조회 (Query Port)
   c. Payload 생성 (DiscordPayloadFactory)
   d. Bot 호출 (Transaction 밖)
   e. Attempt 저장 + 결과 반영
```

**HTTP 호출은 Transaction 밖에서 수행한다**(`spring-boot.md`: 느리거나 실패할 수 있는 I/O를 `@Transactional` 내부에서 수행 금지). Claim, Attempt 저장, 상태 반영은 각각 짧은 Transaction이다.

### 8.3 성공 처리

- CREATE 성공: `discordMessageId` 저장 → `status=DELIVERED`, `deliveredAt=now`.
  **`messageId`가 비어 있으면 `DELIVERED`로 처리하지 않는다**(요구사항 §21). Contract 오류로 보고 `lastErrorCode=MISSING_MESSAGE_ID`로 **재시도 불가 FAILED**.
- PATCH 성공: `status=DELIVERED`, `deliveredAt=now`. `discordMessageId`는 그대로.

### 8.4 실패 처리

| 상황 | `retryable` | 결과 |
| --- | --- | --- |
| Bot이 `retryable=false` 반환 (`INVALID_REQUEST`, `MESSAGE_NOT_FOUND` 등) | false | **즉시 FAILED**. 요구사항 §23대로 `MESSAGE_NOT_FOUND`에 CREATE fallback 금지 |
| Bot이 `retryable=true` 반환 (`RATE_LIMITED`, `DISCORD_UNAVAILABLE`) | true | 잔여 자동 횟수 있으면 `PENDING` + `nextRetryAt`, 소진이면 `FAILED` |
| 연결 실패 / read timeout / 알 수 없는 응답 | **true로 간주** | 요구사항 §42("Timeout은 재시도 가능 실패로 분류") |
| PATCH인데 `discordMessageId`가 없음 | — | **호출하지 않고** `FAILED`(`lastErrorCode=MISSING_DISCORD_MESSAGE_ID`). 요구사항 §22 |
| Query Port가 대상을 찾지 못함 | — | `FAILED`(`lastErrorCode=TARGET_NOT_FOUND`). 존재하지 않는 대상은 재시도해도 생기지 않는다 |

요구사항 §16대로 **Bot의 `retryable`을 판단 재료로 쓰되 최종 Retry Policy의 Source of Truth는 서버**다. 위 표에서 "연결 실패"를 서버가 독자적으로 `true`로 정하는 것이 그 예다.

### 8.5 자동 Retry 간격 (요구사항 §17)

```text
automaticRetryCount 0 → 1 : nextRetryAt = now + 1분
                   1 → 2 : nextRetryAt = now + 5분
                   2 → 3 : nextRetryAt = now + 30분
                   3     : FAILED (nextRetryAt = null, 카운터는 3에 머문다)
```

`automaticRetryCount`는 **재시도를 예약할 때만** 증가한다. FAILED로 확정할 때 함께 올리면 상한이 3인데 4가 저장되어, 상태 조회에 `3/3`이 아닌 `4/3`으로 노출된다.

Bot이 `429 RATE_LIMITED`에 `Retry-After`를 준다면 그 값을 우선한다(§17). 현재 Bot 구현은 이 Header를 내려주지 않으므로 **없을 때의 기본 동작만 구현하고**, Header가 있으면 쓰도록 파싱만 해 둔다.

### 8.6 수동 Retry (요구사항 §18, 결정 5)

```text
전제: status == FAILED (PENDING/PROCESSING/DELIVERED는 거부)
      manualRetryCount < 3

결과: manualRetryCount += 1
      automaticRetryCount = 0          ← 결정 5
      status = PENDING
      nextRetryAt = null
      새 Delivery Row를 만들지 않는다
```

이번 PR은 이 동작을 `DiscordDeliveryService.retryManually(deliveryId)`로 구현하고 **REST로 노출하지 않는다**(결정 2). 권한 검증은 Endpoint가 생기는 PR B에서 요구사항 §37 명세(등록자/담당 교사, DEVELOPER)에 맞춰 추가한다.

---

## 9. `HttpDiscordBotClient`

`domain/notification/service/impl/HttpDiscordBotClient.kt`. `RestClient.Builder` 주입 — `DiscordWebhookClient`·`DgOAuthProviderClient`와 같은 방식이며 **새 HTTP Library를 추가하지 않는다**(요구사항 §15).

```text
baseUrl              properties.baseUrl
공통 Header          X-Internal-Api-Key, X-Request-Id
CREATE 추가 Header   X-Idempotency-Key                (PATCH에는 붙이지 않는다 — §3 불일치 3)
Timeout              connect 2초 / read 12초           (JdkClientHttpRequestFactory 등 명시 설정)
성공                 201/200 Body의 messageId를 Success로
실패                 Body의 code/retryable/message를 Failure로
Body 파싱 실패        Failure(code="MALFORMED_RESPONSE", retryable=true)
연결 실패/Timeout     Failure(code="BOT_UNREACHABLE", retryable=true)
```

- **HTTP Status가 아니라 Body의 `code`를 우선**한다(§2.6 — Fastify가 Route 도달 전 오류를 413 등으로 낼 수 있다).
- 오류 응답에 `retryable`이 없으면 §2.6 표의 기본값으로 보정하고, 알 수 없는 `code`면 `retryable=false`(보수적)로 둔다.
- **로그에 API Key와 전체 Payload를 남기지 않는다**(요구사항 §41). `deliveryId`, `requestId`, `targetType`, `targetId`, `action`, `code`만 남긴다.

### 9.1 설정 (결정 8)

```yaml
app:
  discord:
    bot:
      enabled: ${DISCORD_BOT_ENABLED:false}
      base-url: ${DISCORD_BOT_BASE_URL:}
      internal-api-key: ${DISCORD_BOT_INTERNAL_API_KEY:}
      connect-timeout-ms: 2000
      read-timeout-ms: 12000
      sweep-interval-ms: 60000
      stale-processing-threshold-ms: 300000
      max-automatic-retry-count: 3
      max-manual-retry-count: 3
      batch-size: 50
```

```kotlin
@ConfigurationProperties(prefix = "app.discord.bot")
data class DiscordBotProperties(...) {
    fun isConfigured(): Boolean = enabled && baseUrl.isNotBlank() && internalApiKey.isNotBlank()
}
```

- 기존 `app.discord.job-notification.*`(Collector Webhook)과 **형제 관계로 나란히 둔다**. 두 Secret은 전혀 다른 값이며 서로 대체할 수 없다.
- **Secret에 안전하지 않은 기본값을 주지 않는다**(`.claude/rules/spring-boot.md`). 빈 문자열 기본값 + `isConfigured()` 게이트는 Collector가 이미 쓰는 검증된 방식이다.
- Bot 쪽 환경변수 이름은 `GETI_INTERNAL_API_KEY`다. **값은 같고 이름은 서비스별로 다르다**(요구사항 §6).
- `.env.example`과 문서에는 **Placeholder만** 남긴다.

---

## 10. 파일 목록

### `domain/notification` (신규 20)

```text
entity/DiscordDelivery.kt
entity/DiscordDeliveryAttempt.kt
entity/type/DiscordDeliveryStatus.kt
entity/type/DiscordDeliveryTargetType.kt
entity/type/DiscordDeliveryAction.kt
entity/type/DiscordMessageTemplate.kt
entity/type/DiscordDeliveryAttemptType.kt
entity/type/DiscordDeliveryAttemptResult.kt
repository/DiscordDeliveryRepository.kt
repository/DiscordDeliveryAttemptRepository.kt
service/DiscordBotClient.kt                     Port + Command/Result
service/DiscordDeliveryService.kt               Interface (enqueue / retryManually / processDue)
service/DiscordPayloadFactory.kt
service/DiscordDeliveryRetryPolicy.kt           간격·상한 계산 단일 지점
service/DiscordIdempotencyKeys.kt                Key 생성 단일 지점
service/impl/DiscordDeliveryServiceImpl.kt
service/impl/HttpDiscordBotClient.kt
scheduler/DiscordDeliveryWorker.kt
config/DiscordBotProperties.kt
config/DiscordBotHttpConfig.kt                   Bot 전용 RestClient Bean
dto/DiscordDeliveryEnqueueCommand.kt             내부 계약 (REST 미노출)
exception/DiscordDeliveryErrorCode.kt
exception/DiscordDeliveryExceptions.kt
```

`DiscordBotHttpConfig`가 `RestClient` 조립을 맡는 이유는 Contract Test 때문이다. `MockRestServiceServer.bindTo(builder)`는 Builder에 Mock Request Factory를 심는 방식이라, Client가 생성자에서 다시 `requestFactory(...)`를 호출하면 그 Mock이 덮여 요청을 가로챌 수 없다. 조립을 Config로 빼면 Test가 Mock을 심은 Builder로 만든 `RestClient`를 그대로 주입할 수 있다.

### `domain/job` · `domain/program` · `domain/inquiry` (각 신규 2, 순수 추가)

```text
job/query/JobDiscordPayloadQueryPort.kt             @NamedInterface + Snapshot
job/service/impl/JobDiscordPayloadQueryPortImpl.kt
program/query/ProgramDiscordPayloadQueryPort.kt
program/service/impl/ProgramDiscordPayloadQueryPortImpl.kt
inquiry/query/InquiryDiscordPayloadQueryPort.kt     query Package 신규
inquiry/service/impl/InquiryDiscordPayloadQueryPortImpl.kt
```

`domain/inquiry`에는 `query` Package가 없으므로 새로 만든다. 기존 `InquiryDiscordDeliveryQueryPort`는 `service` Package에 있고 **공개되지 않은 내부 계약**이라 성격이 다르다 — 이번 것은 `@NamedInterface`로 공개한다.

### 설정·Migration·문서

```text
src/main/resources/application.yaml                                       수정  app.discord.bot.*
src/main/resources/db/migration/V19__create_discord_delivery_tables.sql   신규
docs/architecture/erd.md                                                  수정  Table 2개 추가
docs/notification/discord-delivery-plan.md                                신규  (이 문서)
```

`DiscordBotProperties`는 저장소 관례대로 소비자 쪽 `@EnableConfigurationProperties`(여기서는 `DiscordBotHttpConfig`)로 등록한다 — `@ConfigurationPropertiesScan`은 이 저장소가 쓰지 않는다.

`CoreDomainSchemaIntegrationTest`의 Table 개수 기대값도 34 → **36**으로 함께 갱신한다.

---

## 11. 테스트 계획

### `src/test` — Docker 없이 통과해야 함

```text
service/impl/DiscordDeliveryServiceImplTest.kt      (요구사항 §53)
  CREATE Delivery 생성 / 초기 PENDING / 동일 idempotencyKey 중복 생성 방지
  PROCESSING 전환(Claim 실패 시 건너뜀) / 성공 DELIVERED / messageId 저장
  messageId 없는 CREATE 성공 → FAILED (§21)
  retryable 실패 → PENDING + nextRetryAt / non-retryable → 즉시 FAILED
  자동 retry count 증가 / 자동 최대 3회 소진 → FAILED
  manual retry count / manual 최대 3회 / FAILED 외 상태에서 manual retry 거부
  manual retry 시 automaticRetryCount 초기화 (결정 5)
  stale PROCESSING 회수 / 임계값 미만 PROCESSING은 건드리지 않음
  PATCH인데 messageId 없음 → 호출하지 않고 FAILED (§22)
  Query Port가 null → FAILED

service/DiscordPayloadFactoryTest.kt
  title 200자 절단 / 빈 선택 필드 생략 / 시각 ISO-8601 Offset 포함
  template별 필수 필드 존재 / Bot schema에 없는 필드 미포함

service/DiscordDeliveryRetryPolicyTest.kt
  1분 / 5분 / 30분 / 소진

service/impl/HttpDiscordBotClientTest.kt            (요구사항 §54)
  MockRestServiceServer 사용 — 새 WireMock Dependency를 추가하지 않는다
  POST Endpoint·PATCH Endpoint 경로
  X-Internal-Api-Key / X-Request-Id / X-Idempotency-Key(POST에만, PATCH에는 없음)
  PATCH Body에 roleIds가 없음
  201 성공 파싱 / 200 성공 파싱
  400 / 401 / 404 MESSAGE_NOT_FOUND / 429 / 5xx / malformed response
  Body의 code를 HTTP Status보다 우선

ModularityTest / PackageArchitectureTest            기존 Test가 순환 의존·배치 규칙 검증 (요구사항 §57)
```

`FakeDiscordBotClient`는 `src/test`에 두고 Production Source에 넣지 않는다(요구사항 §44). 테스트에 Discord Bot Token이 필요 없고 CI가 외부 Network에 의존하지 않는다.

### `src/integrationTest` — Docker 필요 (요구사항 §56)

```text
persistence/DiscordDeliveryRepositoryIntegrationTest.kt   신규
  V19 포함 Flyway 전체 실행 / ddl-auto=validate로 Entity Mapping 일치
  idempotency_key UNIQUE 위반 / attempts FK
  Worker 대상 Query(status + next_retry_at)
  동시 Claim: 두 Thread가 같은 Row를 Claim하면 하나만 성공
  status 전이 / retry scheduling / stale 회수 Query
```

실제 Discord·실제 Bot을 호출하지 않는다.

### Architecture Test (요구사항 §57)

`ModularityTest`의 `modules.verify()`가 다음을 자동 검증한다.

```text
job / program / inquiry → notification repository  : 금지 (역방향 의존 없음)
notification → job.query / program.query / inquiry.query : 허용 (@NamedInterface 단방향)
```

`discord.js`를 비롯한 Discord SDK는 **서버 Dependency에 절대 추가하지 않는다**.

### 검증 명령

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean test build
.\gradlew.bat integrationTest
```

---

## 12. 보안 점검 (요구사항 §52)

- Internal API Key는 **Server → Bot 전용**이다. 어떤 API 응답·로그·예외에도 넣지 않는다.
- 브라우저/App이 Bot에 직접 접근하는 구조를 만들지 않는다.
- Bot Base URL이 노출돼도 API Key 없이는 명령이 수행되지 않는다(Bot이 `preHandler`에서 검증).
- Inquiry Payload에 이메일·전화번호·본문·파일 URL을 넣지 않는다. Bot `.strict()` schema가 2차 방어선이다.
- Attempt Log에 Key·Header·전체 Payload를 저장하지 않는다(§6.4).

---

## 13. 요구사항 §62 완료 조건 대비표

| 완료 조건 | 이번 PR | 비고 |
| --- | --- | --- |
| Notification이 Discord Delivery Source of Truth | ✅ | |
| PENDING/PROCESSING/DELIVERED/FAILED 통일 | ✅ | Notification 도메인 한정. Program·Inquiry Enum 교체는 PR B |
| `discord_deliveries` / `discord_delivery_attempts` | ✅ | V19 |
| `DiscordBotClient` Port / `HttpDiscordBotClient` | ✅ | |
| Bot Internal API 인증 / Request ID / Idempotency Key | ✅ | |
| Automatic Retry / Manual Retry | ✅ | 로직만. REST 노출은 PR B |
| Stale PROCESSING recovery | ✅ | 시각 기반 |
| CREATE messageId 저장 / UPDATE·CLOSE·DELETE 기존 Message 수정 | ✅ | |
| Missing Message 자동 재생성 금지 | ✅ | `MESSAGE_NOT_FOUND` → FAILED |
| Mention 최초 성공 CREATE 1회 | ✅ | PATCH Command 타입에 `roleIds` 부재로 강제 |
| Fake Bot Client Test / HTTP Contract Test / Integration Test | ✅ | |
| ModularityTest / Architecture Test | ✅ | |
| Discord 실패 Core Transaction 격리 | ⏸ | Event가 없어 이번 PR에는 원본 Transaction이 없다 → PR B |
| Job / Program / Inquiry Event 연결 | ⏸ | PR B |
| Program 자체 Discord 상태 제거·교체 | ⏸ | PR B (Breaking Change) |
| Swagger/API 문서 갱신 | ⏸ | 이번 PR에 Endpoint가 없다 → PR B |
| Secret 미노출 / CI 전체 통과 | ✅ | |

---

## 14. 주요 가정

1. **`data.url`을 보내지 않는다.** 프론트 Base URL 설정이 저장소에 없고 Bot은 절대 URL만 받는다. 세 schema 모두 optional이라 생략해도 계약 위반이 아니다.
2. **`changes`/`reason`을 보내지 않는다.** 값의 출처가 없다. Event가 붙는 PR B에서 결정한다.
3. **시각은 `ZoneId.systemDefault()` 기준 Offset**으로 직렬화한다. 저장소에 명시적 timezone 설정이 없고 Collector Embed Builder가 이미 같은 방식을 쓴다.
4. **`roleIds`는 Enqueue 호출자가 결정한다.** 허용 채널 4개와 `targetGrades` → Role ID 매핑은 요구사항 §10이 "코드 상수로 임의 지정하지 않는다"고 못박았고 실제 Snowflake가 확정되지 않았다.
5. **Bot의 `Retry-After` Header는 현재 존재하지 않는다.** 파싱 코드만 두고 기본 백오프로 동작한다.

---

## 15. DECISION_REQUIRED

구현을 막지는 않지만 확정되지 않아 가정으로 진행하는 항목이다. PR 본문에 그대로 옮긴다.

| # | 항목 | 이번 PR의 처리 | 근거 문서 |
| --- | --- | --- | --- |
| 1 | 프론트 Base URL (Discord `data.url`) | 보내지 않음. 확정 시 `DiscordPayloadFactory` 한 곳만 수정 | 요구사항 §46, notification-core-plan §13 |
| 2 | 허용 Discord 채널 4개(guildId/channelId/이름/용도) | 이번 PR 범위 밖. Enqueue 호출자가 `channelId`를 준다 | 요구사항 §25, Notification 요구사항 §10 |
| 3 | `targetGrades` → Discord Role ID 매핑 | 동일 | Notification 요구사항 §9 |
| 4 | 문의 전용 Guild·Channel | 동일 | Notification 요구사항 §9 |
| 5 | `Job.discordChannelKey`(논리 Key) vs `Program.discordChannelId`(원시 Snowflake) 불일치 | 이번 PR은 어느 쪽도 읽지 않는다. PR B에서 통일 방향 결정 | 실측 |
| 6 | Program API `discordDelivery` 값 집합 교체 시점 | PR B에서 Breaking Change로 진행 | 요구사항 §32·§58 |
| 7 | `InquiryDiscordDeliveryQueryPortImpl`의 `PENDING` 고정 해소 | 이번 PR에서 손대지 않는다. `inquiry → notification` 역방향 의존이 필요해 순환이 생기므로 별도 설계 필요 | 실측, 해당 Port KDoc |

---

## 16. 후속 PR 방향

- **PR B — Domain Event 연결.** `ProgramPublishedEvent` 등 Program Event 신설, `JobChangedEvent`가 `jobId`만 담아 CREATE/UPDATE/CLOSE/DELETE를 구분할 수 없는 문제 해결(기존 `search` Listener 계약을 깨지 않도록 **신규 Event 추가** 방향, 요구사항 §50), `InquiryCreatedEvent` 구독, `@TransactionalEventListener(AFTER_COMMIT)` + 전용 `TaskExecutor`, 허용 채널·Role 매핑, Program의 `DiscordDeliveryStatus`/`DiscordDeliveryResult` 교체(**Breaking Change**, 요구사항 §58대로 PR 본문에 명시), 요구사항 §36·§37의 상태 조회·수동 Retry REST API(Controller는 `domain.notification`에 배치 — 결정 9), Swagger 문서화.
- **PR D — 통합 검증/운영 보강.** 실제 dev Bot 연동 확인, 운영 설정, 문서.
- **별도 Issue — Collector Discord Webhook을 GETI-Bot-V1로 통합** (요구사항 §34, 이번 범위에서 명시적 제외).
