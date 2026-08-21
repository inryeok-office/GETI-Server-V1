# Discord 전달 내역 전체 목록 조회 API(Admin) 구현 명세

Issue [#206](https://github.com/inryeok-office/GETI-Server-V1/issues/206)에 대해 Planning Interview를 거쳐 확정한 구현 명세다.
이슈 본문이 "제안, 확정 아님"으로 남겨 둔 항목(경로, Field, 권한)을 이 문서가 확정하며, 이번 작업의 기준은 이 문서다.

---

## 1. 배경

관리자 Discord 전달 관리 화면(`GETI-Client-V1` `src/views/admin-discord-post`)은 Type을 가리지 않고 전체 전달 내역을
하나의 Table로 보여준다. 그런데 `develop`의 Backend API는 전부 **대상 ID를 미리 알아야** 상태를 조회할 수 있다.

| 기존 Endpoint | 권한 |
| --- | --- |
| `GET /api/v1/admin/jobs/{jobId}/discord` + `POST .../retry` | TEACHER / DEVELOPER |
| `GET /api/v1/admin/programs/{programId}/discord` + `POST .../retry` | 등록자·담당 교사 / DEVELOPER |
| `GET /api/v1/admin/inquiries/{inquiryId}/discord` | DEVELOPER |

즉 관리자가 실패한 전달을 찾으려면 **어떤 공고/프로그램/문의가 실패했는지 이미 알고 있어야** 한다. 실패 건을 발견할
방법 자체가 없다. 이 작업은 그 Gap을 메우는 **횡단 목록 조회 API 하나**를 추가한다.

개별 재시도는 기존 Endpoint를 그대로 재사용한다. 이 목록 API는 재시도 기능을 갖지 않는다.

---

## 2. 조사 결과 (`develop`, `c7b38e9` 기준)

### 2.1 이미 있는 것

- `DiscordDelivery` Entity / `discord_deliveries` Table (V19) — `targetType`, `targetId`, `action`, `template`,
  `channelId`, `status`, `discordMessageId`, `automaticRetryCount`, `manualRetryCount`, `nextRetryAt`,
  `lastAttemptAt`, `deliveredAt`, `lastErrorCode`, `lastErrorMessage`, `createdAt`
- `DiscordDeliveryRepository` — `findByIdempotencyKey`, `findFirstByTargetTypeAndTargetIdOrderByIdDesc`,
  `findDueIds`, `claim`, `recoverStaleProcessing`
- `DiscordDeliveryStatusResponse` — 단일 대상 상태 응답 DTO
- `DiscordDeliveryAdminController` — 위 표의 5개 Endpoint
- `DiscordDeliveryRetryPolicy.canRetryManually(manualRetryCount)`, `DiscordBotProperties.maxAutomaticRetryCount`
  / `maxManualRetryCount`
- Named Interface `Job/Program/InquiryDiscordPayloadQueryPort` — **`findById` 단건만** 존재
- Index: `idx_discord_deliveries_status_next_retry (status, next_retry_at)`,
  `idx_discord_deliveries_target (target_type, target_id)`

### 2.2 없는 것

- Type 무관 횡단 목록 조회 Query / Service / Endpoint
- 대상 리소스 이름을 **배치로** 읽는 계약 (`*NotificationTargetQueryPort.findAllByIds`는 배치이지만 `status`/`deleted`
  /`authorMemberId`만 담고 제목이 없다)

### 2.3 발견한 두 가지 함정

**(A) Payload Snapshot이 없다.** Client Mock의 `messageTitle`/`messageBody`에 직접 대응하는 저장 값이 없다.
`DiscordDelivery`는 실패 당시 Payload를 **의도적으로 저장하지 않는다**(요구사항 §19·§39) — 재시도는 원본의 최신 데이터를
다시 읽어 보내야 하고, 문의 본문 같은 개인정보를 중복 저장하지 않기 위해서다. 또 `InquiryDiscordPayloadSnapshot`은
§40에 따라 **제목조차 담지 않는다**(문의 제목에 본인 식별 정보가 들어갈 수 있음).

→ `messageBody`는 어떤 방식으로도 제공하지 않는다. `messageTitle`은 원본에서 **조회 시점에 배치로 다시 읽어** 채운다.

**(B) 목록의 행 단위와 재시도 Endpoint의 단위가 다르다.** `discord_deliveries`는 (대상, Action)마다 한 Row다 —
공고 하나가 게시 후 수정되면 `CREATE`·`UPDATE` 두 Row가 생긴다. 그런데 재시도 Endpoint는
`findFirstByTargetTypeAndTargetIdOrderByIdDesc`로 **대상의 최신 Row 하나만** 재시도한다.

→ 목록이 Row 단위로 내려가면 관리자가 "CREATE 실패(id=5)" 행에서 재시도를 눌러도 서버는 "UPDATE 성공(id=9)"을 건드려
`DISCORD_DELIVERY_NOT_RETRYABLE`(409)로 거부한다. 이 불일치를 `canRetry` 계산으로 해소한다(§3 결정 5).

---

## 3. 확정 결정

| # | 결정 | 근거 |
| --- | --- | --- |
| 1 | **권한: DEVELOPER 전용** | 세 대상의 기존 권한이 서로 다르다. 가장 엄격한 규칙(INQUIRY=DEVELOPER)에 맞춰야 기존 정책을 우회하지 않는다. Service 계층 행 필터링이 불필요해져 Query·Test도 단순해진다. |
| 2 | **대상 이름 포함, 배치 조회** | 이슈 완료 조건이 "Client가 Mock 없이 조회"이고 Client Table에 이름 컬럼이 있다. 단건 루프는 최대 100회 추가 조회(N+1)라 이슈 Performance 절의 "배치 조회" 방침과 어긋난다. |
| 3 | **배치 계약은 기존 `*DiscordPayloadQueryPort`에 메서드 추가** | 이 Port가 이미 "Discord에 보여줄 의미 데이터"의 소유자라 의미가 정확하고, 새 파일·새 Bean·새 Module 의존이 생기지 않는다. `*NotificationTargetQueryPort`에 title을 넣는 안은 인앱 알림이 쓰지 않는 필드가 실리고 Inquiry에서 §40과 정면 충돌한다. |
| 4 | **INQUIRY 표시 이름 = `category`(문의 유형)** | §40에 따라 제목·작성자 이름을 노출하지 않는다. `category`는 이미 Discord로 나가는 값이라 노출 범위가 넓어지지 않는다. |
| 5 | **Delivery Row를 그대로 나열하고 `canRetry`를 정확히 계산** | 대상별 최신 1건으로 집계하면 "CREATE 실패 후 UPDATE 성공" 이력이 숨겨져, 이슈가 해결하려는 "실패 건 발견" 목적 자체가 깨진다. Row는 전부 보여주되 재시도 불가한 행은 `canRetry=false`로 내려 Client가 실패할 버튼을 띄우지 않게 한다. |
| 6 | **Filter는 `status`만** | 이슈 명시 요구사항이 `status`뿐이고 Client 근거에도 type Filter 요구가 없다. AGENTS.md "이슈에 없는 기능을 임의로 추가하지 않는다". 필요해지면 후속 Issue. |
| 7 | **새 Index 추가 안 함** | Filter 없는 기본 조회는 PK 역순 스캔이라 이미 최적이고, 운영 관심사인 `status=FAILED`는 행 수가 작아 기존 `(status, next_retry_at)` Index로 충분하다. 측정된 느림이 없는 상태에서 쓰기 비용을 선제적으로 늘리지 않는다. |
| 8 | **새 `DiscordDeliveryAdminQueryService` 신설** | 전송 파이프라인(enqueue/process/retry)과 관리자 읽기 모델(목록·이름 해석·최신 Row 판정)을 분리한다. `DiscordDeliveryServiceImpl`은 이미 448줄 + `@Suppress("TooManyFunctions")`이고 Test는 606줄이다. |
| 9 | **Controller는 기존 `DiscordDeliveryAdminController`에 추가** | Swagger Tag `Notification - Discord 전달 상태`에 함께 묶여 Client가 한 곳에서 본다. 새 파일·새 Test 클래스 없이 메서드 1개 추가. |
| 10 | **경로 `GET /api/v1/admin/discord-deliveries`** | 기존 admin 경로가 전부 `/api/v1/admin/{kebab-case 복수형}`(companies, job-sources, collection-runs, job-applications, portfolio-requests)이라 이슈 제안이 그대로 관례에 맞는다. |
| 11 | **Test: 이슈 명시 범위 + `integrationTest`** | 새로 쓰는 핵심이 JPQL 5개인데 `@WebMvcTest`/Mock Unit Test는 JPQL 유효성을 검증하지 못한다. 이 저장소에서 Repository Query가 실제로 실행되는 곳은 `src/integrationTest`뿐이다. |

---

## 4. API 계약

### 4.1 Request

```http
GET /api/v1/admin/discord-deliveries?status=FAILED&page=0&size=20
Authorization: Bearer {accessToken}
```

| Parameter | Type | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | `DiscordDeliveryStatus` | 아니오 | `PENDING` / `PROCESSING` / `DELIVERED` / `FAILED`. 생략하면 전체 |
| `page` | int | 아니오 | 0부터 시작, 기본 0 |
| `size` | int | 아니오 | 기본 20, 최대 100(`WebPageableConfig`가 강제) |
| `sort` | — | — | **무시된다.** 정렬은 최신순(`id DESC`)으로 고정 |

`status`는 Enum 그대로 노출한다. 파생 상태("재시도중")를 새로 만들지 않는다 — 서버가 상태의 Source of Truth이고,
자동 재시도 대기는 `status=PENDING` + `automaticRetryCount>0`으로 Client가 그대로 표기할 수 있다. 기존 단일 조회
응답(`DiscordDeliveryStatusResponse`)과 값 집합이 어긋나지 않게 유지하는 것이 더 중요하다.

### 4.2 Response (200)

```json
{
  "data": {
    "content": [
      {
        "deliveryId": 42,
        "targetType": "JOB",
        "targetId": 123,
        "targetName": "2026 상반기 신입 백엔드 개발자",
        "action": "CREATE",
        "channelId": "1234567890123456789",
        "messageId": null,
        "status": "FAILED",
        "automaticRetryCount": 3,
        "manualRetryCount": 0,
        "maxAutomaticRetryCount": 3,
        "maxManualRetryCount": 3,
        "canRetry": true,
        "failureCode": "RATE_LIMITED",
        "failureReason": "Discord 요청이 일시적으로 제한되었습니다.",
        "requestedAt": "2026-08-20T09:00:00",
        "lastSyncedAt": "2026-08-20T09:12:31"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

Field 의미:

| Field | 출처 | 비고 |
| --- | --- | --- |
| `deliveryId` | `discord_deliveries.id` | Client Table의 행 Key |
| `targetType` / `targetId` | 동명 Column | 대상 리소스로 이동하는 정보(이슈 필수 요구사항) |
| `targetName` | **조회 시점 배치 해석** | JOB/PROGRAM은 제목, INQUIRY는 `InquiryType.name`. 원본 Row가 없으면 `null` |
| `action` | `action` Column | 같은 대상의 여러 Row를 구분하는 유일한 값이라 필수 |
| `channelId` / `messageId` | `channel_id` / `discord_message_id` | 기존 단일 조회 응답과 같은 이름 |
| `status` | `status` Column | |
| `automaticRetryCount` / `manualRetryCount` | 동명 Column | |
| `maxAutomaticRetryCount` / `maxManualRetryCount` | `DiscordBotProperties` | 기존 단일 조회 응답과 동일 |
| `canRetry` | **계산값** | §5.3 |
| `failureCode` / `failureReason` | `last_error_code` / `last_error_message` | |
| `requestedAt` | `created_at` | 기존 단일 조회 응답과 같은 매핑 |
| `lastSyncedAt` | `last_attempt_at` | 기존 단일 조회 응답과 같은 매핑 |

**제공하지 않는 것**: `messageBody`(Payload Snapshot 미저장 + §40 개인정보), 문의 제목, 문의 작성자 이름.

### 4.3 Error

| Status | 조건 | Error Code |
| --- | --- | --- |
| 400 | `status` 값이 Enum에 없음 | `TYPE_MISMATCH` (`GlobalExceptionHandler` 기존 처리) |
| 401 | Access Token 없음/무효 | `UNAUTHORIZED` |
| 403 | DEVELOPER 권한 없음 | `FORBIDDEN` |
| 500 | 서버 내부 오류 | — |

404는 없다. 결과가 없으면 빈 `content`와 `totalElements=0`을 반환한다.

---

## 5. 구현 상세

### 5.1 Repository — `DiscordDeliveryRepository`에 Query 2개 추가

```kotlin
/**
 * 관리자 횡단 목록이다. status를 지정하지 않으면 전체를 돌려준다.
 * 정렬은 id DESC로 고정한다 -- id는 BIGSERIAL이라 created_at DESC와 순서가 같고, PK Index를
 * 그대로 쓸 수 있다. 클라이언트가 보낸 Sort는 이 ORDER BY와 충돌하므로 호출부가 제거한다.
 */
@Query(
    """
    SELECT d FROM DiscordDelivery d
    WHERE (:status IS NULL OR d.status = :status)
    ORDER BY d.id DESC
    """,
)
fun findRecent(
    @Param("status") status: DiscordDeliveryStatus?,
    pageable: Pageable,
): Page<DiscordDelivery>

/**
 * 주어진 대상들에 대해 "그 대상의 가장 최근 Delivery"인 Row의 id 집합이다.
 *
 * 수동 재시도 Endpoint가 대상별 최신 Row 하나만 재시도하므로
 * (findFirstByTargetTypeAndTargetIdOrderByIdDesc), 목록의 canRetry는 그 Row에만 true여야 한다.
 * GROUP BY 결과를 받으려면 Projection 타입이 필요한데 이 저장소에는 전례가 없어, 상관
 * 서브쿼리로 id만 돌려받는다.
 *
 * targetTypes/targetIds를 각각 IN으로 걸어 (JOB,3)과 (PROGRAM,5)만 필요할 때 (JOB,5)도 함께
 * 걸린다. 그 여분 Row는 현재 Page에 없으므로 판정 결과에 영향을 주지 않는다 -- 정확성 대신
 * Tuple IN 문법 의존을 피한 선택이다.
 */
@Query(
    """
    SELECT d.id FROM DiscordDelivery d
    WHERE d.targetType IN :targetTypes
      AND d.targetId IN :targetIds
      AND NOT EXISTS (
          SELECT 1 FROM DiscordDelivery o
          WHERE o.targetType = d.targetType AND o.targetId = d.targetId AND o.id > d.id
      )
    """,
)
fun findLatestDeliveryIds(
    @Param("targetTypes") targetTypes: Set<DiscordDeliveryTargetType>,
    @Param("targetIds") targetIds: Set<Long>,
): List<Long>
```

### 5.2 Named Interface — 배치 조회 메서드 3개 추가

기존 Interface에 메서드만 더한다. **새 Module 의존이 생기지 않는다** — `notification`은 이미 세 Port를 모두
주입받고 있다(`DiscordDeliveryServiceImpl` 생성자).

```kotlin
// domain/job/query/JobDiscordPayloadQueryPort.kt
/**
 * 관리자 목록에 표시할 공고 제목을 배치로 읽는다(Issue #206). 존재하지 않는 id는 결과 Map에서
 * 빠진다. findById와 같은 이유로 삭제된 공고도 포함한다 -- 실패한 DELETE_NOTICE 전달이 목록에
 * 남아 있는데 이름이 사라지면 관리자가 대상을 식별할 수 없다.
 *
 * Snapshot 전체가 아니라 표시 이름만 돌려준다. 목록은 제목 외의 값을 쓰지 않고, Snapshot을
 * 그대로 반환하면 program의 bodyMarkdown 같은 큰 값까지 최대 100건 실려 온다.
 */
fun findDisplayNamesByIds(jobIds: Set<Long>): Map<Long, String>

// domain/program/query/ProgramDiscordPayloadQueryPort.kt
fun findDisplayNamesByIds(programIds: Set<Long>): Map<Long, String>

// domain/inquiry/query/InquiryDiscordPayloadQueryPort.kt
/**
 * 문의는 제목 대신 InquiryType.name(category)을 표시 이름으로 쓴다 -- §40에 따라 제목과 작성자
 * 이름을 Module 밖으로 내보내지 않는다. category는 이미 Discord 알림으로 나가는 값이다.
 */
fun findDisplayNamesByIds(inquiryIds: Set<Long>): Map<Long, String>
```

구현은 각 `*DiscordPayloadQueryPortImpl`에 추가한다(`@Transactional(readOnly = true)`).
`JobRepository`/`ProgramRepository`/`InquiryRepository`의 `findAllById(ids)`를 쓰고 `associate`로 Map을 만든다.
Soft Delete 필터를 걸지 않는다(기존 `findById`와 같은 이유).

### 5.3 Service — `DiscordDeliveryAdminQueryService` 신설

`domain/notification/service/DiscordDeliveryAdminQueryService.kt` (Interface)
+ `domain/notification/service/impl/DiscordDeliveryAdminQueryServiceImpl.kt`

```kotlin
interface DiscordDeliveryAdminQueryService {
    fun listRecent(
        status: DiscordDeliveryStatus?,
        pageable: Pageable,
    ): DiscordDeliveryListResponse
}
```

Impl 생성자(6개 — detekt `LongParameterList` 기본 임계값 7 미만):
`DiscordDeliveryRepository`, `Job/Program/InquiryDiscordPayloadQueryPort`, `DiscordDeliveryRetryPolicy`,
`DiscordBotProperties`.

처리 순서(`@Transactional(readOnly = true)`):

1. `findRecent(status, PageRequest.of(pageable.pageNumber, pageable.pageSize))`
   — `NotificationServiceImpl.list`와 같은 이유로 Sort를 버리고 Page 정보만 남긴다.
2. Page content가 비면 즉시 빈 응답 반환 — 다른 Module에 빈 질의를 보내지 않는다
   (`NotificationTargetResolver.loadSnapshots`와 같은 관례).
3. `targetType`별로 `targetId`를 모아 **Domain당 최대 1회씩** `findDisplayNamesByIds` 호출.
   해당 Type의 대상이 하나도 없으면 그 Port는 호출하지 않는다.
4. `findLatestDeliveryIds(page의 targetType 집합, targetId 집합)`으로 최신 Row id 집합을 1회 조회.
5. 각 Row를 매핑. `canRetry` 판정:

```kotlin
val canRetry =
    delivery.id in latestDeliveryIds &&
        delivery.status == DiscordDeliveryStatus.FAILED &&
        retryPolicy.canRetryManually(delivery.manualRetryCount)
```

기존 단일 조회의 `canRetry`(= `FAILED && canRetryManually`)에 **"이 Row가 대상의 최신 Row"** 조건이 하나 더
붙는다. 단일 조회는 애초에 최신 Row만 반환하므로 두 계산은 서로 모순되지 않는다.

**총 Query 수: 목록 1 + count 1 + 최신 id 1 + 대상 Domain당 최대 1 = 최대 6회.** Page 크기와 무관하다.

### 5.4 DTO — `domain/notification/dto/DiscordDeliveryListResponse.kt`

`NotificationListResponse`/`InquiryAdminListResponse`와 같은 Domain 전용 Pagination 구조를 따른다
(`content`/`page`/`size`/`totalElements`/`totalPages`/`first`/`last`). `global.web.PageResponse`는 이 저장소의
어떤 Domain도 쓰지 않는다.

같은 파일에 `DiscordDeliveryListItemResponse`(§4.2 Field)를 둔다. 모든 Field에 `@param:Schema`를 붙인다.
기존 `DiscordDeliveryStatusResponse`는 **건드리지 않는다** — 단일 조회 계약을 이유 없이 바꾸지 않기 위해서다.

### 5.5 Controller — `DiscordDeliveryAdminController`에 메서드 추가

```kotlin
@Operation(summary = "Discord 전달 내역 전체 목록 조회", description = """...""")
@ApiResponses(/* 200, 400, 401, 403, 500 */)
@GetMapping("/api/v1/admin/discord-deliveries")
fun listDiscordDeliveries(
    @Parameter(description = "전달 상태 Filter(선택). 생략하면 전체")
    @RequestParam(required = false)
    status: DiscordDeliveryStatus?,
    @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort는 무시된다.")
    pageable: Pageable,
): ApiResponse<DiscordDeliveryListResponse> =
    ApiResponse.of(discordDeliveryAdminQueryService.listRecent(status, pageable))
```

함께 갱신할 것:

- Class KDoc — "URL은 각 도메인의 기존 admin 경로 아래에 있지만"이 더는 전부 사실이 아니므로, 횡단 목록만
  `notification` 자신의 admin 경로를 쓰고 그래서 `SecurityConfig`에 규칙 한 줄이 필요하다는 점을 적는다.
- `@Tag` description — 횡단 목록 조회를 포함하도록 수정.
- `description`에 명시할 것: 재시도는 이 API가 제공하지 않고 기존 대상별 Endpoint를 쓴다,
  `canRetry=false`인 행은 재시도 버튼을 띄우면 안 된다, `messageBody`는 제공하지 않는다.

### 5.6 Security — `SecurityConfig.applyNormalSecurityRules()`

문의(DEVELOPER 전용) 규칙 근처에 한 줄 추가한다.

```kotlin
// Discord 전달 내역 횡단 목록(Issue #206)은 개발자만 접근한다. 대상별 개별 조회는 각 도메인의
// admin 경로 규칙(jobs=TEACHER/DEVELOPER 등)을 그대로 따르지만, 이 목록은 세 Type을 한 Table에
// 섞어 보여주므로 그중 가장 엄격한 INQUIRY 규칙(DEVELOPER)에 맞춘다 -- 그렇지 않으면 교사가
// 목록을 통해 문의 Discord 전달의 존재를 알게 되어 기존 정책을 우회한다.
authorize("/api/v1/admin/discord-deliveries", hasRole("DEVELOPER"))
```

`applyNormalSecurityRules()`는 현재 호출되지 않는다(임시 전역 `permitAll`, Issue #162). 그래도 규칙을 함께
넣어야 복구 시점에 이 Endpoint가 조용히 열린 채 남지 않는다.

---

## 6. Architecture 영향

- **Spring Modulith**: 새 Module 의존 없음. `notification → job.query / program.query / inquiry.query`는 이미
  존재하고, 기존 `@NamedInterface`에 메서드만 추가한다. `ModularityTest`/`PackageArchitectureTest`에 영향 없음.
- **Migration**: 없음. 새 Column도 새 Index도 추가하지 않는다(결정 7).
- **DB 쓰기 없음**: 전 구간 `@Transactional(readOnly = true)`.
- **외부 I/O 없음**: Discord Bot을 호출하지 않는다.

---

## 7. 변경 파일

### 신규

```text
src/main/kotlin/.../domain/notification/dto/DiscordDeliveryListResponse.kt
src/main/kotlin/.../domain/notification/service/DiscordDeliveryAdminQueryService.kt
src/main/kotlin/.../domain/notification/service/impl/DiscordDeliveryAdminQueryServiceImpl.kt
src/test/kotlin/.../domain/notification/service/impl/DiscordDeliveryAdminQueryServiceImplTest.kt
docs/notification/discord-delivery-admin-list-plan.md   (이 문서)
```

### 수정

```text
src/main/kotlin/.../domain/notification/repository/DiscordDeliveryRepository.kt        Query 2개 추가
src/main/kotlin/.../domain/notification/controller/DiscordDeliveryAdminController.kt   메서드 1개 + KDoc/Tag
src/main/kotlin/.../domain/job/query/JobDiscordPayloadQueryPort.kt                     배치 메서드
src/main/kotlin/.../domain/job/service/impl/JobDiscordPayloadQueryPortImpl.kt          구현
src/main/kotlin/.../domain/program/query/ProgramDiscordPayloadQueryPort.kt             배치 메서드
src/main/kotlin/.../domain/program/service/impl/ProgramDiscordPayloadQueryPortImpl.kt  구현
src/main/kotlin/.../domain/inquiry/query/InquiryDiscordPayloadQueryPort.kt             배치 메서드
src/main/kotlin/.../domain/inquiry/service/impl/InquiryDiscordPayloadQueryPortImpl.kt  구현
src/main/kotlin/.../global/security/SecurityConfig.kt                                  규칙 1줄
src/test/kotlin/.../domain/notification/controller/DiscordDeliveryAdminControllerTest.kt  목록 Test
src/integrationTest/kotlin/.../persistence/DiscordDeliveryRepositoryIntegrationTest.kt    Query 검증
```

---

## 8. Test 계획

### 8.1 Service Unit Test (`DiscordDeliveryAdminQueryServiceImplTest`)

- 세 Type이 섞인 Page에서 **대상 Domain당 Port 호출이 정확히 1회**인지 (N+1 방지 검증)
- 특정 Type의 대상이 없으면 그 Port를 **아예 호출하지 않는지**
- `targetName` 매핑: JOB/PROGRAM=제목, INQUIRY=category, 원본 Row 없음=`null`
- `canRetry` 판정 4가지
  - 최신 Row + FAILED + 상한 미만 → `true`
  - **최신이 아닌 Row** + FAILED + 상한 미만 → `false` (§2.3 (B) 회귀 방지, 가장 중요)
  - 최신 Row + FAILED + 수동 상한 소진 → `false`
  - 최신 Row + DELIVERED → `false`
- Client가 보낸 `sort`가 Repository로 전달되지 않는지
- content가 비면 세 Port와 `findLatestDeliveryIds`를 호출하지 않고 빈 응답을 반환하는지

### 8.2 Controller Test (`@WebMvcTest`, 기존 `DiscordDeliveryAdminControllerTest`에 추가)

- 200: 응답 JSON Field 이름·구조(`data.content[]`, `data.page`, …)
- 400: `status=UNKNOWN` → `TYPE_MISMATCH`
- 401 / 403: 기존 Test와 같은 방식(보존된 정상 Security 규칙 기준)
- `size=500` 요청 시 실제로 100으로 잘리는지
- 오류 응답에 Stack Trace·내부 정보가 없는지

### 8.3 Integration Test (`DiscordDeliveryRepositoryIntegrationTest`에 추가, Docker 필요)

- `findRecent(null, …)`이 `id DESC`로 정렬되고 전체를 반환하는지
- `findRecent(FAILED, …)`가 해당 상태만 반환하고 Pagination 메타가 맞는지
- `findLatestDeliveryIds`가 같은 대상의 여러 Row 중 **최대 id만** 반환하는지
- 세 Type이 섞였을 때 Type별로 올바르게 그룹이 나뉘는지
- 세 `findDisplayNamesByIds` 구현: 존재하지 않는 id 제외, **Soft Delete된 대상 포함**

### 8.4 문서화 검증

- `OpenApiDocumentationTest` 통과 (`docs/ai/openapi-documentation.md` 규칙)

### 8.5 실행 명령

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat clean test build
```

`integrationTest`는 Docker(Testcontainers)가 필요하며 `test`/`check`/`build`에 포함되지 않는다.

---

## 9. 가정과 미확인 사항

| # | 항목 | 처리 |
| --- | --- | --- |
| 1 | GETI Notion API 명세서에 이 Endpoint의 확정 계약이 있는지 **확인하지 못했다** | 저장소 관례(admin 경로 명명, Domain 전용 Pagination DTO, 기존 Error Code)를 기준으로 확정했다. 이후 Notion 계약이 발견되어 다르면 `CONTRACT_MISMATCH`로 보고하고 사용자 결정을 받는다. |
| 2 | Client `mock.ts`의 `messageTitle`/`messageBody` | `messageBody`는 제공하지 않는다(§2.3 A). `messageTitle`은 `targetName`으로 대응한다. Client의 Type 정의 수정이 필요하며, 이 Backend 작업 범위 밖이다. |
| 3 | Client `mock.ts`의 `retryCount`/`maxRetryCount`(단수) | 서버는 자동/수동을 분리해 4개 Field로 내린다. 기존 단일 조회 응답과 같은 형태이므로 Client가 어느 쪽을 표시할지 정하면 된다. |
| 4 | `messageId`를 응답에 포함 | Client Mock에는 없지만 기존 단일 조회 응답에 있는 같은 Row의 값이고, 관리자가 Discord 메시지를 직접 확인할 때 필요하다. 새 기능이 아니라 동일 계약 유지로 판단했다. |
| 5 | INQUIRY 표시 이름이 `category`라 같은 유형 문의가 여러 건이면 이름이 겹친다 | `targetId`가 함께 내려가므로 식별에는 문제가 없다. 제목 노출은 §40이 금지한다. |
| 6 | Client 저장소(`GETI-Client-V1`)에 접근할 수 없어 Mock 원문을 직접 확인하지 못했다 | 이슈 본문이 인용한 Field 목록을 근거로 삼았다. |

---

## 10. 범위 제외

- 이 목록 API에 재시도 기능 추가 (이슈가 명시적으로 제외)
- `deliveryId` 기반 재시도 Endpoint 신설
- `targetType` / 기간 Filter (결정 6, 필요 시 후속 Issue)
- 새 Index / Migration (결정 7)
- INQUIRY 수동 재시도 Endpoint (요구사항 §37에 없음)
- `discord_delivery_attempts` 시도 이력 조회 API
- Client(`GETI-Client-V1`) 수정
- `SecurityConfig`의 임시 전역 `permitAll` 해제 (Issue #162 범위)
