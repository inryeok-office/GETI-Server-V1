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

## 아직 확정되지 않은 규칙

다음 항목은 이 저장소에 아직 도입되지 않았다. 확정된 규칙인 것처럼 강제하거나 임의로 구현하지 않는다.

```text
Spring Modulith Module 경계
상세 Package Architecture
JPA Entity 규칙
QueryDSL 규칙
공통 API Response 구조
Global Exception 구조
Spring Security 구조
```

위 항목은 추후 Architecture 및 Code Quality 관련 PR에서 결정되고 문서화될 예정이다. 관련 작업이 필요한 Issue를 받으면, 이 문서가 갱신되기 전까지는 최소한의 구현만 하고 확정된 규칙처럼 문서화하지 않는다.
