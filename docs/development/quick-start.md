# Quick Start (신규 개발자용)

이 저장소를 처음 Clone한 개발자가 로컬 개발 환경을 구성하고 첫 PR을 만들기까지의 실제 명령을 순서대로 안내한다. 각 단계의 세부 정책은 링크된 문서를 따른다. 이 문서는 명령 순서만 안내하며, 정책 자체를 새로 만들지 않는다.

```text
Clone
  → 환경 확인
  → .env 구성
  → Docker 인프라 실행
  → Spring Boot 실행
  → Test
  → Integration Test
  → 전체 검증
  → Issue와 Branch
  → PR 생성
```

## 1. Clone

```bash
git clone https://github.com/inryeok-office/GETI-Server.git
cd GETI-Server
```

## 2. 환경 확인

필요한 도구: Git, Docker Desktop(Windows/macOS) 또는 Docker Engine(Linux), (선택) Java 25 — Gradle Wrapper가 자동으로 Toolchain을 내려받으므로 로컬에 Java가 없어도 대부분 동작한다.

```bash
docker --version
docker compose version
./gradlew --version   # Windows: .\gradlew.bat --version
```

## 3. `.env` 구성

```bash
cp .env.example .env      # PowerShell: Copy-Item .env.example .env
```

`.env`가 없어도 `compose.yaml`과 Spring Boot `local` Profile에 동일한 기본값이 있어 바로 실행할 수 있다. Port 충돌 등으로 값을 바꾸고 싶을 때만 `.env`를 수정한다. `.env`는 Git에 추적되지 않는다. 세부 내용은 [`configuration.md`](./configuration.md), [`docker.md`](./docker.md)를 따른다.

## 4. Docker 인프라 실행

```bash
docker compose up -d
docker compose ps
```

PostgreSQL, Redis, MinIO 3개 Service만 실행된다. `STATUS`가 `healthy`가 될 때까지 몇 초 걸릴 수 있다.

## 5. Spring Boot 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

정상 기동 확인:

```bash
curl http://localhost:8080/actuator/health
```

## 6. Test

```bash
./gradlew test
```

Docker 없이 실행된다. Web Slice Test, Spring Modulith 구조 검증(`ModularityTest`), Configuration Test가 모두 포함된다.

## 7. Integration Test (Docker 필요)

```bash
./gradlew integrationTest
```

PostgreSQL/Redis Testcontainers를 실제로 띄워 검증한다. Docker Desktop이 실행 중이어야 한다.

## 8. 전체 검증

```bash
./gradlew spotlessApply   # 포맷이 흐트러졌다면 자동 정리
./gradlew clean test build
```

`check`(`clean test build`에 포함)가 `spotlessCheck`, `detekt`, `koverVerify`를 자동으로 포함한다. 세부 내용은 [`code-quality.md`](./code-quality.md), [`testing.md`](./testing.md)를 따른다.

## 9. Issue와 Branch

```bash
gh issue list --state open
git switch develop
git pull --ff-only origin develop
git switch -c chore/{issue-number}-{설명}
```

Branch/Commit Convention은 [`docs/ai/git-conventions.md`](../ai/git-conventions.md)를 따른다(Commit Type은 영문, 설명은 한글).

## 10. PR 생성

```bash
git add {변경한 파일만}
git commit -m "chore: 변경 내용을 한글로"
git push -u origin chore/{issue-number}-{설명}
gh pr create --draft --base develop --title "[CHORE] 작업 내용" --body "..."
```

PR을 올리면 GitHub Actions(`CI` Workflow)가 자동으로 Wrapper Validation, Quality(Spotless/detekt), Unit Test, Integration Test, Build, Docker Validation을 실행한다. 결과는 `gh pr checks {pr-number}`로 확인한다. 세부 내용은 [`ci.md`](./ci.md)를 따른다.

## 막히면

| 문제 | 확인할 문서 |
| --- | --- |
| Docker Port 충돌, Container가 뜨지 않음 | [`docker.md`](./docker.md)의 "문제 해결" |
| 환경 변수, Profile, Secret | [`configuration.md`](./configuration.md) |
| PostgreSQL/Redis/Flyway 연결 문제 | [`persistence.md`](./persistence.md) |
| API 응답 형식, 공통 오류 처리 | [`web-api.md`](./web-api.md) |
| CI 실패 원인 분석 | [`ci.md`](./ci.md)의 "실패 시 확인" |
| Package를 어디에 만들어야 하는지 | [`docs/architecture/modularity.md`](../architecture/modularity.md) |
| AI 도구(Claude Code, Codex) 사용 규칙 | [`AGENTS.md`](../../AGENTS.md), [`docs/ai/README.md`](../ai/README.md) |
| Notion 요구사항과 저장소 구현이 다른 것 같을 때 | [`docs/audit/notion-repository-sync.md`](../audit/notion-repository-sync.md) |
