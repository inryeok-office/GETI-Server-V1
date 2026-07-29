# 코딩 컨벤션 (AI 작업 원칙)

GETI-Server는 아직 초기 구축 단계이며, 확정된 도메인 Architecture가 없다. 이 문서는 현재 실제로 확정된 공통 원칙만 다룬다.

## 공통 원칙

- 기존 코드에 이미 스타일이 존재한다면 새 코드보다 기존 스타일을 우선한다.
- 현재 프로젝트가 사용하는 Java, Kotlin, Spring Boot 버전을 근거 없이 변경하지 않는다.
- 불필요한 추상화를 만들지 않는다. 하나의 사용처만 있는 기능을 미리 일반화하지 않는다.
- Class, 함수, 변수 이름은 역할이 드러나도록 의미 있게 작성한다.
- 이미 구현된 기능을 확인하지 않고 중복 구현하지 않는다.
- 요청받은 작업과 관련 없는 Refactoring을 함께 수행하지 않는다.
- 공개 API(Controller, 외부에 노출되는 함수/클래스 Signature)를 변경하기 전에 호출하는 곳과 영향 범위를 확인한다.
- 의미 없는 주석과 Javadoc/KDoc을 남발하지 않는다. 코드로 설명되지 않는 이유(왜 이렇게 했는지)가 있을 때만 주석을 남긴다.
- 컴파일 경고를 없애기 위해 `@SuppressWarnings` 등을 근거 없이 추가하지 않는다.
- 컴파일 오류나 경고를 숨기거나 우회하지 않고, 원인을 해결한다.
- 사용되지 않는 Class, 빈 Package, 임시로 남겨둔 Placeholder 코드를 만들지 않는다.
- 새 Dependency를 추가하기 전에 기존 Dependency로 대체할 수 있는지 먼저 확인한다.

## 코드 스타일과 정적 분석

Kotlin Source의 포맷은 EditorConfig(`.editorconfig`)와 Spotless(ktlint)로, 정적 분석은 detekt로 자동 검사한다. 도구가 검사하는 항목(공백, Import 정렬, 코드 스멜 등)을 수동으로 재판단하지 않고 `./gradlew spotlessApply`, `./gradlew spotlessCheck`, `./gradlew detekt`를 사용한다. 도구별 설정과 명령은 [`docs/development/code-quality.md`](../development/code-quality.md)를 따른다.

## 모듈 경계 (Spring Modulith)

새 도메인 기능은 Root Package(`team.inreok.geti.getiserver`) 바로 아래의 독립된 Package(Application Module 후보)에 구현한다. 다른 Module의 내부 구현 Package를 직접 참조하지 않고, 순환 의존성을 만들지 않는다. `common`/`global` Package에는 여러 Module이 실제로 공유하는 기술 요소만 두고 특정 도메인 로직을 넣지 않는다. Package를 추가하거나 옮긴 뒤에는 `./gradlew test --tests "*ModularityTest"`로 구조 검증을 실행한다. 세부 원칙과 현재 상태는 [`docs/architecture/modularity.md`](../architecture/modularity.md)를 따른다.

## Configuration과 Profile

환경별로 달라지는 값은 공통 설정(`application.yaml`)에 넣지 않고 `local`/`test`/`prod` Profile 또는 환경 변수로 분리한다. Secret(Password, Token, Key 등)은 코드나 설정 파일에 실제 값으로 작성하지 않고 환경 변수로만 참조하며, 안전하지 않은 기본값을 제공하지 않는다. `.env`는 Spring Boot가 자동으로 읽는 파일이 아니다. Profile 전략, 환경 변수 Naming Convention, `@ConfigurationProperties` 도입 기준은 [`docs/development/configuration.md`](../development/configuration.md)를 따른다.

## 아직 확정되지 않은 규칙

다음 항목은 이 저장소에 아직 도입되지 않았다. 확정된 규칙인 것처럼 강제하거나 임의로 구현하지 않는다.

```text
상세 Package Architecture (api/internal 등 Module 내부 하위 구조)
JPA Entity 규칙
QueryDSL 규칙
공통 API Response 구조
Global Exception 구조
Spring Security 구조
```

위 항목은 추후 Architecture 관련 PR에서 결정되고 문서화될 예정이다. 관련 작업이 필요한 Issue를 받으면, 이 문서가 갱신되기 전까지는 최소한의 구현만 하고 확정된 규칙처럼 문서화하지 않는다.
