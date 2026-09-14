---
name: code-review
description: Covers criteria for systematically reviewing changes from the perspectives of functional correctness, data/Transactions, API, security, performance, maintainability, and Tests.
---

# Code Review

Detailed criteria to consult when reviewing GETI-Server changes. The [`review` Command](../../commands/review.md) references this Skill. By default, a review does not modify code and only reports findings.

## Review Items

### Functional Correctness

- Missing requirements
- Incorrect conditionals
- Boundary value handling
- Null handling
- Exception flows
- Side effects of state changes

### Data and Transactions

- Whether the Transaction scope is appropriate
- Atomicity of operations
- Possibility of duplicate processing
- Concurrency issues
- Data integrity
- Whether retries cause side effects

### API

- Request Validation
- Response compatibility
- Status Code
- Whether public APIs (Signatures, paths, etc.) change
- Error Contract consistency

### Security

- Authentication handling
- Authorization handling
- User input validation
- Whether Secrets are exposed
- Whether sensitive information is logged
- Possibility of privilege escalation

### Performance

- Unnecessary loops
- Possibility of N+1 queries
- How large data volumes are processed
- Blocking calls
- Unnecessary external calls

### Maintainability

- Consistency with existing Patterns
- Duplicate code
- Clarity of Naming
- Separation of responsibilities
- Excessive abstraction
- Unnecessary Dependencies
- Leftover Debug Code
- If a new Package was added, whether it avoids directly referencing other Application Modules' internal implementations and avoids creating circular dependencies (see [`docs/architecture/modularity.md`](../../../docs/architecture/modularity.md))

Pure formatting issues such as whitespace and Import ordering are already checked automatically in `check` by Spotless (ktlint), and many static analysis items such as code smells and complexity by detekt (see [`docs/development/code-quality.md`](../../../docs/development/code-quality.md)). In reviews, focus on logic and design problems these tools cannot catch, and do not duplicate findings for rules the tools already enforce.

### Test

- Whether the happy path is verified
- Whether exception paths are verified
- Whether regressions are prevented
- Whether Tests are overly coupled to implementation details
- Whether required Tests are missing

## Result Format

Each finding includes the following.

- Severity: `Critical` / `High` / `Medium` / `Low` / `Suggestion`
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

## Criteria Not Yet Finalized

This repository does not yet implement domain logic, actual Domain APIs, or Security. PostgreSQL/Redis connections and the Migration/Test foundation ([`docs/development/persistence.md`](../../../docs/development/persistence.md)) and the common Web/API response and error-handling foundation ([`docs/development/web-api.md`](../../../docs/development/web-api.md)) are in place, but actual Domain Entities/Repositories/Controllers do not exist yet. Apply the related items only once an actual implementation exists, and do not flag nonexistent structures as problems.
