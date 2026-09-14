---
description: Verifies the current work and prepares a Commit, Push, and Draft PR targeting develop (only on the user's explicit request)
---

## Purpose

Verify the current work, then proceed through Commit, Push, and Draft Pull Request creation.

**Run this Command only when the user has explicitly requested a Commit, Push, and PR creation.**

## References

Follow the [`pull-request` Skill](../skills/pull-request/SKILL.md) and the [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md) for detailed criteria.

## Procedure

1. Check [`AGENTS.md`](../../AGENTS.md), [`CLAUDE.md`](../../CLAUDE.md), and the current Issue.
2. Check the current Branch.
3. Check for an existing PR from the same Head Branch with `gh pr list --head <branch>`. If one exists, update it instead of creating a new one.
4. Check the Working Tree with `git status` and `git diff`.
5. Compare against the Issue requirements and out-of-scope items.
6. Run related Tests.
7. Run the full Test suite and Build.
8. Run `git diff --check`.
9. Confirm that no Secrets or unnecessary files are included.
10. Stage only related files.
11. Write the Commit message (`<type>: <한글 작업 내용>`).
12. Commit.
13. Push (Force Push is forbidden).
14. Create a Draft PR targeting `develop` (if a PR already exists, update its body).
15. Link the related Issue in the PR body with `Closes #{issue-number}`.
16. Apply Labels that actually exist in the repository to the PR.
17. Change the Issue status Label to `review`.
18. Report the results.

## Default PR Body Structure

The PR body is written in Korean, matching the repository's PR template.

```markdown
## 작업 배경

## 변경 내용

## 주요 설계 판단

## 검증

## 영향 범위

## 제외 범위

## 체크리스트

- [ ] 현재 Issue의 요구사항을 충족합니다.
- [ ] Issue 제외 범위를 준수했습니다.
- [ ] 관련 없는 변경을 포함하지 않았습니다.
- [ ] Commit 메시지가 `<type>: <한글 작업 내용>` 형식을 따릅니다.
- [ ] 관련 Test를 실행했습니다.
- [ ] 전체 Test와 Build 결과를 확인했습니다.
- [ ] Secret 또는 민감정보가 포함되지 않았습니다.
- [ ] 문서 갱신 필요성을 검토했습니다.

## 관련 Issue

Closes #이슈번호
```

Check only items that were actually verified.

## Prohibited Actions

- Creating a Commit, Push, or PR without the user's request
- Force Push
- Creating duplicate PRs
- Merging without the user's request
- Checking unverified items

## Result Report

- Verification results (Test, Build)
- Commit hash
- Push result
- PR number and URL, base/head, whether it is a Draft
- Issue Label changes
- Remaining work
