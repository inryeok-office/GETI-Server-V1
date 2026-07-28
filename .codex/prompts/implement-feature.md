# Prompt: Implement Feature

현재 Issue 범위 안에서 기능 또는 설정 변경을 구현하고 검증하기 위한 Prompt Template이다.

## Placeholder

```text
{ISSUE_NUMBER}
{FEATURE_DESCRIPTION}
```

## 참조

[`AGENTS.md`](../../AGENTS.md), 관련 [`.claude/rules/spring-boot.md`](../../.claude/rules/spring-boot.md)(Claude Code용이지만 Spring Boot 원칙은 도구 공통), [`../policies/execution-policy.md`](../policies/execution-policy.md)를 참고한다.

---

## Prompt 본문

```text
GETI-Server 저장소, Issue #{ISSUE_NUMBER} 작업 Branch에서 다음을 구현해줘.

구현 내용: {FEATURE_DESCRIPTION}

먼저 AGENTS.md를 확인하고, 아래 순서로 진행해.

1. 현재 Issue와 작업 Branch를 확인해.
2. 관련 코드, Test, 설정, 문서를 검색해.
3. 기존 유사 구현과 Naming, Package 구조를 확인해.
4. 영향 범위를 분석해.
5. 최소 변경 계획을 세워. 범위가 크면 논리적 단계로 나눠.
6. 계획에 따라 구현해.
7. 관련 Test를 작성하거나 수정해.
8. 변경 범위에 해당하는 Test를 실행해 (./gradlew test).
9. Kotlin 코드를 변경했다면 ./gradlew spotlessApply로 포맷을 정리하고 ./gradlew detekt로 정적 분석을 확인해.
10. 전체 Test와 Build를 실행해 (./gradlew clean test build, check에 spotlessCheck/detekt 포함됨).
11. git diff --check를 실행해.
12. Diff를 직접 리뷰해.

하지 말아야 할 것:
- Issue 범위 밖 기능 추가
- 관련 없는 Refactoring
- 기존 구현을 확인하지 않은 중복 구현
- 사용되지 않는 Class 생성
- 미래 기능을 위한 Placeholder 생성
- 핵심 요구사항을 TODO로 남기고 완료 처리
- 근거 없는 새 Dependency 추가
- Test 삭제 또는 비활성화
- 컴파일만 통과시키는 임시 구현

Commit과 Push는 내가 명시적으로 요청하기 전까지 하지 마.

마지막에 다음을 보고해: 분석 결과, 구현 내용, 변경 파일, 주요 판단과 가정, Test와 Build 결과, 실행하지 못한 검증, Commit/Push 여부, 남은 문제.
```
