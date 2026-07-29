# AI 개발 시나리오 Static Audit

이 저장소에는 대화 History를 공유하지 않는 독립 Agent를 실행할 별도 Subagent/Worktree 실행 환경이 이번 세션에서 명시적으로 요청되지 않았다. 절대 안전 규칙("Sample 기능을 Production Source에 남기지 않는다", "임시 Worktree를 사용하는 경우 사용자의 기존 Worktree나 Branch에 영향을 주지 않는다")과 실제 실행 비용을 고려해, 4개 시나리오를 **실제 실행하지 않고** 현재 저장소 문서(`AGENTS.md`, `CLAUDE.md`, `docs/ai/*`, `.claude/*`, `.codex/*`, `docs/architecture/modularity.md`, `docs/development/*`)만으로 예상 동작을 분석하는 Static Audit으로 수행했다. **NOT_EXECUTED**(실제 Agent 실행 없음)를 전제로 한 평가다.

## 평가 기준

```text
PASS       현재 문서만으로 올바른 행동을 충분히 유도할 수 있음
PARTIAL    대체로 올바르게 유도되지만 놓치기 쉬운 지점이 있음
FAIL       현재 문서가 부족해 잘못된 행동을 유도하거나 막지 못함
```

## 시나리오 A — "공고 북마크 등록 기능 만들어줘"

| 평가 항목 | 결과 | 근거 |
| --- | --- | --- |
| Context Discovery | PASS(이번 PR로 개선) | `docs/ai/workflow.md` 1단계에 "Domain 기능이면 Notion 확인" 규칙을 이번 PR에서 추가했다. 이전에는 사용자가 Notion을 직접 언급하지 않으면 Agent가 Notion을 확인할 의무가 문서에 없었다(**FAIL 후보였던 항목**). |
| Scope Control | PASS | `AGENTS.md`의 "작업 범위", `.claude/rules/repository-workflow.md`가 Issue 기반 작업과 최소 범위 변경을 강하게 요구한다. |
| Package Placement | PASS(이번 PR로 개선) | `docs/architecture/modularity.md`의 신규 "Domain Module 내부 구조(DDD)" Section과 `docs/audit/notion-repository-sync.md`의 Domain Map으로 "북마크는 Job Domain의 기능"임을 확인할 근거가 생겼다. |
| Module Boundary | PASS | Spring Modulith 원칙(다른 Module 내부 구현 참조 금지)이 `docs/architecture/modularity.md`에 명확하다. |
| Persistence Safety | PASS | `docs/development/persistence.md`, `.claude/rules/spring-boot.md`가 Migration 신규 추가·`ddl-auto=validate`를 명확히 요구한다. |
| API Contract | PARTIAL | 현재 저장소 Contract(`ApiResponse`/`ErrorResponse`)를 따르는 것이 맞지만, Notion API 명세서(`success`/`meta.requestId`)를 우선 발견할 경우 혼란 가능성이 있었다. 이번 PR에서 `AGENTS.md`에 "3번과 4번/9번이 다르면 임의 판단하지 않고 CONTRACT_MISMATCH로 보고"를 명시해 리스크를 낮췄다. |
| Secret Safety | PASS | 해당 없음(외부 Secret 불필요). |
| Test Selection | PASS | `docs/ai/testing-policy.md`가 Unit/Integration 구분을 명확히 요구한다. |
| Documentation | PASS | `docs/ai/coding-conventions.md`의 "Documentation Convention" 상당 부분이 이미 존재한다(API/Enum/환경변수 변경 시 문서 갱신 요구). |
| Git Workflow | PASS | `docs/ai/git-conventions.md`, `.claude/rules/git-and-github.md`가 충분히 구체적이다. |
| Completion Verification | PASS | `docs/ai/completion-policy.md`가 검증 없는 완료 선언을 명확히 금지한다. |

## 시나리오 B — "새 외부 채용 API 연동해줘"

| 평가 항목 | 결과 | 근거 |
| --- | --- | --- |
| Secret Safety | PASS | `docs/ai/security-policy.md`, `.claude/rules/security.md`가 Secret Literal 금지, 환경변수화를 강하게 요구한다. |
| Context Discovery | PASS(이번 PR로 개선) | Collector Domain의 실제 요구사항(사람인/고용24, 매일 1회, 출처별 실패 분리)은 Notion PRD 6.6/11에 있다. 시나리오 A와 동일하게 이번 PR의 Notion 확인 규칙으로 개선됨. |
| Transaction 경계 | PASS(이번 PR로 개선) | "외부 API 호출을 DB Transaction 내부에서 수행하지 않는다"를 `.claude/rules/spring-boot.md`에 이번 PR에서 명시했다(이전에는 문서에 없던 **FAIL 후보**). |
| Test Selection | PASS(이번 PR로 개선) | "Test에서 실제 외부 API를 호출하지 않는다"를 `docs/ai/testing-policy.md`에 이번 PR에서 명시했다(이전에는 명시적 금지가 없던 **PARTIAL 후보**). |
| Timeout/Retry 설계 | PARTIAL | Notion Tech Stack이 `Resilience4j`를 확정했지만 저장소 어디에도 아직 반영되지 않았다. 실제 WebClient 사용 코드가 없어 이번 PR에서 추가하지 않았다(Placeholder Dependency 금지 원칙과 상충). Collector Domain PR에서 처음 도입 시 판단 근거가 되도록 `docs/audit/notion-repository-sync.md`에 기록해 두었다. |
| Package Placement | PASS | Domain Map에 Collector가 명시되어 있다. |

