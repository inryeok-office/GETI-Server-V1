---
description: 현재 Branch의 변경 사항 또는 지정된 Diff를 코드 리뷰한다 (기본적으로 코드 수정 없음)
argument-hint: [리뷰 대상 (선택, 기본은 origin/develop...HEAD)]
---

## 목적

현재 변경 사항을 기능 정확성, 보안, 유지보수성 관점에서 검토한다. 기본적으로 **리뷰만 수행하고 코드는 수정하지 않는다**. 사용자가 수정까지 명시적으로 요청한 경우에만 변경한다.

## 참조

검토 기준은 [`code-review` Skill](../skills/code-review/SKILL.md)을 따른다.

## 대상

`$ARGUMENTS`가 있으면 해당 대상을 리뷰한다. 없으면 다음을 기준으로 한다.

```bash
git diff origin/develop...HEAD
```

## 수행 절차

1. 리뷰 대상 Diff를 확인한다.
2. [`code-review` Skill](../skills/code-review/SKILL.md)의 검토 항목(기능 정확성, 데이터/Transaction, API, 보안, 성능, 유지보수성, Test)을 기준으로 검토한다.
3. 발견 사항마다 중요도(`Critical`, `High`, `Medium`, `Low`, `Suggestion`)를 부여한다.
4. 결과를 보고한다.

## 결과 보고

발견 사항마다 다음을 포함한다.

- 중요도
- 파일과 위치
- 문제
- 영향
- 수정 방향
- 근거

문제가 없더라도 다음을 보고한다.

- 검토한 범위
- 실행한 명령
- 검토하지 못한 영역
- 잔여 위험
