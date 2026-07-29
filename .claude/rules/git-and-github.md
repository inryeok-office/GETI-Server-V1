# Git & GitHub (Claude Code)

Claude Code가 Git과 GitHub CLI(`gh`)를 사용할 때 따르는 행동 규칙이다. 저장소의 Git Flow, Commit Convention, Label 체계는 [`README.md`](../../README.md)와 [`docs/ai/git-conventions.md`](../../docs/ai/git-conventions.md)에 이미 정리되어 있으므로 이 문서는 그 내용과 충돌하지 않는 범위에서 Claude Code의 구체적 행동만 다룬다.

## Branch

- `main`, `develop`에서 직접 작업하지 않는다.
- Issue 번호 기반 Branch(`{type}/{issue-number}-{설명}`)를 사용한다.
- 작업 시작 시 `git branch --show-current`로 현재 Branch를 확인한 뒤 수정한다.
- Base Branch는 기본적으로 `develop`이다.
- 이미 존재하는 작업 Branch를 중복 생성하지 않는다.
- 선행 PR/Issue를 전제로 하는 작업이라면 실제로 Merge되었는지 확인한 뒤 진행한다.

## Commit

형식:

```text
<type>: <한글 작업 내용>
```

예시:

```text
docs: Claude Code 전용 작업 규칙 추가
feat: 사용자 프로필 조회 기능 추가
fix: 인증 토큰 만료 처리 오류 수정
refactor: 공고 검증 로직 분리
test: 사용자 조회 예외 테스트 추가
```

허용 Type은 `AGENTS.md`와 `README.md`의 Commit Convention에 명시된 12종(`feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `config`, `build`, `ci`, `perf`, `style`, `revert`)을 사용한다.

Commit 전 다음을 확인한다.

```bash
git status
git diff
git diff --staged
```

현재 작업과 관련된 파일만 Stage한다.

## Push

- 사용자가 명시적으로 요청한 경우에만 Push한다.
- Push 전 현재 Branch와 Remote Branch를 확인한다.
- `git push --force`, `git push --force-with-lease`를 사용하지 않는다.
- 이미 Push되어 공유된 Commit의 History를 임의로 Rewrite하지 않는다.
- Push가 실패하면 성공했다고 보고하지 않는다.

## Pull Request

- Base Branch는 기본적으로 `develop`이다.
- PR을 생성하기 전에 같은 Head Branch의 PR이 이미 있는지 `gh pr list`로 확인해 중복 생성을 피한다.
- PR 본문에 실제로 실행한 검증 결과를 작성하고, 실행하지 않은 항목을 체크하지 않는다.
- PR 본문에 관련 Issue를 `Closes #{issue-number}` 형식으로 연결한다.
- Merge는 사용자의 명시적 요청 없이 수행하지 않는다.
- GitHub Actions CI(`.github/workflows/ci.yml`, [`docs/development/ci.md`](../../docs/development/ci.md))가 구성되어 있다면 PR 생성 후 `gh pr checks`로 실제 실행 결과를 확인한다. 확인하지 않은 CI 결과를 통과했다고 보고하지 않는다.
- Repository Ruleset/Branch Protection의 Required Status Check는 사용자의 명시적 승인 없이 변경하지 않는다.

## Issue 상태 Label

저장소에 실제 존재하는 상태 Label과 그 흐름은 다음과 같다 (`✅ done`은 Issue Close 상태와 중복되어 만들지 않았다. [`docs/ai/git-conventions.md`](../../docs/ai/git-conventions.md) 참고).

```text
📋 backlog → 📝 ready → 🚧 in progress → 👀 review → (Issue Close)
                                   ↕
                              ⛔ blocked
```

- 작업을 시작하면 `📝 ready`를 제거하고 `🚧 in progress`를 추가한다 (`gh issue edit {n} --remove-label "📝 ready" --add-label "🚧 in progress"`).
- 리뷰를 기다리는 단계면 `🚧 in progress`를 제거하고 `👀 review`를 추가한다.
- 상태 Label은 항상 하나만 유지한다.
- Label 이름은 저장소에 실제 존재하는 이름을 `gh label list`로 확인한 뒤 정확히 그대로 사용한다. 존재하지 않는 Label을 임의로 만들지 않는다.

## GitHub 기록

Issue 또는 PR 댓글에 다음을 기록할 수 있다.

- 작업 요약
- 변경 파일
- Test 및 Build 결과
- Commit hash
- 남은 작업
- 다음 단계

실제로 생성하지 않은 Issue, PR, 댓글을 생성했다고 보고하지 않는다.
