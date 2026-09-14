---
description: Reproduces a bug, finds its actual cause, and fixes it
argument-hint: <bug symptom or reproduction conditions>
---

## Purpose

Reproduce the reported bug (`$ARGUMENTS`) and, instead of hiding the symptom, find the actual cause and fix it with minimal scope.

## References

Follow the [`spring-boot-change` Skill](../skills/spring-boot-change/SKILL.md) and the [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md) for detailed criteria.

## Required Information

- Bug symptom
- Reproduction conditions
- Expected behavior
- Logs or Stack Trace, if available

If information is insufficient, do not proceed by guessing; confirm with the user.

## Procedure

1. Summarize the symptom and expected behavior.
2. Check whether the bug can be reproduced.
3. Explore the related code and existing Tests.
4. List possible causes.
5. Verify the actual cause with logs and code.
6. If possible, write a Test that reproduces the failure first.
7. Fix it with minimal scope.
8. Run regression Tests.
9. If Kotlin code changed, check formatting/static analysis with `./gradlew spotlessApply` and `./gradlew detekt`.
10. Run the full Test suite and Build (`check` includes `spotlessCheck`, `detekt`, and `koverVerify`).
11. Review the Diff directly.
12. Report the cause and the fix.

## Prohibited Actions

- Fixes that only hide the symptom
- Unconditionally catching and ignoring exceptions
- Deleting only the logs and treating it as resolved
- Removing Validation
- Bypassing Security
- Deleting failing Assertions
- Disabling Tests (`@Disabled`, etc.)
- Large-scale changes based on guesses without reproduction
- Including unrelated Refactoring

## Result Report

- Whether the bug was reproduced
- Actual cause
- Changes made
- Regression-prevention Tests
- Verification results
- Remaining risks
