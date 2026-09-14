---
name: issue-workflow
description: Provides a consistent procedure for GitHub Issue-based work, from start through PR linking. Covers Issue analysis, Branches, the status Label flow, progress comments, and failure-handling criteria.
---

# Issue Workflow

Detailed decision criteria to consult when starting and progressing GitHub Issue-based work in GETI-Server. The [`start-issue` Command](../../commands/start-issue.md) references this Skill.

## Issue Analysis

When reviewing an Issue, read all of the following.

- Title
- Background
- Tasks (checklist)
- Completion conditions (Acceptance Criteria)
- Out-of-scope items
- Prerequisite Issues and PRs (whether they are mentioned in the body or comments)
- Labels (work type, priority, affected area)
- Ownership (if there is an Assignee, whether there is a conflict)

Do not decide the work scope arbitrarily without checking the completion conditions and out-of-scope items.

## Branch

- The Base Branch is `develop` by default. Update it with `git pull --ff-only origin develop` before starting work.
- Include the Issue number in the Branch name: `<type>/{issue-number}-{short-description}`.
- Check whether a work Branch for that Issue number already exists locally or remotely; if it does, use that Branch instead of creating a duplicate.
- Do not work directly on `main` or `develop`.

## Status Label Flow

Follow the flow below, based on the status Labels that actually exist in the repository (check with `gh label list`).

```text
📋 backlog → 📝 ready → 🚧 in progress → 👀 review → (Issue Close)
                                   ↕
                              ⛔ blocked
```

- When starting work, remove `ready` and add `in progress`.
- When implementation and verification are done and the work is awaiting review, remove `in progress` and add `review`.
- When blocked by external factors, add `blocked`, and restore the previous status once resolved.
- Always keep exactly one status Label.
- The `✅ done` Label does not exist in this repository. The Issue's Closed state itself means completion (see [`docs/ai/git-conventions.md`](../../../docs/ai/git-conventions.md)).

## Progress Comments

You may leave Issue comments at the following points.

- Work start
- Completion of a major step (e.g., a midpoint of work split into multiple Commits)
- Entering the Blocked state
- PR creation
- When a verification failure requires a change of direction
- When the work scope needs to change

Comments must contain only results actually produced, actual Commit hashes, and actual Test/Build results. Do not comment on every minor intermediate state.

## Failure Handling

In the following situations, do not proceed on your own; report the cause.

- GitHub authentication failure (check with `gh auth status`)
- Issue lookup failure (wrong number, no permission)
- A required Label does not exist in the repository — do not create it; substitute an existing Label or report to the user
- A prerequisite PR has not yet been merged/applied
- Failure to update `develop` (Fast-forward not possible, conflicts, etc.)
- Push failure
- A PR for the same Head Branch already exists (update the existing PR instead of creating a new one)

Do not invent Issue numbers or nonexistent Labels.
