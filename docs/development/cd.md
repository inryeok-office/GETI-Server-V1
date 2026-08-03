# CD (배포) 및 Discord 배포 알림

GETI-Server의 실제 배포 자동화(CD)와 Discord 배포 알림을 다룬다. [`ci.md`](./ci.md)는 "CD(배포)는 이 문서의 범위가 아니다"라고 명시하고 있어, CD는 이 문서가 별도로 담당한다.

## Workflow

`.github/workflows/cd.yml` (Workflow 이름: `CD`)

### Trigger

```yaml
on:
  push:
    branches: [develop]
```

`develop` Branch에 Push될 때만 실행된다(Pull Request Event로는 실행되지 않는다 — PR/Draft PR 생성이 실제 배포를 유발하지 않는다). `main`에 대한 CD는 아직 구성되어 있지 않다.

### 권한과 동시 실행

```yaml
permissions:
  contents: read

concurrency:
  group: cd-deploy-develop
  cancel-in-progress: false
```

연속된 `develop` Push가 EC2에서 동시에 `git reset`/`docker compose build`를 실행해 서로 간섭하지 않도록 한 번에 하나씩만 배포되게 한다. `cancel-in-progress: false`로 두어, 진행 중인 배포가 새 Push로 임의로 취소되어 Container가 절반만 Build된 상태로 남는 것을 피한다(대신 다음 배포가 순서대로 대기한다).

## 배포 방식 (현재 상태와 알려진 제약)

