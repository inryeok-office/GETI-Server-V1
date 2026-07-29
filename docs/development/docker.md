# Docker 및 로컬 인프라

GETI-Server는 개발자가 PostgreSQL, Redis, MinIO를 로컬 PC에 각각 설치하지 않고 Docker Compose로 동일한 개발 인프라를 실행할 수 있도록 구성되어 있다.

평소 개발 흐름은 다음과 같다.

```text
Docker Compose (compose.yaml)
├── postgres
├── redis
└── minio

Host PC
└── Spring Boot (IntelliJ Run 또는 ./gradlew bootRun)
```

Spring Boot 애플리케이션은 기본적으로 Docker 밖에서 실행한다. 전체 Container 환경을 검증하고 싶을 때만 선택적으로 `app` Compose Profile을 사용해 Spring Boot까지 Container로 실행할 수 있다.

## 사전 요구사항

- Docker Desktop(Windows/macOS) 또는 Docker Engine(Linux)
- Docker Compose v2 (`docker compose` 명령. `docker-compose`(v1, 하이픈)는 사용하지 않는다)
- Windows에서는 Linux Container Mode가 필요하다(Docker Desktop 기본값)

이 문서의 명령은 Docker Desktop 4.48.0 / Engine 28.5.1 / Compose v2.40.2(Linux Container, x86_64)에서 실제로 검증했다.

## 초기 설정

`.env.example`을 복사해 `.env`를 만든다. `.env`가 없어도 `compose.yaml`에 동일한 기본값이 있어 바로 실행할 수 있지만, Port 충돌 등으로 값을 바꾸고 싶다면 `.env`를 사용한다.

PowerShell:

```powershell
Copy-Item .env.example .env
```

Git Bash:

```bash
cp .env.example .env
```

`.env`는 `.gitignore`에 의해 Commit되지 않는다. Docker Compose는 프로젝트 Root의 `.env`를 변수 치환에 사용하지만, 이는 Spring Boot가 `.env`를 읽는 것과 무관하다([`configuration.md`](./configuration.md) 참고). Spring Boot는 `.env`를 자동으로 읽지 않는다.

## 인프라 실행

```bash
docker compose up -d
```

`postgres`, `redis`, `minio` 3개 Service만 실행된다. Spring Boot Container는 포함되지 않는다.

## 상태 확인

```bash
docker compose ps
```

`STATUS`가 `healthy`가 될 때까지 몇 초 정도 걸릴 수 있다.

## 로그 확인

```bash
docker compose logs -f
```

특정 Service만:

```bash
docker compose logs -f postgres
docker compose logs -f redis
docker compose logs -f minio
```

## Spring Boot 실행

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

또는 IntelliJ에서 `GetiServerApplication`을 실행한다(Run Configuration에 `SPRING_PROFILES_ACTIVE=local` 환경 변수 설정).

이번 PR 시점에는 Spring Boot가 PostgreSQL/Redis/MinIO에 실제로 연결하지 않는다. Application 연동은 PR 8(Persistence)에서 진행한다.

## 전체 Container 환경 실행 (선택)

Spring Boot까지 Container로 실행해 전체 환경을 검증하고 싶을 때만 사용한다. 평소 개발에는 사용하지 않는다.

```bash
docker compose --profile app up -d --build
```

`app` Service는 `postgres`/`redis`/`minio`가 모두 `healthy` 상태가 된 뒤 시작된다. Source Directory를 Bind Mount하지 않으므로 코드를 바꾸면 다시 `--build`해야 한다. Hot Reload가 필요한 평소 개발에는 IntelliJ 또는 `bootRun`을 사용한다.

## 중지

```bash
docker compose stop
```

Container는 남아 있고 Named Volume 데이터도 유지된다. 다시 시작하려면:

```bash
docker compose start
```

## Container 제거

```bash
docker compose down
```

Container와 Network는 제거되지만 Named Volume(`postgres-data`, `redis-data`, `minio-data`)의 데이터는 유지된다.

## 완전 초기화 (⚠ 데이터 삭제)

```bash
docker compose down -v
```

**이 명령은 PostgreSQL, Redis, MinIO의 로컬 데이터를 모두 삭제하는 파괴적 명령이다.** 의도적으로 로컬 환경을 초기화하려는 경우에만 실행한다.

## Service별 접속 정보

| Service | Host 접속 | Container 내부 접속(`app` Profile용) | User/Credential |
| --- | --- | --- | --- |
| PostgreSQL | `localhost:${POSTGRES_PORT:-5432}` | `postgres:5432` | `.env`/`compose.yaml`의 `POSTGRES_USER`/`POSTGRES_PASSWORD` (기본값은 아래 참고) |
| Redis | `localhost:${REDIS_PORT:-6379}` | `redis:6379` | 인증 없음(Local 전용) |
| MinIO API | `http://localhost:${MINIO_API_PORT:-9000}` | `http://minio:9000` | `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` |
| MinIO Console | `http://localhost:${MINIO_CONSOLE_PORT:-9001}` | 해당 없음 | 위와 동일 |

Host에서 실행하는 Spring Boot(`bootRun`, IntelliJ)는 `localhost` 열을, Compose의 `app` Container에서 실행하는 Spring Boot는 Container 내부 접속(Service 이름) 열을 사용한다. Docker Compose 기본 Network가 Service 이름을 DNS처럼 해석해 준다.

