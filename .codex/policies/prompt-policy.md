# Prompt Policy

Codex Prompt를 작성하고 실행할 때 필요한 정보와 품질 기준이다. [`prompts/`](../prompts/) 아래 Template은 이 정책을 기준으로 작성되었다.

## 좋은 Prompt에 포함할 항목

```text
1. 작업 목표
2. 현재 저장소 상태
3. 현재 Issue
4. 완료 조건
5. 제외 범위
6. 선행 작업
7. 분석해야 할 파일
8. 구현 원칙
9. Test 및 Build 명령
10. Git 작업 허용 범위
11. 중단 조건
12. 최종 보고 형식
```

## 입력 변수 (Placeholder)

Prompt Template에서 다음과 같은 명확한 Placeholder를 사용한다.

```text
{ISSUE_NUMBER}
{ISSUE_TITLE}
{BASE_BRANCH}
{WORK_BRANCH}
{FEATURE_DESCRIPTION}
{BUG_DESCRIPTION}
{EXPECTED_BEHAVIOR}
{TARGET_FILES}
{TEST_COMMAND}
{BUILD_COMMAND}
```

Placeholder는 실제 값으로 채운 뒤 사용한다. Placeholder를 값처럼 그대로 실행하지 않는다. 핵심 Placeholder(`{ISSUE_NUMBER}` 등)가 채워지지 않은 상태라면 임의로 값을 추측하지 않고 사용자에게 확인한다.

## Prompt 작성 원칙

- 한 Prompt에서 하나의 논리적 목표만 다룬다.
- 이번 단계에서 하지 않을 작업을 명시한다.
- 기존 코드 분석을 구현보다 먼저 요구한다.
- Test와 Build 명령을 명시한다.
- Commit, Push, PR 수행 허용 여부를 명시한다 (기본은 허용하지 않음).
- 위험 명령(Force Push, `reset --hard` 등)을 금지한다.
- 성공과 실패를 표현하는 보고 형식을 명시한다.
- 실제로 확인 가능한 완료 조건을 사용한다 ("가능한 한 잘"과 같은 모호한 조건을 사용하지 않는다).

## 금지되는 Prompt 방식

다음과 같은 Prompt는 사용하지 않는다.

```text
알아서 전부 구현해
완벽하게 수정해
필요한 건 모두 해
무조건 성공시켜
테스트가 실패하면 우회해
모든 권한을 사용해
문제가 있어도 Commit하고 Push해
```

이런 지시는 범위를 무한정 넓히거나, 실패를 숨기거나, 위험한 권한을 정당화하는 방식으로 이어지기 쉽다.

## 결과 보고

모든 Prompt 실행 결과는 다음을 포함해서 보고하도록 요구한다.

```text
- 분석 결과
- 변경 내용
- 변경 파일
- Test 및 Build 결과
- 실패 또는 미검증 항목
- Commit 및 Push 상태
- 남은 문제
- 주요 가정
```

실행하지 않은 작업이나 검증하지 않은 항목을 완료했다고 보고하지 않는다.
