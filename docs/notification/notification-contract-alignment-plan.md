# 인앱 알림 API Notion 계약 정렬 및 삭제·대상 판정 보강 계획

`docs/notification/사용자-facing Notification 도메인 구현.md`를 근거로 Planning Interview를 거쳐 확정한 구현 명세다.
원본 문서는 "Personal Notification이 아직 없을 수 있다"를 전제로 작성됐으나, 조사 결과 그 전제가 사실이 아니어서
범위를 재정의했다. 이 문서가 이번 작업의 기준이다.

---

## 1. 조사 결과 — 원본 문서 전제 정정

### 1.1 P0 범위는 이미 `develop`에 있다

| 원본 문서 P0 항목 | `develop` 현황 | 판정 |
| --- | --- | --- |
| Personal Notification Entity/Schema | `Notification` + `notifications` Table (V2 생성 → V16 정렬) | MATCH |
| Repository | `NotificationRepository` (동적 Filter, Bulk mark-read) | MATCH |
| 생성 공개 Contract | `NotificationService.create(NotificationCreateCommand)` (`REQUIRES_NEW`) | MATCH |
| 목록 / Filter / Pagination | `GET /api/v1/notifications?isRead&type&page&size` | MATCH (계약 이름만 다름) |
| unreadCount | `GET /api/v1/notifications/unread-count` | MATCH (위치만 다름) |
| 단건 읽음 | `PATCH /api/v1/notifications/{id}/read` | MATCH (형태만 다름) |
| 전체 읽음 | `PATCH /api/v1/notifications/read-all` | MATCH (형태만 다름) |
| 삭제 | 없음 (`deleted_at` Column과 방어 Query만 존재) | **GAP** |
| targetAvailable Architecture | `NotificationTargetResolver` + Job/Program Query Port | MATCH (지원 대상 부족) |
| 본인 소유권 검증 | `NotificationAccessDeniedException` (403) | MATCH |
| Unit / Controller / Integration / Architecture / Swagger | 전부 존재 | MATCH |
| Discord Delivery와의 분리 | Entity·Service·Table 모두 분리되어 있음 | MATCH |
| (원본 문서 P1) Event 연동 | Inquiry / JobApplication / MemberApproval / ProgramDeleted Listener 존재 | 일부 MATCH |

### 1.2 병렬 작업 관련 전제도 이미 지났다

- **Issue #171**: 이미 Merge됨(PR #180, `origin/develop` 최신 Commit `210cf90`). Job/Search 충돌 우려는 해소됐다.
- **Portfolio**: 여전히 `entity`/`repository`만 존재하고 Service 계층이 없다. 이번 범위에서 건드리지 않는다.
- **Push**: 저장소에 관련 코드가 전혀 없다(`push`/`fcm`/`apns`/`device_token` 검색 결과 0건).
- **Notification 관련 OPEN Issue/PR**: 없음. 신규 Issue를 만든다.

### 1.3 실제 GAP은 "계약 불일치"다 (CONTRACT_MISMATCH)

현재 구현은 `docs/notification/Notification_domain_development.md` §4를 정확히 따르고 있고,
원본 문서가 인용한 Notion 계약은 그와 다르다.

| 항목 | Notion 계약 | `develop` 구현 |
| --- | --- | --- |
| 목록 Filter | `unreadOnly: Boolean`, `notificationType` | `isRead: Boolean?`, `type` |
| 항목 필드 | `notificationType`, `read` | `type`, `isRead` (+ `readAt`, `deepLink`) |
| unreadCount | 목록 응답에 포함 | 별도 Endpoint |
| 읽음 처리 | `PATCH /notifications/read` + `{scope, notificationId}` | `PATCH /{id}/read`, `PATCH /read-all` |
| 읽음 응답 | `{unreadCount, updatedCount}` | `{notificationId, isRead, readAt}` / `{updatedCount, readAt}` |
| 삭제 | `DELETE /notifications/{notificationId}` | 없음 |

`AGENTS.md` 우선순위(3. Notion 확정 요구사항 > 4. 저장소 구현)에 따라 **Notion 계약으로 정렬**한다.

