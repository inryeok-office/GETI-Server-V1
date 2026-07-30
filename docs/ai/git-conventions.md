# Git 규칙 (AI 작업 원칙)

저장소의 전체 Git Flow, Branch 전략, Commit Convention, Label 체계는 저장소 [`README.md`](../../README.md)에 이미 정리되어 있다. 이 문서는 그 내용을 반복하지 않고, AI Agent가 특히 주의해야 할 사항만 다룬다.

## Branch

- `main` : 운영/배포 가능한 안정 버전. AI가 직접 작업하거나 Push하지 않는다.
- `develop` : 다음 개발 버전을 통합하는 기본 개발 브랜치. AI가 직접 작업하거나 Push하지 않는다.
- 모든 작업은 Issue 번호를 포함한 작업 Branch에서 수행한다. Branch Naming은 README의 형식(`feature/{issue-number}-...`, `chore/{issue-number}-...` 등)을 따른다.
- `main`, `develop`은 GitHub Branch Protection이 적용되어 있어 직접 Push, Force Push, 삭제가 차단된다. AI가 우회를 시도하지 않는다.

## Commit

- Conventional Commit 형식 `<type>: <한글 작업 내용>`을 사용한다. Type은 영문 소문자, 설명은 한글이다.
- 허용 Type은 `AGENTS.md`에 명시된 12종(`feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `config`, `build`, `ci`, `perf`, `style`, `revert`)을 사용한다.
- Commit Body는 변경이 복잡하거나 이유를 설명해야 할 때만 사용한다. 한 줄 제목으로 충분한 변경에는 Body를 억지로 추가하지 않는다.
- Breaking Change가 있으면 Footer에 `BREAKING CHANGE: <설명>`을 명시한다.
- Issue 연결이 필요하면 Footer에 `Refs: #번호`를 사용할 수 있다. `Closes #번호`는 커밋이 아닌 Pull Request 본문에서 사용해 Issue가 의도치 않게 닫히는 상황을 피한다.
- Commit 전 반드시 `git status`, `git diff`, `git diff --staged`로 실제 변경 내용을 확인하고, 현재 작업과 관련된 파일만 Stage한다.

## Push

- 사용자가 명시적으로 요청한 경우에만 Push한다.
- 일반 `git push`만 사용한다. `git push --force`, `git push --force-with-lease`는 사용자가 명확히 요청하고 영향 범위(다른 사람이 해당 Branch를 사용 중인지)를 확인하기 전에는 사용하지 않는다.
- 이미 원격에 Push되어 공유된 Commit의 History를 임의로 Rewrite(amend, rebase 등)하지 않는다. 아직 Push하지 않은 로컬 Commit만 필요한 경우 정리할 수 있다.

## Pull Request

- Base Branch는 `develop`이다. `main`을 대상으로 PR을 생성하지 않는다 (별도 릴리스 절차 제외).
- 작업이 진행 중이거나 리뷰를 미리 받고 싶은 단계에서는 Draft PR을 사용한다. 구현과 검증이 끝나 리뷰를 받을 준비가 되면 Ready for Review로 전환한다.
- PR 제목은 저장소 Convention(`[FEAT] ...`, `[CHORE] ...` 등)을 따르고, 본문에 `Closes #{issue-number}`로 연결한다.
- Squash Merge를 사용하는 경우 최종 Squash Commit 메시지도 한글 규칙을 따른다. 실제 Merge는 사용자가 명시적으로 요청한 경우에만 수행한다.

## Issue 상태 Label 흐름

저장소에 실제로 존재하는 상태 Label은 다음과 같다 (`✅ done`은 Issue Close 상태와 중복되어 별도로 만들지 않았다).

```text
📋 backlog → 📝 ready → 🚧 in progress → 👀 review → (Issue Close)
                                   ↕
                              ⛔ blocked
```

- 작업을 시작하면 `📝 ready`를 제거하고 `🚧 in progress`를 추가한다.
- 구현과 검증이 끝나 PR 리뷰를 기다리는 단계면 `🚧 in progress`를 제거하고 `👀 review`를 추가한다.
- 외부 의존성이나 문제로 진행이 막히면 `⛔ blocked`를 추가한다. 문제가 해결되면 이전 상태 Label로 되돌린다.
- 상태 Label은 항상 하나만 유지한다. 두 개 이상의 상태 Label을 동시에 붙이지 않는다.
- Issue가 완료되어 Close되면 별도의 `done` Label 없이 Close 상태 자체로 완료를 표시한다.
- 상태 Label은 Issue에만 적용한다. PR에는 작업 유형(`🧹 chore` 등)과 영향 영역(`area:` 등) Label만 적용한다.

## 사용자 변경 보호

- 사용자가 이미 작업한 내용, Stage된 변경, 다른 Branch의 작업을 AI가 임의로 삭제하거나 덮어쓰지 않는다.
- `git reset --hard`, `git clean -fd`, `git checkout -- .`, `git restore .` 등 되돌리기 어려운 명령은 [`security-policy.md`](./security-policy.md)의 제한을 따른다.
