# Discord Event 연결 구현 명세 (Notification 후속 PR B)

[`notification_after_development.md`](./notification_after_development.md)의 **PR B(Domain Event 연결)** 범위를 현재 저장소 상태(`develop`, V19 병합 이후)에 맞춰 확정한 구현 명세다. Issue #97에 대응한다.

선행 PR([#95](https://github.com/inryeok-office/GETI-Server-V1/pull/95), Issue #96, [`discord-delivery-plan.md`](./discord-delivery-plan.md))이 Delivery Persistence·Worker·실제 Bot HTTP 연동을 완성했다. **지금은 트리거가 없어 운영에서 Delivery Row가 생기지 않는다** — 이 문서는 그 트리거를 붙이고, 각 도메인에 흩어진 기존 Discord 상태를 공통 계약으로 교체하는 작업을 다룬다.

---

## 1. 작업 전 확인 결과

모두 `develop` 최신 코드에서 직접 확인했다.

| # | 확인 항목 | 실제 상태 |
| --- | --- | --- |
| 1 | Job Event 발행 지점 | `JobServiceImpl.create`(:74), `.update`(:107), `.changeStatus`(:146) 3곳 + `CollectedJobUpsertUseCaseImpl`(:79) 1곳. 전부 `JobChangedEvent(jobId)` |
| 2 | `JobChangedEvent` 구독자 | `domain.search.event.JobIndexSyncEventListener` **하나뿐** |
| 3 | Job 상태 전이 | `DRAFT→{PUBLISHED, DELETED}`, `PUBLISHED→{CLOSED, DELETED}`, `CLOSED→{DELETED}`. **네 Action이 모두 도달 가능** |
| 4 | Job 게시 진입점 | `create(status=PUBLISHED)`와 `changeStatus(DRAFT→PUBLISHED)` **두 곳** |
| 5 | Program Event | **없음**. 어떤 Domain Event도 발행하지 않는다 |
| 6 | Program 상태 전이 | `DRAFT→{PUBLISHED, DELETED}`, `PUBLISHED→{DELETED}`, `CLOSED→{DELETED}` |
| 7 | Program `CLOSED` 도달 경로 | **없다.** `status = ProgramStatus.CLOSED` 대입이 저장소 전체에 존재하지 않는다(§2 참고) |
| 8 | Program Discord 상태 노출 | `DiscordDeliveryResult.SKIPPED_NOT_IMPLEMENTED`가 `ProgramCreateResponse`(:141), `ProgramUpdateResponse`(:213), `ProgramStatusUpdateResponse`(:282) 세 곳에서 **항상 고정 반환** |
| 9 | Program 채널 | `ProgramCreateRequest.discordChannelId`(원시 Snowflake)를 **클라이언트가 입력**하고, 게시 시 `DiscordChannelRequiredException`으로 **비어 있는지만** 검사한다. 허용 목록 검증 없음 |
| 10 | Job 채널 | `Job.discordChannelKey` Column은 있으나 **읽고 쓰는 코드가 하나도 없다**(죽은 Column) |
| 11 | Inquiry Event | `InquiryCreatedEvent` **발행 중, 구독자 없음**. `InquiryAnsweredEvent`는 `domain.notification`이 이미 구독 |
| 12 | Inquiry Discord 상태 | `InquiryDiscordDeliveryQueryPortImpl`이 항상 `PENDING` 반환. `Inquiry.discordMessageId`/`discordErrorMessage`는 `@Deprecated` |
| 13 | Program 관리 권한 | `requireManager`(:529) — `createdByMemberId` 또는 `managerMemberId`이거나 DEVELOPER |
| 14 | Security 규칙 | `/api/v1/admin/jobs/**`·`/api/v1/admin/programs/**`는 `hasAnyRole("TEACHER","DEVELOPER")`, `/api/v1/admin/inquiries/**`는 `hasRole("DEVELOPER")` |
| 15 | AFTER_COMMIT 패턴 | `@TransactionalEventListener(phase = AFTER_COMMIT)` + 도메인 전용 `TaskExecutor`(`SearchTaskExecutorConfig`). `runCatching`으로 예외를 삼키고 로그만 남김 |
| 16 | 최신 Migration | **V19** → 이번 PR은 **V20**(필요한 경우) |

---

## 2. 확정된 제약 — `PROGRAM_CLOSED`는 연결하지 않는다

`ProgramStatus.CLOSED`는 Enum과 전이표에 존재하지만 **그 값을 대입하는 코드가 없다.** `ProgramStatusUpdateRequest` KDoc과 `ProgramAdminController`(:133)는 이렇게 적어 두었다.

> `PUBLISHED -> CLOSED`는 이 API로 지정할 수 없다 — 신청 종료 시각 도달 시 서버 Scheduler가 처리한다

그런데 그 Scheduler가 아직 구현되지 않았다. 따라서 `PROGRAM_CLOSED` Template은 **현재 어떤 경로로도 발동할 수 없다.**

이번 범위에서는 연결하지 않고 사유를 코드 주석과 이 문서에 남긴다(사용자 확정). Program 자동 마감 Scheduler가 생기는 시점에 `PROGRAM_CLOSED`를 연결한다. 이는 Discord와 무관한 **Program 도메인 자체의 공백**이므로 별도 Issue 후보로 보고한다 — 신청 기간이 끝나도 프로그램이 계속 `PUBLISHED`로 보인다.

Job은 `changeStatus`로 `PUBLISHED → CLOSED`가 정상 도달하므로 `JOB_CLOSED`는 이번에 연결한다.

---

## 3. 확정된 결정

| # | 항목 | 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | `JobChangedEvent` | **깨지 않는다.** Discord용 신규 Event를 별도로 추가 | `search`가 구독 중인 공개 계약. "id만 담고 재조회로 수렴"은 Issue #69의 의도적 설계 |
| 2 | 수집 공고 | **Discord Delivery를 만들지 않는다** | Collector가 이미 자체 Webhook으로 알림 중. 둘 다 보내면 중복 공지(요구사항 §34) |
| 3 | `PROGRAM_CLOSED` | 연결하지 않고 문서화 | §2, 사용자 확정 |
| 4 | 채널·Role 값 | **설정 구조와 매핑 로직만 구현하고 값은 비워 둔다** | 실제 Snowflake 미확정. 요구사항 §10이 "코드 상수로 임의 지정 금지" |
| 5 | Job 채널 선택 | `JobCreateRequest`/`JobUpdateRequest`에 **논리 Key** `discordChannelKey` 추가. 죽어 있던 Column을 살린다 | 사용자 확정. 운영 Snowflake를 API·DB에 노출하지 않는다 |
| 6 | Program 채널 | 기존 `discordChannelId` 계약을 유지하되 **허용 목록 검증을 추가** | 요구사항 §10 "등록 API에 discordChannelId가 존재하더라도 서버에서 반드시 허용 목록을 검증" |
| 7 | 상태 조회·재시도 API | URL은 명세대로 도메인 경로, **Controller는 `domain.notification`에 배치** | `JobAdminController`에 두면 `job → notification` 역방향으로 순환 의존 |
| 8 | Retry 권한 | 기존 명세 유지. 임의 확대 금지 | 요구사항 §37 |
| 9 | Program·Inquiry 응답의 `discordDelivery` | **응답에서 제거하고 §7의 조회 API로 일원화** | 순환 의존을 구조적으로 차단. 등록·수정 응답 시점에는 Delivery Row가 없어 어차피 고정값밖에 담을 수 없다 |
| 10 | Job Retry 권한 | **Role만 검사**(TEACHER·DEVELOPER), 소유권 개념 미도입 | Job은 담당자 기준 자체가 미확정(`JobErrorCode` 주석). AskUserQuestion으로 사용자 확정(§7 결정 10) |
| 11 | Program 소유권 조회 | `ProgramDiscordPayloadQueryPort`(Bot Payload 전용)를 재사용하지 않고 `ProgramManagerQueryPort` 신설 | 기존 Port는 용도가 다르다고 문서화되어 있어 재사용하면 계약이 흐려짐(§7 결정 11) |

---

## 4. Event 설계

### 4.1 신규 Event (`domain.{job,program}.event`)

```kotlin
@NamedInterface
data class JobDiscordEvent(
    val jobId: Long,
    val action: JobDiscordAction,   // PUBLISHED / UPDATED / CLOSED / DELETED
)
```

`ProgramDiscordEvent`도 같은 형태(`programId`, `ProgramDiscordAction`)다.

- **Entity를 담지 않는다.** id와 Action만 담고, 수신 측이 공개 Query Port로 최신 상태를 다시 읽는다(저장소 전체 관례, 요구사항 §26).
- `JobChangedEvent`와 **함께** 발행한다. `search`는 기존 Event를, `notification`은 새 Event를 구독해 서로 영향이 없다.
- Action Enum을 소유 도메인에 두는 이유: `notification`의 `DiscordMessageTemplate`을 `job`이 알게 되면 `job → notification` 역방향 의존이 생겨 순환이 된다. Template 매핑은 수신 측(`notification`)이 한다.

### 4.2 발행 지점

| 도메인 | 지점 | 조건 | Action |
| --- | --- | --- | --- |
| Job | `create` | `status == PUBLISHED`로 등록 | `PUBLISHED` |
| Job | `changeStatus` | `DRAFT → PUBLISHED` | `PUBLISHED` |
| Job | `update` | 대상이 **이미 PUBLISHED/CLOSED**일 때만 | `UPDATED` |
| Job | `changeStatus` | `→ CLOSED` | `CLOSED` |
| Job | `changeStatus` | `→ DELETED` **이면서 이전 상태가 DRAFT가 아닐 때만** | `DELETED` |
| Job | `CollectedJobUpsertUseCaseImpl` | — | **발행하지 않는다**(결정 2) |
| Program | `create` | `status == PUBLISHED`로 등록 | `PUBLISHED` |
| Program | `changeStatus` | `DRAFT → PUBLISHED` | `PUBLISHED` |
| Program | `update` | 대상이 **이미 PUBLISHED**일 때만 | `UPDATED` |
| Program | `changeStatus` | `→ DELETED` **이면서 이전 상태가 DRAFT가 아닐 때만** | `DELETED` |
| Inquiry | (기존 `InquiryCreatedEvent`) | 생성 성공 | `INQUIRY_CREATED` |

**DRAFT 관련 조건이 핵심이다.** 한 번도 게시되지 않은 DRAFT를 수정하거나 삭제하면 Discord에 아직 메시지가 없다. 그 상태에서 `UPDATED`/`DELETED` Delivery를 만들면 `discordMessageId`가 없어 Worker가 `MISSING_DISCORD_MESSAGE_ID`로 FAILED 처리한다(선행 PR §8.4) — 실패할 것이 뻔한 Row를 쌓는 셈이다. 그래서 **발행 단계에서 거른다.**

Inquiry는 기존 Event를 그대로 쓴다. 새 Event를 만들지 않는다.

### 4.3 수신 (`domain.notification.event`)

`JobDiscordEventListener` / `ProgramDiscordEventListener` / `InquiryDiscordEventListener`.

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onJobDiscordEvent(event: JobDiscordEvent) { ... }
```

- **AFTER_COMMIT**이라 원본 Transaction이 Rollback되면 Delivery가 생기지 않는다(요구사항 §29·§55).
- `runCatching`으로 예외를 삼키고 로그만 남긴다 — 이미 Commit된 원본을 되돌릴 수 없고, 되돌려서도 안 된다(요구사항 §48). `InquiryAnsweredNotificationListener`와 같은 방식이다.
- Action → `DiscordMessageTemplate` 매핑과 채널·Role 결정은 Listener가 수행하고, 최종적으로 `DiscordDeliveryService.enqueue()`를 호출한다.
- 전용 `TaskExecutor`는 **두지 않는다.** Listener가 하는 일은 Query Port 조회 없이 Row 하나를 만드는 것뿐이라(실제 전송은 Worker가 별도 Thread에서 수행) 요청 Thread를 오래 잡지 않는다. `search`가 전용 Pool을 둔 이유(Elasticsearch 색인 I/O)가 여기에는 없다.

### 4.4 `UPDATE`의 `sourceUpdatedAt`

선행 PR의 Idempotency Key는 UPDATE에 대해 원본 `updatedAt`을 요구한다(`discord-delivery-plan.md` §6.3). Listener는 Query Port로 조회한 Snapshot의 `updatedAt`을 넘긴다 → **두 Query Port Snapshot에 `updatedAt`을 추가**해야 한다.

---

## 5. 채널·Role 매핑

### 5.1 설정 구조

`app.discord.bot`(Bot Internal API 접속 설정, 선행 PR)과 **같은 Key 아래 두지 않는다.** 이쪽은 "어디에 보낼지"만 정하고 Job·Program·Notification 세 Module이 함께 읽는 값이라, 별도 Prefix `app.discord.channel-policy`로 분리해 `DiscordBotProperties`와 바인딩이 섞이지 않게 한다.

```yaml
app:
  discord:
    channel-policy:
      # 허용 채널 목록. 실제 Snowflake는 코드에 두지 않고 환경변수로만 주입한다(요구사항 §10·§25).
      # 비어 있으면 Delivery를 만들지 않고 경고 로그만 남긴다 — 기동과 원본 API는 정상이다.
      channels:
        job-notice:
          channel-id: ${DISCORD_CHANNEL_JOB_NOTICE:}
          display-name: 공고 공지
        program-notice:
          channel-id: ${DISCORD_CHANNEL_PROGRAM_NOTICE:}
          display-name: 프로그램 공지
        inquiry-alert:
          channel-id: ${DISCORD_CHANNEL_INQUIRY_ALERT:}
          display-name: 문의 접수 알림
      # 학년 → Mention Role. 최초 성공 CREATE 한 번만 사용된다(요구사항 §24).
      grade-roles:
        "1": ${DISCORD_ROLE_GRADE_1:}
        "2": ${DISCORD_ROLE_GRADE_2:}
        "3": ${DISCORD_ROLE_GRADE_3:}
      default-job-channel-key: job-notice
      default-program-channel-key: program-notice
      inquiry-channel-key: inquiry-alert
```

- 채널 **4개의 정확한 이름·용도가 확정되지 않아** 위 3개는 임시 Key다. 확정되면 이 설정만 늘리면 된다(요구사항 §10 "코드 상수로 임의 지정하지 않는다").
- 이 블록은 **`application.yaml`에 실제로 선언한다.** 채널 Key(`job-notice` 등)와 `default-job-channel-key`/`inquiry-channel-key`는 Secret이 아니므로 여기서 확정하고, Snowflake와 Role ID만 환경변수로 남긴다. Key까지 비워 두면 Snowflake를 주입해도 `channelIdOf("")`가 `null`이라 기본 채널 Job과 Inquiry는 Delivery가 영원히 생기지 않는다.
- `guildId`는 넣지 않는다. Bot이 Token으로 접속한 Guild에서 `channelId`로 직접 해석하며, Bot Internal API 계약(`channelId`만 받음)에도 `guildId`가 없다.
- **값이 비어 있으면**: Delivery를 만들지 않고 `log.warn`만 남긴다. Fail-Fast가 아니며 공고·프로그램·문의 등록 API는 정상 성공한다(요구사항 §48, Collector Webhook과 같은 방식).

### 5.2 도메인별 채널 결정

| 도메인 | 입력 | 해석 |
| --- | --- | --- |
| Job | `Job.discordChannelKey`(논리 Key, 신규 요청 필드) | 미지정이면 `default-job-channel-key`. 허용 목록에 없는 Key는 **등록·수정 시점에 400으로 거부** |
| Program | `Program.discordChannelId`(원시 Snowflake, 기존 계약) | 허용 목록의 `channel-id` 집합에 포함되는지 검증. 미포함이면 **400으로 거부** |
| Inquiry | 없음 | `inquiry-channel-key` 고정. 사용자가 선택하지 않는다(요구사항 §9) |

Program의 검증은 게시 시점(`validateProgramForPublish`)이 아니라 **값을 받는 시점**에 수행한다. 게시까지 미루면 잘못된 채널 ID가 DRAFT로 저장되어 나중에 게시가 막힌다.

### 5.3 Mention Role

`JOB_PUBLISHED`/`PROGRAM_PUBLISHED`(CREATE)에만 전달한다. Job은 `targetGrade`(단수, nullable), Program은 `program_target_grades`(복수)를 Role ID로 변환한다. 학년이 지정되지 않았으면 빈 목록(전 학년 대상이므로 특정 학년만 Mention하지 않는다).

Mention이 CREATE에만 실리는 것은 선행 PR이 이미 타입으로 강제한다 — `DiscordPatchCommand`에 `roleIds` 필드가 없다.

---

## 6. 기존 Discord 자산 교체

### 6.1 Program (Breaking Change)

`domain.program.entity.type.DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED)`와 `DiscordDeliveryResult`를 **제거**하고, 세 응답 DTO에서 `discordDelivery` 필드를 **뺀다**. Discord 상태는 §7의 조회 API로만 제공한다(결정 9).

```text
변경 전  ProgramCreateResponse.discordDelivery = { status: "SKIPPED", failureReason: null }
변경 후  (필드 없음)  →  GET /api/v1/admin/programs/{id}/discord
```

이유는 두 가지다.

1. **순환 의존을 구조적으로 차단한다.** `notification`이 이미 `program.query`를 소비하므로, Program 응답이 Discord 상태를 읽으려면 `program → notification` 역방향이 필요해져 `ModularityTest`(`modules.verify()`)가 순환으로 실패한다. 필드를 없애면 그 의존 자체가 생기지 않는다.
2. **그 시점에는 담을 값이 없다.** 등록·수정 응답은 `AFTER_COMMIT` **이전에** 만들어져 Delivery Row가 아직 존재하지 않는다. 무엇을 넣어도 고정값이 되고, 이는 지금의 `SKIPPED` 고정과 다를 바 없다. 요구사항 §48이 금지한 "가짜 상태 반환"에 가까워지는 방향이다.

**Breaking Change다** — 값 집합 변경이 아니라 **필드 제거**다. 요구사항 §58대로 PR 본문에 명시하고 Swagger를 갱신한다. 현재 이 필드는 항상 `SKIPPED`만 반환해 실질 정보가 없었으므로 소비 측이 잃는 정보는 없다.

### 6.2 Inquiry

Program과 같은 이유로 Inquiry 응답에서도 Discord 상태를 **제거**하고, `InquiryDiscordDeliveryStatus` Enum과 `InquiryDiscordDeliveryQueryPort`(항상 `PENDING`을 반환하던 Module 내부 계약)를 함께 삭제한다. 상태는 `GET /api/v1/admin/inquiries/{id}/discord`로만 제공한다.

`Inquiry.discordMessageId`/`discordErrorMessage`(`@Deprecated`)와 `Program.discordMessageId`는 `discord_deliveries`가 소유하므로 **제거 대상**이다. Column 삭제는 되돌리기 어려우므로 2단계로 나눈다 — 이번 PR은 Entity Mapping만 제거하고, Column DROP은 다음 Migration으로 미룬다.

`Program.discordChannelId`는 **유지한다.** 여전히 클라이언트가 채널을 지정하는 입력값이다.

---

## 7. 상태 조회·수동 재시도 API

Notification 요구사항 §15 기준. **Controller는 `domain.notification.controller`에 둔다**(결정 7).

```text
GET  /api/v1/admin/jobs/{jobId}/discord
GET  /api/v1/admin/programs/{programId}/discord
GET  /api/v1/admin/inquiries/{inquiryId}/discord
POST /api/v1/admin/jobs/{jobId}/discord/retry
POST /api/v1/admin/programs/{programId}/discord/retry
```

응답은 요구사항 §15의 필드를 따른다(`status`, `automaticRetryCount`, `maxAutomaticRetryCount`, `manualRetryCount`, `maxManualRetryCount`, `canRetry`, `failureCode`, `failureReason`, `messageId`, `channelId`, `requestedAt`, `lastSyncedAt`).

### 권한 (요구사항 §37 — 임의 확대 금지)

| 대상 | 권한 |
| --- | --- |
| Job 조회/재시도 | **Role만 검사**(TEACHER·DEVELOPER) — 결정 10 |
| Program 조회/재시도 | 등록자 또는 담당 교사, 그리고 DEVELOPER |
| Inquiry 조회 | DEVELOPER |
| Inquiry 재시도 | **API를 만들지 않는다** — 명세에 없다 |

`SecurityConfig`의 경로 규칙(`admin/jobs/**`, `admin/programs/**`는 TEACHER·DEVELOPER)은 Role만 거른다. Program은 **소유권(등록자/담당 교사) 검증을 Service 계층이 별도로 수행**한다 — `requireManager`와 동일한 판정이 필요하므로, `ProgramManagerQueryPort`(결정 11)로 그 판정을 공개하고 `DiscordDeliveryAdminController`가 소비한다(방향은 기존과 같은 `notification → 도메인`).

`Inquiry` 재시도가 없으므로 `/api/v1/admin/inquiries/{id}/discord`는 조회만 등록한다. 새 Endpoint는 `OpenApiDocumentationTest`를 통과해야 한다.

### 결정 10 — Job은 Role만 검사한다(구현 중 확정)

`JobErrorCode`의 기존 주석대로 Job은 "담당자" 기준 자체가 아직 확정되지 않아 `requireManager` 같은 소유권 판정이 없다. 요구사항 §37 원문은 Job에도 "등록자 또는 담당 교사"를 요구하지만, 이를 만족하려면 이번 PR 범위 밖의 새 Job 소유권 개념을 만들어야 한다. AskUserQuestion으로 확인한 결과 **Role 검사만 수행**하기로 확정했다 — 기존 Job 관리자용 Endpoint들이 이미 Role만으로 판정하는 관례와 일치한다. Job 소유권 개념이 생기면 그때 §37과 동일한 수준으로 좁힌다.

### 결정 11 — `ProgramManagerQueryPort` 신설

Program Discord 상태 조회·재시도는 `requireManager`와 동일한 소유권 판정이 필요하지만, 기존 `ProgramDiscordPayloadQueryPort`는 Bot Payload 조립 전용으로 문서화되어 있어 용도가 다르다. 그 계약을 재사용하는 대신 **별도 Port**(`domain.program.query.ProgramManagerQueryPort`, `@NamedInterface`)를 새로 만들었다. `findById(programId): ProgramManagerSnapshot?`만 제공하고 `ProgramManagerSnapshot(programId, createdByMemberId, managerMemberId)`으로 소유권 판정에 필요한 최소 필드만 노출한다.

---

## 8. 테스트 계획

### `src/test`

```text
event/JobDiscordEventListenerTest.kt          Action → Template 매핑, 채널 미설정 시 미생성
event/ProgramDiscordEventListenerTest.kt      동일
event/InquiryDiscordEventListenerTest.kt      INQUIRY_CREATED 생성, 고정 채널 사용
global/discord/DiscordChannelResolverTest.kt  허용 목록 검증, 미등록 Key 거부, 학년 → Role 변환,
                                              application.yaml 기본 설정(채널 Key·기본 Key 선언)
job/service/JobServiceTest.kt                 (수정) 상태별 Event 발행/미발행, DRAFT 수정·삭제 시
                                              미발행, 허용되지 않은 채널 Key 거부
program/service/impl/ProgramServiceImplTest.kt (수정) 동일 + discordChannelId 허용 목록 거부
controller/DiscordDeliveryAdminControllerTest.kt   조회·재시도 성공, 권한 없음 403, 없는 대상 404
OpenApiDocumentationTest                       기존 Test가 새 Endpoint를 자동 검사
ModularityTest / PackageArchitectureTest       순환 의존 검증 (이번 PR의 핵심 위험)
```

### `src/integrationTest`

```text
원본 Commit + Discord 예약 실패 시 원본 데이터가 유지된다   (Job/Program/Inquiry 각 1건, 실제 구현)
```

`InquiryAnswerNotificationFailureIntegrationTest`와 같은 방식(실제 PostgreSQL, 실패를 강제하는
Mock)으로 3개 도메인 각각에 대응하는 `*DiscordEventIntegrationTest`를 추가했다. 세 Test는 원본
데이터 유지뿐 아니라 **Listener가 실제로 실행됐는지**(`verify` + Template·targetId·channelId 확인)를
함께 단언한다 -- 이 검증이 없으면 Listener가 아예 동작하지 않아도 똑같이 통과해 "AFTER_COMMIT
격리"를 "Listener 미실행"과 구분할 수 없다.

다음 두 항목은 **새로 추가하지 않고 기존 커버리지로 충분하다고 판단**했다.

- **원본 Transaction Rollback 시 Delivery가 생기지 않는다** — `@TransactionalEventListener
  (AFTER_COMMIT)` 자체가 Spring Framework의 표준 보장이고, `create()`에는 Event 발행 이후
  Rollback을 유발할 코드 경로가 없어 우리 코드가 이 보장을 깨뜨릴 지점이 없다.
- **같은 Event가 두 번 도착해도 Delivery Row가 하나만 생긴다** — 선행 PR(#95)의
  `DiscordDeliveryRepositoryIntegrationTest`(`idempotency_key` UNIQUE 위반 실제 검증)와
  `DiscordDeliveryServiceImplTest`("이미 예약된 Delivery가 있으면...")가 이미 이 계약을
  Repository/Service 양쪽에서 검증한다. Listener는 Command를 만들어 `enqueue()`에 넘길 뿐이라
  중복 방지 로직 자체를 갖지 않는다.

Bot 호출은 세 Test 모두 `DiscordDeliveryService`를 `@MockitoBean`으로 대체해 우회한다. 실제
Discord·실제 Bot을 호출하지 않는다.

---

## 9. DECISION_REQUIRED

| # | 항목 | 이번 PR의 처리 |
| --- | --- | --- |
| 1 | 허용 채널 4개의 이름·용도·Snowflake | 임시 Key 3개로 구조만 만든다. 확정되면 설정만 추가 |
| 2 | 학년별 Role ID | 동일 |
| 3 | Program 자동 마감 Scheduler | 이번 범위 밖. `PROGRAM_CLOSED` 미연결(§2) |
| 4 | `Program.discordMessageId`·`Inquiry.discord*` Column DROP | 이번 PR은 Mapping만 제거, DROP은 다음 Migration |

가장 큰 설계 위험이던 "Program·Inquiry가 Discord 상태를 읽는 방향"은 결정 9로 해소했다 — 응답에서 필드를 제거해 역방향 의존 자체를 만들지 않는다.

---

## 10. 이번 범위에서 제외

```text
Collector Discord Webhook 이관              요구사항 §34, 별도 Issue
Program 자동 마감 Scheduler                 Program 도메인 기능 추가
PROGRAM_CLOSED 연결                         §2 (자동 마감 Scheduler 선행)
Inquiry 수동 재시도 API                     명세에 없다(요구사항 §37)
Discord Column 물리 DROP                    다음 Migration
모바일 Push                                 요구사항 §63
```