---

## 2. 이번 작업의 범위

### 2.1 포함

1. 목록 API를 Notion 계약으로 정렬 (`unreadOnly`, `notificationType`, `read`, 응답 내 `unreadCount`)
2. 읽음 처리 API를 `PATCH /api/v1/notifications/read` + `{scope, notificationId}` 단일 Endpoint로 통합
3. `DELETE /api/v1/notifications/{notificationId}` 신규 구현 (Soft Delete)
4. `NotificationTargetResolver`에 `INQUIRY`, `JOB_APPLICATION` 판정 추가 (+ 해당 Domain에 Query Port 신설)
5. Swagger/OpenAPI 문서 갱신, Test 전면 갱신

### 2.2 제외 (Follow-up Issue로 분리)

- Push Device 등록·해제·설정 조회·변경, FCM/APNs Provider (원본 문서 §24·§45, `Notification_domain_development.md` §18 Phase 4)
- producer가 없는 `NotificationType` 11개의 Event 연동 (`JOB_PUBLISHED`, `PROGRAM_*`, `SYSTEM` 등)
- `PORTFOLIO_REQUEST` / `MEMBER_APPROVAL` Target Resolver
- Discord Delivery 리팩터링
- Job / Search / Recommendation / Portfolio 파일 수정
- 대량 알림 성능 최적화, Notification Retention/Cleanup

---

## 3. 확정된 설계 결정

| # | 결정 | 근거 |
| --- | --- | --- |
| D1 | Notion 계약으로 정렬 + 누락분 구현 | `AGENTS.md` 우선순위 3 > 4 |
| D2 | 기존 Endpoint를 **완전 교체**(병행 유지 없음) | 현재 API를 사용하는 클라이언트가 없음 |
| D3 | `deepLink`, `readAt`은 **유지**(Notion superset) | 계산값이고 정보 손실을 막는다. 추가 필드는 클라이언트에 무해 |
| D4 | `NotificationReadScope = { SINGLE, ALL }` | Notion 정의 |
| D5 | `scope=ALL` + `notificationId` 전달 → **무시** / `scope=SINGLE` + `notificationId=null` → **400** | `JobApplicationAdminActionRequest`(무시) + `MemberApprovalServiceImpl.reject`(전용 예외 400) 선례 |
| D6 | 삭제는 **Soft Delete** (`deleted_at`) | Column이 이미 존재하고 모든 조회 Query가 `deletedAt IS NULL`을 걸고 있음. Migration 불필요 |
| D7 | DELETE 응답은 **204 No Content** | `removeBookmark`, `removeExclusion`, `deleteCompany`와 동일한 저장소 관례 |
| D8 | `GET /unread-count` **유지** | 204 DELETE 이후 Badge 갱신 경로가 필요. 목록 한 Page를 통째로 받지 않아도 된다 |
| D9 | 읽음 응답에 **nullable `readAt` 포함** | `{unreadCount, updatedCount, readAt}` |
| D10 | Resolver에 `INQUIRY`, `JOB_APPLICATION` 추가. `MEMBER_APPROVAL`은 현행 유지 | 앞의 둘은 이동할 상세 화면이 있고, 승인 결과는 없다 |
| D11 | Event 연동은 제외 | 수신자 정책(전체 학생 vs 관심 등록자)이 미정이라 지금 구현하면 추측이 된다 |
| D12 | 별도 Worktree 없이 현재 저장소에 Branch 생성 | 병렬 작업이 없고 작업 디렉터리가 깨끗하다 |
| D13 | 타인 알림 접근은 **403** 유지 | Notification Domain의 기존 정책(`NOTIFICATION_ACCESS_DENIED`). 변경하지 않는다 |

---

## 4. 정렬 후 최종 API 계약

공통: `Authorization: Bearer {accessToken}` 필수. `SecurityConfig`가 `/api/v1/notifications/**`를 `authenticated`로 이미 열어 두었으므로 Security 설정은 건드리지 않는다. 모든 응답은 `ApiResponse<T>`(`success` / `data` / `meta.requestId`)로 감싼다.