## Local Credential

`compose.yaml`은 `.env`가 없어도 바로 실행되도록 아래 기본값을 갖고 있다.

| 환경 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `POSTGRES_DB` | `geti` | Database 이름 |
| `POSTGRES_USER` | `geti` | PostgreSQL User |
| `POSTGRES_PASSWORD` | `geti-local-only` | PostgreSQL Password |
| `MINIO_ROOT_USER` | `geti-local` | MinIO Root User |
| `MINIO_ROOT_PASSWORD` | `geti-local-only` | MinIO Root Password |

**이 값들은 Local 개발 전용이며 운영 환경이나 공유 환경에서 절대 재사용하지 않는다.** 실제 값은 `.env.example`과 `compose.yaml`에서 확인할 수 있으며 둘 다 실제 운영 Secret이 아니다. 운영 Credential 관리 방식은 [`configuration.md`](./configuration.md)의 Secret 관리 정책을 따른다.

## Port 충돌 해결

이 저장소를 포함해 여러 프로젝트를 동시에 개발하는 PC에서는 `5432`, `6379`, `9000`, `9001` Port가 이미 다른 프로젝트의 Container에서 사용 중일 수 있다. 이 경우 `.env`에서 Host Port만 바꾼다(Container 내부 Port와 Service 이름은 그대로다).

```dotenv
POSTGRES_PORT=15432
REDIS_PORT=16379
MINIO_API_PORT=19000
MINIO_CONSOLE_PORT=19001
```

## 문제 해결

- **Docker Daemon이 실행되지 않음**: Docker Desktop을 실행한 뒤 `docker info`로 확인한다.
- **Port 충돌**(`port is already allocated`): 위 "Port 충돌 해결"을 참고한다.
- **Health Check 실패가 계속됨**: `docker compose logs <service>`로 원인을 확인한다.
- **오래된 Container/Volume으로 인한 이상 동작**: `docker compose down`(데이터 유지) 후 `docker compose up -d`로 재시작한다. 그래도 해결되지 않고 로컬 데이터를 포기해도 된다면 `docker compose down -v`로 완전히 초기화한다.
- **PostgreSQL 18 Volume Mount 경고**: 이 저장소의 `compose.yaml`은 PostgreSQL 18+ 공식 Image의 권장 방식대로 `postgres-data` Volume을 `/var/lib/postgresql`(상위 Directory)에 Mount한다. 만약 과거에 `/var/lib/postgresql/data`에 직접 Mount하던 Volume을 재사용하면 PostgreSQL이 시작을 거부할 수 있다. 이 경우 `docker compose down -v`로 초기화한다.
- **MinIO Console 접속 안 됨**: `docker compose ps`로 `minio` Service가 `healthy`인지, Console Port(`9001`)가 다른 프로세스와 충돌하지 않는지 확인한다.
- **Windows에서 파일 공유 문제**: 이 구성은 Host 절대 경로를 Bind Mount하지 않으므로(Named Volume만 사용) 일반적으로 Windows 파일 공유 설정이 필요하지 않다.

## `app` Profile Dockerfile

Root의 `Dockerfile`은 `compose.yaml`의 `app` Service 전용이며 Local 전체 환경 검증 목적이다. 운영 배포용 Image 전략은 아직 확정하지 않았다.

- Build Stage: `eclipse-temurin:25.0.3_9-jdk-alpine` — `./gradlew bootJar` 실행(Test는 `bootJar`가 기본으로 실행하지 않아 별도로 Skip 옵션을 주지 않았다)
- Runtime Stage: `eclipse-temurin:25.0.3_9-jre-alpine` — Build Stage에서 생성한 Jar만 복사
- Root가 아닌 `spring` User로 실행
- Application Health Check는 아직 구성하지 않았다. `/actuator/health` 등 실제 Health Endpoint가 없는 상태에서 존재하지 않는 URL을 Health Check에 사용하지 않기 위함이다. Spring Boot Actuator가 도입되면 재검토한다.

```bash
docker build -t geti-server:local .
```

또는:

```bash
docker compose build app
```

## Docker Image 버전 선택 근거

| Service | Image | 선택 이유 |
| --- | --- | --- |
| PostgreSQL | `postgres:18.4-alpine` | 공식 Image, 확인 시점 최신 Stable Major(18), Alpine으로 Image 크기 최소화 |
| Redis | `redis:8.8.1-alpine` | 공식 Image, 확인 시점 최신 Stable |
| MinIO | `minio/minio:RELEASE.2025-09-07T16-13-09Z` | 공식 Image, 확인 시점(2026년 7월) Docker Hub에 게시된 가장 최근 Release. MinIO의 Docker Hub 공개 Image가 2025-09-07 이후로는 새 Release를 게시하지 않은 상태였다 |
| Dockerfile Base | `eclipse-temurin:25.0.3_9-jdk-alpine` / `25.0.3_9-jre-alpine` | 프로젝트 Java Toolchain(25)과 동일한 Major Version, 공식 Temurin Image, Patch까지 고정 |

모든 Image는 `latest`나 Major-only Tag가 아닌 특정 Patch/Release Tag로 고정했다.