## 시나리오 C — "회원 프로필에 새로운 필드 하나 추가해줘"

| 평가 항목 | 결과 | 근거 |
| --- | --- | --- |
| Persistence Safety | PASS | Migration 불변성, 새 버전 추가 규칙이 여러 문서에 반복적으로 명시되어 있다. |
| 영향 범위 파악 | PASS | `docs/ai/workflow.md` 4단계("영향 범위 분석")가 명시적 단계로 존재한다. |
| 개인정보 정책 확인 | PARTIAL | Notion PRD 6.2가 "전화번호는 응답에 포함하지 않는다"처럼 Member 고유의 엄격한 규칙을 명시하지만, 이는 Domain 고유 지식이라 `AGENTS.md` 같은 공통 문서에 넣지 않았다(의도적으로 두지 않음 — 특정 Domain 정책을 공통 문서에 넣으면 다른 Domain에는 적용되지 않는 규칙이 전역 규칙처럼 보일 위험이 있다). 이번 PR의 "Domain 기능이면 Notion 확인" 규칙이 이 Gap을 완화하지만, 실제 Member Domain PR에서 PRD 6.2를 놓치지 않는지는 그 PR 자체에서 다시 확인이 필요하다. |
| Test Selection | PASS | Integration Test 실행 조건이 명확하다. |

## 시나리오 D — "로그인이 가끔 안 되는 문제 고쳐줘"

| 평가 항목 | 결과 | 근거 |
| --- | --- | --- |
| 재현·증거 우선 | PASS | `AGENTS.md`("코드를 먼저 수정한 뒤 저장소 구조를 파악하는 방식은 금지"), `.claude/skills/test-and-verify`의 "실패 분석"("Root Cause 확인, 성급한 결론 금지")이 이미 강하다. |
| Scope Control | PASS | 관련 없는 Refactoring 금지가 반복적으로 명시되어 있다. |
| Token 로그 미노출 | PASS | `docs/ai/security-policy.md`, `.claude/rules/security.md`가 이미 명확하다(Notion Logging Convention과도 일치). |
| Regression Test | PASS | "변경한 동작에 대응하는 테스트를 작성하거나 갱신한다"(`docs/ai/testing-policy.md`)가 버그 수정에도 적용되며, Notion의 "Regression Test 추가" 요구와 실질적으로 동일하다. |
| 완료 보고 정확성 | PASS | `docs/ai/completion-policy.md`가 "검증 불가/부분 완료" 상태를 명확히 요구한다. |

## 종합

- 이번 Audit에서 발견한 가장 중요한 Gap은 **"Domain 기능 작업 시 Notion을 먼저 확인하라"는 명시적 지시가 없었던 것**이다. 사용자가 매번 Notion을 직접 언급하지 않는 한 이전 문서 구조로는 Agent가 이를 놓칠 수 있었다. `docs/ai/workflow.md`에 반영해 해결했다.
- 두 번째로 중요한 Gap은 **외부 I/O(API 호출)와 DB Transaction의 결합 금지**가 명문화되어 있지 않았던 것이다. `.claude/rules/spring-boot.md`에 반영해 해결했다.
- 세 번째는 **Test에서 실제 외부 서비스 호출 금지**가 명시적이지 않았던 것이다. `docs/ai/testing-policy.md`에 반영해 해결했다.
- API Contract 판단 시 Notion과 저장소 실제 구현이 충돌할 때의 처리 방법이 없었다. `AGENTS.md`의 우선순위 개정으로 해결했다.
- Member Domain의 개인정보 규칙처럼 특정 Domain에만 적용되는 세부 정책은 의도적으로 공통 문서에 넣지 않았다. 실제 Member Domain PR에서 Notion PRD를 다시 확인해야 한다는 점을 이 문서에 남긴다.
- 재실행 검증: 이번 PR에서 반영한 문서 변경 이후 시나리오를 다시 Static 평가한 결과, 위에서 "이번 PR로 개선"이라고 표시한 항목은 모두 PASS로 상향되었다. 실제 Agent 실행을 통한 동적 검증은 수행하지 않았다(NOT_EXECUTED).