### 4.1 `GET /api/v1/notifications`

Query Parameter:

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `unreadOnly` | `Boolean` (기본 `false`) | `true`면 읽지 않은 알림만 |
| `notificationType` | `NotificationType?` | 종류 Filter |
| `page` | `Int` (기본 0) | |
| `size` | `Int` (기본 20, 최대 100) | `WebPageableConfig`의 전역 상한 |

`sort`는 무시한다(정렬은 Query가 `createdAt DESC, id DESC`로 고정).

응답 `data`:

```json
{
  "content": [
    {
      "notificationId": 1,
      "notificationType": "PROGRAM_PUBLISHED",
      "title": "...",
      "content": "...",
      "targetType": "PROGRAM",
      "targetId": 123,
      "targetAvailable": true,
      "targetUnavailableReason": null,
      "deepLink": "/programs/123",
      "read": false,
      "readAt": null,
      "createdAt": "2026-08-20T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1,
  "first": true,
  "last": true,
  "unreadCount": 5
}
```

**`unreadCount` 의미**: Filter와 무관한 "현재 사용자 전체 미읽음 수"다(Header Badge 용도, 원본 문서 §19).
`notificationType=JOB`으로 걸러도 값이 달라지지 않는다. Page content를 세지 않고 별도 COUNT Query를 쓴다.
Swagger `description`에 이 의미를 명시한다.

**`unreadOnly` 매핑**: `unreadOnly=true` → Repository의 `isRead=false`, `unreadOnly=false`(또는 미전달) → `isRead=null`(전체).
즉 "읽은 것만" 조회는 계약상 불가능해진다(Notion 계약이 Boolean Flag이므로 의도된 동작이다).

### 4.2 `GET /api/v1/notifications/unread-count`

변경 없음. 응답 `{ "unreadCount": 5 }`.

### 4.3 `PATCH /api/v1/notifications/read`

Request Body:

```json
{ "scope": "SINGLE", "notificationId": 1 }
```

| scope | notificationId | 동작 |
| --- | --- | --- |
| `SINGLE` | 필수 | 해당 알림 1건 읽음 처리 |
| `SINGLE` | `null` | **400** `NOTIFICATION_ID_REQUIRED` |
| `ALL` | 무시 | 본인의 읽지 않은 알림 전체 Bulk Update |

응답 `data`:

```json
{ "unreadCount": 4, "updatedCount": 1, "readAt": "2026-08-20T10:05:00" }
```

- `updatedCount`: 실제로 상태가 바뀐 건수. 이미 읽은 알림을 `SINGLE`로 다시 호출하면 `0`(멱등).
- `readAt`: `SINGLE`이면 해당 알림의 읽은 시각(이미 읽은 건은 **처음** 읽은 시각 유지), `ALL`이면 이번 갱신에 사용한 시각. `ALL`이고 갱신 0건이면 `null`. 이 scope별 의미 차이를 Swagger `@Schema`에 명시한다.
- `unreadCount`: 처리 **이후** 값.

오류:

| 상황 | Status | Error Code |
| --- | --- | --- |
| 알림 없음 / 이미 삭제됨 | 404 | `NOTIFICATION_NOT_FOUND` |
| 타인의 알림 | 403 | `NOTIFICATION_ACCESS_DENIED` |
| `SINGLE`인데 id 없음 | 400 | `NOTIFICATION_ID_REQUIRED` (신규) |

### 4.4 `DELETE /api/v1/notifications/{notificationId}`

- 성공: **204**, 본문 없음.
- 본인 알림만 삭제 가능. 타인 알림 → 403 `NOTIFICATION_ACCESS_DENIED`.
- 없거나 이미 삭제된 알림 → 404 `NOTIFICATION_NOT_FOUND` (멱등 아님, 기존 DELETE 관례와 동일).
- Soft Delete: `deleted_at`을 채운다. 삭제된 알림은 목록·`unreadCount`·읽음 처리 대상에서 모두 빠진다(기존 Query가 이미 `deletedAt IS NULL`을 걸고 있으므로 누락 위험 없음).
- 읽지 않은 알림을 삭제하면 `unreadCount`가 자연히 줄어든다(별도 보정 로직 불필요).

