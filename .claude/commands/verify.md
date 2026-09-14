---
description: Verifies that the current changes meet the requirements and quality criteria (Test, Build, links, Secrets)
---

## Purpose

Comprehensively verify the current changes and judge the results accurately.

## References

Follow the [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md) for detailed criteria.

## Procedure

1. `git status`
2. Check the current Branch
3. `git diff`
4. `git diff --check`
5. Run Tests matching the change scope
6. If Kotlin code changed, run `spotlessCheck` and `detekt` (if there are formatting violations, auto-fix with `spotlessApply` and re-check)
7. Run the full Test suite
8. Run the Build (`check` includes `spotlessCheck` and `detekt`, so they run as well)
9. Check Markdown relative links and configuration paths
10. Check whether Secrets or personal environment files are included
11. Check whether unnecessary files are included
12. Compare against the Issue's completion conditions and out-of-scope items
13. Report the results

## Default Gradle Verification

Windows:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat detekt
.\gradlew.bat clean test build
```

Git Bash or Unix:

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew clean test build
```

If you need to check the coverage Report, run it separately (it is not included in `check`).

```bash
./gradlew koverHtmlReport
./gradlew koverXmlReport
```

## Failure Classification

```text
Compilation failure
Test failure
Spring Context failure
Configuration failure
Dependency failure
Formatting violation (spotlessCheck)
Static analysis violation (detekt)
Documentation or path failure
External service failure
Local environment failure
Permission failure
```

## Prohibited Actions

- Deleting failing Tests
- Bypassing by adding `@Disabled`
- Removing Assertions
- Skipping Build Tasks and reporting success
- Treating verification that was not run as successful
- Arbitrarily confusing warnings and errors

## Result Status

State the result clearly as one of the following (see [`docs/ai/completion-policy.md`](../../docs/ai/completion-policy.md), which defines these status terms in Korean).

```text
완료       (Complete)
부분 완료  (Partially complete)
검증 불가  (Unverifiable)
실패       (Failed)
```
