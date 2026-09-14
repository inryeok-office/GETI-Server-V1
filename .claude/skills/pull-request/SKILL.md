---
name: pull-request
description: Covers the procedure and criteria for safely turning only verified changes into a Commit, Push, and Draft Pull Request.
---

# Pull Request

Detailed criteria to consult when preparing a Commit, Push, and PR in GETI-Server. The [`prepare-pr` Command](../../commands/prepare-pr.md) references this Skill.

## Pre-PR Checks

- The current Issue's completion conditions and out-of-scope items
- Whether the current Branch is the correct work Branch
- Whether the Base Branch (`develop`) is up to date
- Whether a PR for the same Head Branch already exists (`gh pr list --head <branch>`)
- Working Tree and Diff
- Test and Build results
- Whether Secrets or unnecessary files are included

## Commit

- Stage only related files.
- Put only one logical unit in each Commit.
- Write the Type in lowercase English and the description in Korean: `<type>: <한글 작업 내용>`.
- If a Commit fails (Hook failure, etc.), identify the cause and do not report the failure as a success.

## Push

- Check the current Branch and Upstream.
- Use only a plain `git push`.
- Do not Force Push (`--force`, `--force-with-lease`).
- If the Push fails, report the exact error.

## Draft PR

- The Base Branch is `develop` by default.
- If a PR for the same Head Branch already exists, update the existing PR body instead of creating a new one.
- Include the following in the PR body.
  - Related Issue link (`Closes #{issue-number}`)
  - Changes
  - Key design decisions
  - Verification actually run and its results
  - Impact scope
  - Out-of-scope items
  - A checklist in which only actually verified items are checked

## Label

- Apply only Labels confirmed to actually exist in the repository via `gh label list`.
- Do not arbitrarily create nonexistent Labels.
- Apply work type (`🧹 chore`, etc.) and affected area (`area:`, etc.) Labels to the PR as well.
- Apply status Labels (`in progress`, `review`, etc.) only to Issues, not to PRs (see [`docs/ai/git-conventions.md`](../../../docs/ai/git-conventions.md)).

## CI Checks

- After creating the Draft PR, confirm with `gh pr checks {pr-number}` that GitHub Actions (the `CI` Workflow, see [`docs/development/ci.md`](../../../docs/development/ci.md)) actually runs and passes.
- If a Job fails, check the cause with `gh run view {run-id} --log-failed`, fix it, and Push a new Commit (Amend and Force Push are forbidden).
- Do not report that CI passed without checking the actual GitHub Actions Run result.
- When modifying Workflow files (`.github/workflows/*.yml`), keep least-privilege `permissions` and do not add risky Triggers such as `pull_request_target` without justification.

## Issue Status

When a PR is created, transition the Issue status Label as follows.

```text
in progress → review
```

Closing the Issue after the PR is merged and cleaning up statuses after `review` is not within this Skill's scope; leave it as separate work (on user request or at Merge time).

## Prohibited Actions

- Creating a Commit, Push, or PR without the user's explicit request
- Force Push
- Creating duplicate PRs
- Merging without the user's request
- Checking off unverified items in the checklist