---

## 5. Target Availability 확장

### 5.1 판정 규칙

`Inquiry`와 `JobApplication` 모두 Soft Delete Column이 없고, 알림 수신자가 곧 리소스 소유자다
(문의 답변 알림 → 문의 작성자, 지원서 검토 알림 → 지원 학생). 따라서 규칙이 단순하다.

| targetType | available | reason | deepLink |
| --- | --- | --- | --- |
| `INQUIRY` (존재 + 작성자 본인) | `true` | `null` | `/inquiries/{id}` |
| `INQUIRY` (존재하나 타인) | `false` | `FORBIDDEN` | `null` |
| `INQUIRY` (없음) | `false` | `DELETED` | `null` |
| `JOB_APPLICATION` (존재 + 지원자 본인) | `true` | `null` | `/job-applications/{id}` |
| `JOB_APPLICATION` (존재하나 타인) | `false` | `FORBIDDEN` | `null` |
| `JOB_APPLICATION` (없음) | `false` | `DELETED` | `null` |

- 이 작업으로 `NotificationTargetUnavailableReason.FORBIDDEN`과 `resolveAll(..., viewerMemberId)` 파라미터가
  **처음 실제로 사용된다**. 현재 `viewerMemberId`에 붙은 `@Suppress("UNUSED_PARAMETER")`를 제거한다.
- Role은 필요 없다. 개발자가 모든 문의를 볼 수 있는 규칙(`InquiryController.getInquiry`)은 존재하지만,
  알림 수신자는 언제나 작성자 본인이라 소유자 판정만으로 충분하다. 없는 권한 판정을 지어내지 않는다.
- `JobApplication`의 `DRAFT` 상태는 걸러내지 않는다. 알림은 검토 결과에서만 발생하고, 학생은 본인의 DRAFT를 볼 수 있다.
- `MEMBER_APPROVAL`, `PORTFOLIO_REQUEST`는 현행 유지(`available=false`, `reason=null`).

### 5.2 신설 Query Port

기존 `JobNotificationTargetQueryPort` / `ProgramNotificationTargetQueryPort`와 **동일한 방향과 형태**를 따른다.
Interface는 소유 Domain이 공개하고 `notification`이 참조한다(반대로 두면 Modulith 순환 의존이 생긴다).
목록 한 Page가 최대 100건이므로 배치 조회로 만든다(N+1 방지).

```text
domain/inquiry/query/InquiryNotificationTargetQueryPort.kt        (@NamedInterface)
domain/inquiry/service/impl/InquiryNotificationTargetQueryPortImpl.kt
domain/application/query/JobApplicationNotificationTargetQueryPort.kt   (@NamedInterface)
domain/application/service/impl/JobApplicationNotificationTargetQueryPortImpl.kt
```

각 Port는 `findAllByIds(ids: Set<Long>): Map<Long, Snapshot>`을 제공하고, Snapshot은 소유자 판정에 필요한
`authorMemberId` / `applicantMemberId`만 담는다. 존재하지 않는 id는 결과 Map에서 빠진다(기존 Port와 동일한 관례).
판정 Enum은 돌려주지 않는다 — 그러면 소유 Domain이 `notification`의 타입을 알아야 해서 순환이 생긴다.

**순환 의존 확인**: `notification`은 이미 `inquiry.event.InquiryAnsweredEvent`와
`application.event.JobApplicationReviewedEvent`를 참조하므로 `notification → inquiry`, `notification → application`
방향 의존이 이미 존재한다. 새 의존 방향이 생기지 않는다.

---

## 6. 변경 파일

### 6.1 수정

