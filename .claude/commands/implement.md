---
description: Implements the feature, configuration, or documentation changes for the current Issue
argument-hint: [additional description of what to implement (optional)]
---

## Purpose

Implement the requirements of the Issue linked to the current work Branch with minimal scope. Refer to `$ARGUMENTS` if additional description is needed.

## References

Before starting, check [`AGENTS.md`](../../AGENTS.md), the current Issue, and the related [`.claude/rules/`](../rules/). Follow the [`spring-boot-change` Skill](../skills/spring-boot-change/SKILL.md), the [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md), and, when changing Controllers, [`docs/ai/openapi-documentation.md`](../../docs/ai/openapi-documentation.md) for detailed criteria.

## Procedure

1. Check the current Issue and work Branch.
2. Search for related code, Tests, configuration, and documentation.
3. Check existing similar implementations and Naming.
4. Analyze the impact scope.
5. Make a minimal change plan. If the scope is large, split it into logical steps.
6. Implement according to the plan.
7. If you added or changed a Controller Endpoint, apply the Swagger Annotation rules in `docs/ai/openapi-documentation.md`.
8. Write or update related Tests.
9. Run the Tests covering the change scope (including `OpenApiDocumentationTest` if you changed a Controller).
10. If Kotlin code changed, clean up formatting with `./gradlew spotlessApply` and check static analysis with `./gradlew detekt`.
11. Run the full Test suite and Build (`check` includes `spotlessCheck`, `detekt`, and `koverVerify`).
12. Run `git diff --check`.
13. Review the Diff directly.
14. Report the results.

Commit and Push only when the user explicitly requests it.

## Prohibited Actions

- Adding features outside the Issue scope
- Unrelated Refactoring
- Duplicate implementations without checking existing ones
- Creating unused Classes
- Creating Placeholders for future features
- Leaving core requirements as TODOs and marking the work complete
- Adding new Dependencies without justification
- Deleting or disabling Tests
- Temporary implementations that only make compilation pass

## Result Report

- Analysis results
- Implementation details
- Changed files
- Key decisions and assumptions
- Test and Build results
- Verification that could not be run
- Whether Commit and Push were done
- Remaining issues