CD는 별도 Container Registry(GHCR/ECR 등)를 사용하지 않는다. EC2 서버에 SSH로 접속해 `~/GETI-Server`에서 직접 `git reset --hard origin/develop` 후 `docker compose --profile app up -d --build`로 Source Build한다. 이 `compose.yaml`/`Dockerfile`은 원래 로컬 전체 Container 환경 검증용으로 만들었지만, 현재 CD가 그대로 재사용하고 있다([`docker.md`](./docker.md#app-profile-dockerfile), [`web-api.md`](./web-api.md#docker-연계) 참고). Registry 기반 운영 Image 전략은 아직 확정되지 않았으며, 이번 PR의 범위가 아니다.

서버의 실제 `.env`/`SPRING_PROFILES_ACTIVE` 설정은 저장소에서 확인할 수 없다. `compose.yaml`의 `app` Service는 `SPRING_PROFILES_ACTIVE`가 지정되지 않으면 `local` Profile 기본값(`DEBUG` Logging, 개발용 JWT Secret 기본값 등)으로 기동된다 — 서버가 이 값을 실제로 재정의하는지는 이번 PR에서 확인하지 못했다(아래 "알려진 위험" 참고).

## 배포 절차

`deploy` Job의 SSH Script가 아래 순서로 실행된다(기존 EC2 Secret 재사용, 새 SSH Secret을 추가하지 않았다).

```text
1. git fetch origin develop && git reset --hard origin/develop
2. 배포 Metadata 생성(DEPLOY_SHA, APP_BUILD_TIME, APP_VERSION)
3. docker compose --profile app up -d --build (Metadata를 Container 환경변수로 전달)
4. Readiness Check(/actuator/health/readiness, 5초 간격 최대 24회 = 최대 120초)
5. /actuator/info 호출 확인
6. 실행 중인 Container의 APP_GIT_SHA와 배포 대상 SHA(DEPLOY_SHA) 전체 문자열 비교
```

SSH 명령 성공이나 Container 실행 성공만으로 배포를 성공으로 판단하지 않는다 — Readiness와 Git SHA 검증이 모두 성공해야 Job이 성공한다.

### 배포 Metadata

```bash
DEPLOY_SHA=$(git rev-parse HEAD)
APP_BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
APP_VERSION=$(grep -m1 '^version = ' build.gradle.kts | sed -E 's/version = "([^"]+)"/\1/')
```

`APP_VERSION`은 Gradle을 실행해 출력을 Parsing하는 대신(느리고 실패하기 쉬움) `build.gradle.kts`의 `version = "..."` 줄을 직접 읽는다. `APP_ENVIRONMENT`는 `develop`로 고정한다(이 Workflow가 `develop` 배포 전용이기 때문). 이 세 값과 `APP_ENVIRONMENT`는 `sudo env VAR=value ... docker compose up`처럼 `env` 명령으로 전달한다 — `sudo -E`가 아니라 `env`를 쓰는 이유는 서버의 `sudoers` 설정이 `-E`(호출자 환경 변수 보존)를 허용하는지 확인할 수 없어서다(`env` 명령 자체는 별도 `sudoers` 설정 없이 항상 지정한 값을 하위 프로세스 환경으로 전달한다). Secret 값과는 절대 혼합하지 않는다(DB Password 등은 서버의 기존 `.env`/`compose.yaml` 설정을 그대로 사용).

### Readiness Check

`/actuator/health/readiness`를 Container 내부에서 `wget`으로 호출한다(Runtime Image에 `curl`이 없어 BusyBox `wget`을 사용, [`docker.md`](./docker.md) 참고).

- HTTP 200만 성공으로 처리한다.
- 503, Connection Refused, Timeout은 5초 간격으로 재시도한다.
- 최대 24회(120초) 실패하면 배포 실패로 처리하고 `docker compose logs app --tail=50`을 출력한다.

### Git SHA 검증

서버에 `jq`/Python 등 별도 도구가 설치되어 있다고 가정하지 않는다. `/actuator/info`의 JSON을 직접 Parsing하지 않고, 실행 중인 `app` Container의 `APP_GIT_SHA` 환경변수를 `docker compose exec app printenv APP_GIT_SHA`로 직접 읽어 배포 대상 SHA(`DEPLOY_SHA`)와 전체 문자열로 비교한다.

- 완전 일치만 성공으로 인정한다(축약 SHA 비교 없음).
- `APP_GIT_SHA`가 비어 있거나 `unknown`이면 실패한다.
- 값이 다르면(이전 배포가 남아 있는 경우 등) 실패한다. `APP_GIT_SHA`는 Docker Build Argument로 Image에 반영되므로(`Dockerfile`), SHA가 바뀌면 Image도 바뀌어 `docker compose up -d --build`가 실제로 새 Container를 만든다 — 이전 Container가 재사용돼 그대로 남아 있는 상황을 이 비교가 잡아낸다.
- `/actuator/info`가 정상 응답하는지도 별도로 확인한다(위 SHA 비교와는 독립적인 신호).

`/actuator/info`의 `deployment.gitSha` Field가 실제로 `APP_GIT_SHA` 환경변수와 같은 값을 반환하는지는 Application Test([`DeploymentInfoContributorTest`](../../src/test/kotlin/team/inreok/getiserver/global/health/DeploymentInfoContributorTest.kt), [`HealthEndpointTest`](../../src/test/kotlin/team/inreok/getiserver/global/health/HealthEndpointTest.kt))가 보장한다.

## Discord CD 배포 알림

CI의 `DISCORD_CI_WEBHOOK_URL` 기반 알림([`ci.md`](./ci.md#discord-ci-알림))과 동일한 안전한 JSON 생성 방식(jq, `allowed_mentions.parse: []`, Webhook Secret 없으면 Skip, 전송 실패가 결과를 바꾸지 않음)을 그대로 따르되, CD 전용 Secret을 분리해 사용한다.

| GitHub Secret | 용도 |
| --- | --- |
| `DISCORD_CI_WEBHOOK_URL` | CI 알림 전용(이번 PR에서 사용하지 않음, 값을 변경하지 않았다) |
| `DISCORD_CD_WEBHOOK_URL` | CD 배포 알림 전용(신규) |

같은 Discord 채널을 가리키더라도 두 Secret 이름을 혼용하지 않는다.

### 알림 상태

| 상태 | 전송 시점 | Job |
| --- | --- | --- |
| `started` | 실제 배포 Step 직전 | `notify-start` |
| `success` | Readiness와 Git SHA 검증이 모두 성공한 뒤 | `notify-success` (`if: needs.deploy.result == 'success'`) |
| `failure` | 배포 Step(Readiness/SHA 검증/Container 실행 중 하나) 실패 후 | `notify-failure` (`if: always() && needs.deploy.result == 'failure'`) |
| `cancelled` | Workflow Run 취소 | `notify-cancelled` (`if: always() && (cancelled() || needs.deploy.result == 'cancelled')`) |

Embed는 Repository/Environment(`develop`)/Branch/Commit/Actor/Workflow/Status/Workflow Run URL/시각을 공통으로 포함하고, `success`는 Readiness·Git SHA 검증 결과를, `failure`는 실패 사유(Workflow Log 안내)를 추가로 포함한다.

### 안전 정책

- Payload는 `jq -n --arg ...`로 생성한다(Shell 문자열 연결로 JSON을 직접 조립하지 않음). Commit Message나 Actor에 특수문자(따옴표, 개행 등)가 있어도 `jq`가 안전하게 Escape한다.
- 모든 Payload는 `allowed_mentions: { parse: [] }`을 포함해 Discord Mention을 발생시키지 않는다.
- `DISCORD_CD_WEBHOOK_URL`은 Step 환경변수로만 전달하고, `echo`나 `set -x`로 출력하지 않는다. Payload에도 포함되지 않는다.
- Secret이 없으면 알림을 Skip하고 `GITHUB_STEP_SUMMARY`에 안내를 남기며, Workflow를 실패시키지 않는다.
- Discord HTTP 전송 실패(Timeout, 4xx/5xx)도 배포 결과를 바꾸지 않는다 — `GITHUB_STEP_SUMMARY`에 경고만 남긴다.
- `notify-success`/`notify-failure`/`notify-cancelled`는 서로 배타적인 `if` 조건이라 중복 전송되지 않는다.

### 알려진 제약

- **취소 알림 보장 안 됨**: GitHub Actions가 Workflow Run을 강제 취소하면 대기 중인 Job(`notify-cancelled` 포함)이 예약되지 않고 즉시 종료될 수 있다. 취소 알림은 Best Effort이며 100% 보장되지 않는다.
- **실제 Discord 전송 미검증**: `DISCORD_CD_WEBHOOK_URL`이 아직 등록되지 않아, 이 PR에서는 Payload가 유효한 JSON을 생성한다는 것만 로컬에서 확인했고 실제 Discord Webhook 호출은 수행하지 않았다.
- **서버 Profile/Secret 구성 미확인**: 위 "배포 방식" 참고. 이번 PR은 Health/CD 검증과 Discord 알림 범위만 다루며, 서버의 실제 Profile/Secret 전달 구조를 바꾸지 않는다.
- **Production CD 없음**: `main` Branch 또는 운영 서버에 대한 CD는 아직 구성되어 있지 않다.

## 필요한 GitHub 설정

| 이름 | 종류 | 값 |
| --- | --- | --- |
| `EC2_HOST` | Secret | 기존(이미 등록됨) |
| `EC2_USER` | Secret | 기존(이미 등록됨) |
| `EC2_SSH_KEY` | Secret | 기존(이미 등록됨) |
| `DISCORD_CD_WEBHOOK_URL` | Secret | **신규** — 미등록 시 알림만 Skip되고 배포는 정상 진행된다 |

Secret 실제 값은 이 문서에 기록하지 않는다. 존재 여부만 `gh secret list`로 확인한다.

## 로컬 검증

```bash
docker compose config --quiet
```

`cd.yml`의 jq 기반 Discord Payload 생성 Logic은 `jq`가 설치된 환경에서 동일한 명령을 직접 실행해 유효한 JSON을 만드는지, 특수문자가 포함된 Actor/Branch 값에서도 깨지지 않는지 확인했다(`jq empty`로 검증). `actionlint`는 이번 검증 시점에 로컬에 설치되어 있지 않아 실행하지 못했고, YAML 문법과 `if`/`needs`/`permissions`/`concurrency` 조건은 수동으로 재검토했다.