```text
domain/notification/controller/NotificationController.kt
domain/notification/dto/NotificationSummaryResponse.kt        # type→notificationType, isRead→read
domain/notification/dto/NotificationListResponse.kt           # unreadCount 추가
domain/notification/dto/NotificationReadResponse.kt           # {unreadCount, updatedCount, readAt}
domain/notification/service/NotificationService.kt
domain/notification/service/impl/NotificationServiceImpl.kt
domain/notification/service/NotificationTargetResolver.kt
domain/notification/service/NotificationDeepLink.kt
domain/notification/repository/NotificationRepository.kt
domain/notification/entity/Notification.kt                    # softDelete() 추가, deletedAt KDoc 갱신
domain/notification/exception/NotificationErrorCode.kt        # NOTIFICATION_ID_REQUIRED 추가
```

### 6.2 신규

```text
domain/notification/dto/NotificationReadRequest.kt
domain/notification/dto/NotificationReadScope.kt              # JobApplicationAdminAction과 같이 dto 패키지
domain/notification/exception/NotificationIdRequiredException.kt
domain/notification/service/impl/NotificationResponseMapping.kt   # 목록 응답 조립(detekt TooManyFunctions 회피)
domain/inquiry/query/InquiryNotificationTargetQueryPort.kt
domain/inquiry/service/impl/InquiryNotificationTargetQueryPortImpl.kt
domain/application/query/JobApplicationNotificationTargetQueryPort.kt
domain/application/service/impl/JobApplicationNotificationTargetQueryPortImpl.kt
```

### 6.3 삭제

```text
domain/notification/dto/NotificationReadAllResponse.kt        # 통합 응답으로 대체
```

### 6.4 건드리지 않는 것

- **`V16__align_notifications_for_in_app_api.sql`**: 이미 병합된 Migration이라 수정 금지(`.claude/rules/spring-boot.md`).
  "deleted_at은 이번 범위에서 사용하지 않는다"라는 설명이 낡게 되지만, 정정은 `Notification` Entity의 KDoc에서 한다.
  Schema 변경이 없으므로 **신규 Migration도 만들지 않는다**.
- `SecurityConfig` (이미 필요한 규칙이 있다), Discord Delivery 전체, `domain/job`, `domain/search`,
  `domain/recommendation`, `domain/portfolio`, 기존 Event Listener 4종.

---

## 7. Test 계획

### 7.1 갱신

| 파일 | 내용 |
| --- | --- |
| `NotificationControllerTest` | Query Parameter 이름, 응답 필드명(`notificationType`/`read`), `unreadCount`, 통합 읽음 Endpoint, DELETE 3종(성공 204 / 404 / 403), `SINGLE`+id 없음 400 |
| `NotificationServiceImplTest` | `unreadOnly` 매핑, 응답 내 `unreadCount`가 Filter와 무관함, `read(scope=SINGLE/ALL)` 멱등·`updatedCount`, `delete` 소유권·재삭제 404 |
| `NotificationTargetResolverTest` | `INQUIRY`/`JOB_APPLICATION` × (본인 / 타인 / 없음) 6 케이스, Domain당 Port 호출 1회(N+1 방지) |
| `DomainEventNotificationIntegrationTest` | 응답 필드명 변경 반영 |

### 7.2 신규

- `NotificationInboxPersistenceIntegrationTest` (`src/integrationTest`): Soft Delete 후 목록·`unreadCount`·읽음 처리에서
  모두 제외되는지, `markAllAsRead` Bulk Update가 삭제된 Row와 다른 사용자를 건드리지 않는지, 사용자 간 격리,
  `createdAt` 동일 시 `id DESC` Tie-breaker, Filter와 무관한 `unreadCount`, Pagination.

### 7.3 데이터 격리 (필수)

사용자 A / B 각각 알림을 두고:

- A의 목록에 B의 알림이 없다
- A가 B의 알림을 읽음 처리하면 403이고 B의 알림은 변하지 않는다
- A가 B의 알림을 삭제하면 403이고 B의 알림은 남는다
- A의 전체 읽음이 B의 알림을 건드리지 않는다
- Target Availability를 B의 권한으로 계산하지 않는다 (B 소유 문의 → A에게는 `FORBIDDEN`)

