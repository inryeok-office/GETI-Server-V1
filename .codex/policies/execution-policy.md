# Execution Policy

Codex를 대화형 또는 비대화형(`codex exec`)으로 실행할 때 적용하는 공통 실행 원칙이다. 공통 원칙은 [`AGENTS.md`](../../AGENTS.md)를 따르고, 이 문서는 Codex 실행 관점에서 구체화한다.

## 실행 전 확인

```bash
git status
git branch --show-current
git log --oneline --decorate -10
```

필요한 경우:

```bash
gh issue view {issue-number}
gh pr status
```

확인 항목:

- 현재 Branch (Issue 번호 기반 작업 Branch인지)
- Working Tree (미커밋 변경의 출처와 목적)
- 현재 Issue의 완료 조건과 제외 범위
- 선행 PR이 실제로 반영되었는지
- 관련 코드와 테스트
- 사용자 미커밋 변경 — 자동으로 Stash, Reset, 삭제하지 않는다

## 실행 모드 선택

`codex --help`로 확인된 실제 옵션을 기준으로 한다.

```text
-s, --sandbox <read-only|workspace-write|danger-full-access>
-a, --ask-for-approval <untrusted|on-request|never>
```

원칙:

- 저장소 분석이나 리뷰처럼 읽기만 필요한 작업은 `--sandbox read-only`처럼 가장 제한적인 권한을 우선 사용한다.
- 코드 변경이 필요한 작업은 `--sandbox workspace-write`로 현재 저장소 내부 쓰기 권한만 사용한다.
- Network Access는 Dependency 다운로드나 `gh` 명령처럼 실제로 필요한 경우에만 허용한다.
- 저장소 외부 쓰기 권한(`--add-dir`로 추가 디렉터리를 넓히는 것 포함)은 반드시 필요한 경우에만 사용한다.
- `--sandbox danger-full-access`와 `--dangerously-bypass-approvals-and-sandbox`는 기본값으로 사용하지 않는다. CLI 자체가 후자를 "EXTREMELY DANGEROUS"로 명시한다.
- 승인 요청이 번거롭다는 이유로 `-a never`를 기본값으로 사용하지 않는다. `-a on-request`(기본값에 가까움) 또는 `-a untrusted`를 우선 검토한다.

작업 유형별 구체적인 권장 조합은 [`sandbox-policy.md`](./sandbox-policy.md)를 따른다.

## 대화형 실행 (`codex`)

다음 작업에 적합하다.

- 저장소 탐색과 요구사항 구체화
- 코드 리뷰
- 여러 단계로 나뉘는 구현으로, 중간 결과를 확인하며 진행해야 하는 작업
- 사용자와의 확인이 반복적으로 필요한 작업

## 비대화형 실행 (`codex exec`)

다음 조건을 모두 만족할 때 사용한다.

- Prompt가 구체적이다 (목표, 완료 조건, 제외 범위가 명확함)
- 적용할 Sandbox와 Approval 정책이 명확하다
- 실패 시 중단 조건이 정의되어 있다
- Commit, Push, PR 수행 권한이 Prompt에 명확히 기술되어 있다

`codex exec`는 Prompt를 인자 또는 표준 입력(`-`)으로 받는다.

```bash
codex exec "<prompt>"
codex exec - < .codex/prompts/verify-changes.md
```

비대화형 코드 리뷰는 전용 명령을 사용한다.

```bash
codex review --base develop
codex review --uncommitted
```

## 중단 조건

다음 상황에서는 임의로 진행하지 않고 원인을 보고한다.

- 현재 Branch가 잘못됨 (`main`/`develop` 직접 작업 포함)
- 출처 불명의 미커밋 변경이 있음
- 선행 PR이 아직 반영되지 않음
- Issue 조회 실패 또는 GitHub 인증 실패
- 요구사항과 저장소 실제 상태가 충돌함
- Secret 또는 운영 데이터 접근이 필요함
- 파괴적 Git 명령이 필요해 보임
- Test 또는 Build 실행 환경이 불명확함

## 금지 사항

- 사용자 요청 없는 Merge
- Force Push (`--force`, `--force-with-lease`)
- 공유 History Rewrite
- 운영 데이터 수정
- Secret 출력 또는 Commit
- 인증 및 인가 우회
- Test 삭제 또는 비활성화
- 실패를 성공으로 보고
- 관련 없는 대규모 Refactoring
