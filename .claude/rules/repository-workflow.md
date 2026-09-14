# Repository Workflow (Claude Code)

The execution procedure Claude Code follows when working in the GETI-Server repository. It organizes the common Workflow of [`AGENTS.md`](../../AGENTS.md) (`docs/ai/workflow.md`) into concrete behavior rules from Claude Code's perspective.

## Starting Work

Check the following.

```bash
git status
git branch --show-current
git log --oneline --decorate -10
```

If needed:

```bash
gh issue view {issue-number}
gh pr status
```

Things to check:

- Whether you are on the correct work Branch
- Whether there are uncommitted changes, and if so, whose changes they are
- Whether prerequisite work (other PRs, Issues) has actually been applied
- The current Issue's completion conditions and out-of-scope items

## Explore First

Do the following before modifying files.

- Search for related files
- Search for similar implementations
- Search for existing tests
- Check existing Naming and Package structure
- Check the Build configuration
- Check related documents (`AGENTS.md`, `docs/ai/`, `README.md`)

Generating code first and then fitting it to the repository structure is forbidden.

## Planning

Organize the following before making changes.

- Purpose of the change
- Files to change
- Impact scope
- Test plan
- Changes to exclude
- Key assumptions

Do not create excessive design documents for simple tasks.

## Implementation

- Meet the requirements with minimal changes.
- Do not modify unrelated files.
- Prefer existing patterns.
- Do not create temporary Code or empty Classes.
- Do not leave core requirements as TODOs.
- Check existing alternatives before adding a new Dependency.

## Verification

- Run the Tests covering the change scope.
- Run the full Test suite and Build.
- Run `git diff --check`.
- Review the Diff directly.
- Confirm that no Secrets or temporary files are included.

## Commit and Push

- Perform them only when the user requests it or the current Prompt explicitly requests it.
- Stage only related files.
- Write the Commit Type in English and the description in Korean (see [`git-and-github.md`](./git-and-github.md)).
- Check the current Branch before pushing.
- Do not Force Push.

## Autonomous Decision Criteria

Follow these so that Claude Code does not needlessly ask about every minor decision.

- Decide on your own matters that can be safely judged from existing code and specifications.
- Do not ask about matters that can be decided by existing patterns, such as simple Naming or file placement.
- Ask only when important product, data, or security policies are unclear.
- If you chose a safe default, state the assumption in the completion report.
- Do not stop over a minor choice when the work can proceed.

However, risky Git operations, Secret access, and production data changes are not decided autonomously; follow [`security.md`](./security.md).
