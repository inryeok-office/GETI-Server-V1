# Git & GitHub (Claude Code)

Behavior rules Claude Code follows when using Git and the GitHub CLI (`gh`). The repository's Git Flow, Commit Convention, and Label system are already documented in [`README.md`](../../README.md) and [`docs/ai/git-conventions.md`](../../docs/ai/git-conventions.md), so this document covers only Claude Code's concrete behavior, within limits that do not conflict with them.

## Branch

- Do not work directly on `main` or `develop`.
- Use Issue number-based Branches (`{type}/{issue-number}-{description}`).
- When starting work, check the current Branch with `git branch --show-current` before making changes.
- The Base Branch is `develop` by default.
- Do not create a duplicate of an existing work Branch.
- If the work depends on a prerequisite PR/Issue, confirm it has actually been merged before proceeding.

## Commit

Format:

```text
<type>: <한글 작업 내용>
```

Examples (the description must be written in Korean):

```text
docs: Claude Code 전용 작업 규칙 추가
feat: 사용자 프로필 조회 기능 추가
fix: 인증 토큰 만료 처리 오류 수정
refactor: 공고 검증 로직 분리
test: 사용자 조회 예외 테스트 추가
```

Use the 12 allowed Types specified in the Commit Convention of `AGENTS.md` and `README.md` (`feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `config`, `build`, `ci`, `perf`, `style`, `revert`).

Check the following before committing.

```bash
git status
git diff
git diff --staged
```

Stage only files related to the current work.

## Push

- Push only when the user explicitly requests it.
- Check the current Branch and Remote Branch before pushing.
- Do not use `git push --force` or `git push --force-with-lease`.
- Do not arbitrarily Rewrite the History of Commits that have already been pushed and shared.
- If a Push fails, do not report it as successful.

## Pull Request

- The Base Branch is `develop` by default.
- Before creating a PR, check with `gh pr list` whether a PR for the same Head Branch already exists, to avoid duplicates.
- Write the verification results actually run in the PR body, and do not check items that were not run.
- Link the related Issue in the PR body in the `Closes #{issue-number}` format.
- Do not Merge without the user's explicit request.
- If GitHub Actions CI (`.github/workflows/ci.yml`, [`docs/development/ci.md`](../../docs/development/ci.md)) is configured, check the actual run results with `gh pr checks` after creating the PR. Do not report unchecked CI results as passing.
- Do not change the Required Status Checks of the Repository Ruleset/Branch Protection without the user's explicit approval.

## Issue Status Labels

The status Labels that actually exist in the repository and their flow are as follows (`✅ done` was not created because it duplicates the Issue Closed state; see [`docs/ai/git-conventions.md`](../../docs/ai/git-conventions.md)).

```text
📋 backlog → 📝 ready → 🚧 in progress → 👀 review → (Issue Close)
                                   ↕
                              ⛔ blocked
```

- When starting work, remove `📝 ready` and add `🚧 in progress` (`gh issue edit {n} --remove-label "📝 ready" --add-label "🚧 in progress"`).
- When the work is awaiting review, remove `🚧 in progress` and add `👀 review`.
- Always keep exactly one status Label.
- Confirm Label names that actually exist in the repository with `gh label list` and use them exactly as they are. Do not arbitrarily create nonexistent Labels.

## GitHub Records

You may record the following in Issue or PR comments.

- Work summary
- Changed files
- Test and Build results
- Commit hash
- Remaining work
- Next steps

Do not report that you created an Issue, PR, or comment that you did not actually create.