### 7.4 검증 명령

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat test --tests "*Notification*"
.\gradlew.bat test --tests "*ModularityTest*"
.\gradlew.bat test --tests "*PackageArchitectureTest*"
.\gradlew.bat test --tests "*ModuleDocumentationTest*"
.\gradlew.bat test --tests "*OpenApiDocumentationTest*"
.\gradlew.bat integrationTest        # Docker Desktop 실행 필요
.\gradlew.bat clean test build
```

Docker Desktop이 현재 실행 중이 아니다. `integrationTest`는 Docker를 켠 뒤 실제로 실행해 통과시킨다.

---

## 8. Git 절차

1. 신규 Issue 생성: `[FEAT] 인앱 알림 API를 Notion 계약으로 정렬하고 삭제·대상 판정 보강`
   - 본문에 §1.3 CONTRACT_MISMATCH 표와 §2 범위, Follow-up 목록을 기록한다.
   - Label: 저장소에 실제 존재하는 것만 `gh label list`로 확인 후 부여. 시작 시 `📝 ready` → `🚧 in progress`.
2. `origin/develop` 최신화 후 Branch 생성: `feature/{issue-number}-notification-contract-alignment`
3. Commit 분할(과도하게 쪼개지 않는다):
   - `feat: 인앱 알림 조회·읽음 API를 Notion 계약으로 정렬`
   - `feat: 인앱 알림 삭제 API 구현`
   - `feat: 문의·지원서 알림 대상 접근 가능 여부 판정 추가`
   - `test: 인앱 알림 계약 정렬 검증 보강`
4. Draft PR (Base `develop`), 본문에 `Closes #{issue-number}` + Breaking Change 명시 + 실제 실행한 검증 결과만 기재.
5. PR 생성 후 `gh pr checks`로 CI 결과 확인. Issue Label을 `👀 review`로 전환.
6. **Merge하지 않는다. Force Push하지 않는다.**

---

## 9. Follow-up Issue (생성 완료)

| Issue | 내용 | 선행 조건 |
| --- | --- | --- |
| #189 | 푸시 기기 등록·해제 및 푸시 알림 설정 API | 없음 (Notion에 계약 존재) |
| #190 | FCM/APNs Push Provider 연동 | #189 |
| #191 | producer 없는 `NotificationType` 11개 Event 연동과 수신자 정책 확정 | 수신자 정책 결정 |
| #192 | `PORTFOLIO_REQUEST`·`MEMBER_APPROVAL` 대상 접근 판정 | Portfolio Service 계층 / 승인 결과 화면 |
| #193 | 인앱 알림 중복 생성 방지(Idempotency) | Unique 범위 결정 |
| #194 | 인앱 알림 보관 기한 및 정리 정책 | 보관 정책 결정 |

#191(대량 Fan-out)을 켜기 전에 #194(정리 정책)가 있어야 `notifications` Table이 무한정 커지지 않는다.
#193도 Fan-out 배치 재실행 안전성과 연결된다.

---

## 10. DECISION_REQUIRED / 가정

| # | 항목 | 처리 |
| --- | --- | --- |
| A1 | `NotificationReadScope = { SINGLE, ALL }` | 사용자 확인값. Notion과 다르면 구현 전 정정 |
| A2 | `deepLink` 경로 `/inquiries/{id}`, `/job-applications/{id}` | 프론트 라우트 규약이 미확정이라 API Resource 이름을 그대로 따른다(`/jobs/{id}`, `/programs/{id}`와 동일한 방식). 상대 경로 유지 |
| A3 | `unreadOnly=false`일 때 "읽은 것만" 조회 불가 | Notion 계약이 Boolean Flag이므로 의도된 축소. 클라이언트가 필요하면 계약 변경 필요 |
| A4 | Notion에 `GET /unread-count`와 `deepLink`/`readAt`이 없더라도 superset으로 유지 | 사용자 확인값 |
| A5 | 타인 알림 접근 시 403 (Recommendation Domain은 404로 숨김) | Notification Domain의 기존 정책 유지. Domain 간 불일치는 별도 논의 대상 |
| A6 | `NOTIFICATION_ID_REQUIRED` Error Code 이름 | Notion §20 Error Code 목록에 대응 값이 있으면 그 이름을 우선한다 |
