# Security (Claude Code)

Security rules Claude Code observes when handling files, Shell, Dependencies, and Git. Follow [`docs/ai/security-policy.md`](../../docs/ai/security-policy.md) for background and detailed principles. This repository does not yet have an actual Spring Security implementation, and this document does not cover that implementation.

## Secret

- Do not output Secrets, Tokens, Passwords, or API Keys.
- Do not read and output the entire contents of a `.env` file as-is.
- When reporting environment variable values, use a masked form instead of the actual value.
- Do not read and expose, or Commit, the contents of Private Keys, certificates (`*.pem`, `*.key`, `*.p12`), or Credential files.
- Do not use actually working Secret values in examples or documentation.

Use the following form when reporting sensitive information.

```text
OPENAI_API_KEY=<configured>
DATABASE_PASSWORD=<redacted>
JWT_SECRET=<redacted>
```

## User Data

- Do not query real user data.
- Do not use real user information as Test Data.
- Do not directly access or modify the production DB.
- Do not output personal information in logs.
- Do not output user Token or Session information.

## Shell

Do not use the following patterns.

```bash
curl ... | sh
wget ... | sh
eval "$(...)"
rm -rf <unverified path>
```

- Do not run external Scripts whose origin and contents have not been reviewed.
- Do not combine user input or external data into Shell commands as-is without validation.

## Git

Do not run the following without the user's explicit request and confirmation of the impact scope.

```bash
git reset --hard
git clean -fd
git restore .
git checkout -- .
git push --force
git push --force-with-lease
```

## Docker

- Use only official Images with pinned Versions (Patch/Release Tags). Do not use `latest` or Major-only Tags.
- Do not use `privileged`, Docker Socket Mounts, or Host Network.
- Document that Local-only Compose Credentials must not be reused in production.
- The following is a destructive command that deletes Local data. Do not run it without the user's explicit request.

```bash
docker compose down -v
```

Follow [`docs/development/docker.md`](../../docs/development/docker.md) for details.

## Dependency

Check the following before adding a new Dependency.

- Whether it comes from an official or trusted source
- Maintenance status
- Compatibility with the project's Java/Kotlin/Spring Boot versions
- Known security vulnerabilities
- Whether it pulls in unnecessary Transitive Dependencies
- Whether a License review is needed

## Authentication and Authorization

Even at the current stage, where authentication/authorization is not yet implemented, prevent the following in advance.

- Do not add code that disables authentication for testing convenience.
- Do not remove authorization checks or leave temporary code that permits all Endpoints.
- Do not add code that bypasses Token validation.
- Do not delete Security-related Tests.

## Reporting Principles

- Do not silently pass over security-related assumptions or findings; state them in the completion report.
- If you discover a security problem outside the work scope, do not fix it on your own; report it as a candidate follow-up Issue.
