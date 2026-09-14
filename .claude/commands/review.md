---
description: Code-reviews the current Branch's changes or a specified Diff (no code changes by default)
argument-hint: [review target (optional, defaults to origin/develop...HEAD)]
---

## Purpose

Review the current changes from the perspectives of functional correctness, security, and maintainability. By default, **only perform the review and do not modify code**. Make changes only when the user explicitly requests fixes as well.

## References

Follow the [`code-review` Skill](../skills/code-review/SKILL.md) for review criteria.

## Target

If `$ARGUMENTS` is provided, review that target. Otherwise, use the following.

```bash
git diff origin/develop...HEAD
```

## Procedure

1. Check the Diff under review.
2. Review it against the review items in the [`code-review` Skill](../skills/code-review/SKILL.md) (functional correctness, data/Transactions, API, security, performance, maintainability, Test).
3. Assign a severity (`Critical`, `High`, `Medium`, `Low`, `Suggestion`) to each finding.
4. Report the results.

## Result Report

Each finding includes the following.

- Severity
- File and location
- Problem
- Impact
- Suggested fix direction
- Evidence

Even if there are no problems, report the following.

- Scope reviewed
- Commands run
- Areas not reviewed
- Residual risk
