# 코드 품질 및 정적 분석

GETI-Server는 개발자와 AI Agent가 동일한 코드 스타일과 품질 기준을 적용할 수 있도록 다음 도구를 사용한다.

## 적용 도구

| 도구 | 역할 |
| --- | --- |
| [EditorConfig](../../.editorconfig) | Editor/IDE의 기본 파일 형식(인코딩, 개행, 들여쓰기)을 통일한다. |
| [Spotless](https://github.com/diffplug/spotless) | 자동 포맷팅과 포맷 검사를 통합한다. Kotlin Source는 ktlint로 포맷한다. |
| [ktlint](https://github.com/pinterest/ktlint) | Spotless 내부의 Kotlin Formatter/Linter로 사용한다. 별도의 독립 Gradle Plugin은 추가하지 않았다. |
| [detekt](https://detekt.dev/) | Kotlin 전용 정적 분석 도구다. 코드 스멜, 복잡도, 잠재적 오류를 검사한다. |

포맷은 Spotless(ktlint)가 담당하고, detekt는 정적 분석에 집중하도록 역할을 분리했다.

## 자동 포맷 적용

```bash
./gradlew spotlessApply
```

Windows:

```powershell
.\gradlew.bat spotlessApply
```

## 포맷 검사

```bash
./gradlew spotlessCheck
```

## 정적 분석

```bash
./gradlew detekt
```

## 전체 품질 검사

`spotlessCheck`와 `detekt`는 별도 연결 설정 없이 Gradle의 표준 `check` Lifecycle에 이미 포함되어 있다(Spotless, detekt Gradle Plugin이 자동으로 `check`에 연결한다).

```bash
./gradlew check
```

## 전체 검증

```bash
./gradlew clean test build
```

## 권장 로컬 작업 순서

```text
1. 코드 작성
2. spotlessApply   (포맷이 흐트러졌다면)
3. spotlessCheck   (포맷 검사만 빠르게 확인)
4. detekt          (정적 분석만 빠르게 확인)
5. check 또는 clean test build (최종 검증)
```

`check`가 이미 `spotlessCheck`, `detekt`, `test`를 모두 포함하므로, 실패 원인을 빠르게 좁히고 싶을 때만 개별 Task를 먼저 실행한다.

## Report 위치

```text
build/reports/spotless/         Spotless 관련 산출물
build/reports/detekt/detekt.html   detekt HTML Report
build/reports/detekt/detekt.md     detekt Markdown Report
build/reports/detekt/detekt.xml    detekt Checkstyle 형식 Report
build/reports/detekt/detekt.sarif  detekt SARIF Report
```

`build/`는 `.gitignore`에 이미 포함되어 있어 Report가 Git에 커밋되지 않는다.

## detekt 설정

- 버전: `dev.detekt` `2.0.0-alpha.5`가 최신이지만, 이 버전은 Kotlin 2.4.0으로 컴파일되어 있어 프로젝트의 Kotlin 2.3.21과 호환되지 않는다. 반대로 이전 세대 `io.gitlab.arturbosch.detekt` 1.23.8(마지막 안정 릴리스, 2025년 2월)은 Kotlin 2.0.21로 컴파일되어 있어 역시 호환되지 않는다. 실제로 각 버전을 순서대로 실행해 확인한 결과 `dev.detekt` **`2.0.0-alpha.3`**이 Kotlin 2.3.21과 호환되는 것을 확인해 이 버전을 사용한다.
  - 이 버전은 정식 안정 릴리스가 아닌 Alpha이므로, 향후 detekt 2.0 정식 버전이 나오면 재검토가 필요하다.
  - 프로젝트의 Kotlin/Spring Boot 버전을 낮추는 방식으로 문제를 회피하지 않았다.
- 설정 방식: `buildUponDefaultConfig = true`로 detekt 기본 Config를 사용하고, [`config/detekt/detekt.yml`](../../config/detekt/detekt.yml)에는 실제로 필요한 Override 하나만 최소한으로 작성했다(기본 Config 856줄 전체를 복사해 오지 않았다).
- Override 내용: `EmptyFunctionBlock` 규칙을 `**/test/**` 경로에서 제외했다. `@Test fun contextLoads() { }`처럼 Spring Boot의 관용적인 빈 Application Context 스모크 테스트를 오탐으로 잡지 않기 위해서다.
- Main/Test 소스 모두 검사 대상이며, Test 전체를 정적 분석에서 제외하지 않았다.
- Type Resolution: 이번 Alpha 버전에서는 `detektMain`/`detektTest`(Type Resolution 포함) Task가 실험적 표시 없이 제공되지만, 이번 PR에서는 기본 `detekt` Task(Type Resolution 미사용)만 `check`에 연결된 상태로 검증했다. Type Resolution 기반 검사가 필요해지면 후속 Issue에서 별도로 검토한다.
- Baseline: 사용하지 않는다. 현재 Source가 Initializr 수준으로 매우 작아 Baseline 없이 전체 검사를 통과한다.
- Suppression: 위 `EmptyFunctionBlock` 경로 Override 외에 코드 내 `@Suppress`를 사용하지 않았다.

## CI/CD 범위

- 현재는 로컬에서 실행하는 Gradle Task만 구성되어 있다.
- GitHub Actions CI는 테스트 하네스와 나머지 프로젝트 기반 구성이 완료된 뒤 DevOps 담당자와 별도 PR에서 구성한다. CI에서는 이 문서에서 정의한 `spotlessCheck`, `detekt`, `check` Task를 그대로 사용할 예정이다.
- CD는 배포 서버와 운영 환경이 확보된 이후 별도로 구성한다.
