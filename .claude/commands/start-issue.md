---
description: Safely starts GitHub Issue-based work (updates develop, creates a Branch, switches the status Label)
argument-hint: <issue-number>
---

## Purpose

Take a GitHub Issue number and prepare everything up to a state where work can begin (a work Branch based on the latest `develop`, `in progress` status).

## Required Input

Issue number: `$1`

If no Issue number is provided, do not pick or guess one; confirm with the user.

## References

Before starting, check [`AGENTS.md`](../../AGENTS.md) and [`CLAUDE.md`](../../CLAUDE.md). Follow the [`issue-workflow` Skill](../skills/issue-workflow/SKILL.md) for detailed decision criteria.

## Procedure

1. Check `git status` and the current Branch — if there are uncommitted changes, analyze their origin and protect the user's changes.
2. Check the Issue title, body, completion conditions, out-of-scope items, and Labels with `gh issue view $1`.
3. If there are prerequisite PRs/Dependencies, confirm they have actually been merged.
4. Update `develop` with `git switch develop && git pull --ff-only origin develop`.
5. Check whether a work Branch based on the Issue number already exists.
6. If not, create it with `git switch -c <type>/$1-{short-description} develop`. The Branch format follows the repository's Git Convention (`README.md`, `docs/ai/git-conventions.md`).
7. Change the Issue status Label from `ready` → `in progress`, using the names that actually exist in the repository.
8. Write a brief work plan before implementation.

This Command stops here. It does not implement code, Commit, Push, or create a PR.

## Stop Conditions

Stop and report the cause in the following cases.

- The Working Tree has changes of unknown origin
- A prerequisite PR has not been applied yet
- The Issue cannot be found
- The current Branch is already a Branch for other work
- Failure to update `develop` (Fast-forward not possible, etc.)
- GitHub authentication failure

Do not automatically Stash, Reset, or delete the user's changes.

## Result Report

- Issue number and title
- Completion conditions, out-of-scope items
- Base Branch, work Branch
- Label changes
- Implementation plan
- If stopped, the cause and risk factors
