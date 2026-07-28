---
name: code-review
description: 변경 사항을 기능 정확성, 데이터/Transaction, API, 보안, 성능, 유지보수성, Test 관점에서 체계적으로 검토하는 기준을 다룬다.
---

# Code Review

GETI-Server 변경 사항을 검토할 때 참고하는 상세 기준이다. [`review` Command](../../commands/review.md)가 이 Skill을 참조한다. 기본적으로 리뷰는 코드를 수정하지 않고 발견 사항만 보고한다.

## 검토 항목

### 기능 정확성

- 요구사항 누락
- 잘못된 조건문
- 경계값 처리
- Null 처리
- 예외 흐름
- 상태 변경의 부작용

### 데이터와 Transaction

- Transaction 범위가 적절한지
- 연산의 원자성
- 중복 처리 가능성
- 동시성 문제
- 데이터 정합성
- 재시도가 부작용을 일으키지 않는지

### API

- Request Validation
- Response 호환성
- Status Code
- 공개 API(Signature, 경로 등) 변경 여부
- Error Contract 일관성

### 보안

- 인증 처리
- 인가 처리
- 사용자 입력 검증
- Secret 노출 여부
- 민감정보 로그 출력 여부
- 권한 상승 가능성

### 성능

- 불필요한 반복
- N+1 조회 가능성
- 대량 데이터 처리 방식
- Blocking 호출
- 불필요한 외부 호출

### 유지보수성

- 기존 Pattern과의 일관성
- 중복 코드
- Naming의 명확성
- 책임 분리
- 과도한 추상화
- 불필요한 Dependency
- 남아 있는 Debug Code

공백, Import 정렬 등 순수 포맷 문제는 Spotless(ktlint)가, 코드 스멜/복잡도 등 상당수 정적 분석 항목은 detekt가 이미 `check`에서 자동 검사한다 ([`docs/development/code-quality.md`](../../../docs/development/code-quality.md) 참고). 리뷰에서는 이런 도구가 잡아내지 못하는 논리·설계 문제에 집중하고, 도구가 이미 강제하는 규칙을 중복으로 지적하지 않는다.

### Test

- 정상 경로 검증 여부
- 예외 경로 검증 여부
- 회귀 방지 여부
- 구현 세부사항에 과도하게 결합되어 있는지
- 필요한 Test 누락 여부

## 결과 형식

발견 사항마다 다음을 포함한다.

- 중요도: `Critical` / `High` / `Medium` / `Low` / `Suggestion`
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

## 아직 확정되지 않은 기준

이 저장소는 아직 도메인 로직, API, DB, Security가 구현되어 있지 않다. 관련 항목은 실제로 구현이 생겼을 때부터 적용하고, 존재하지 않는 구조를 문제로 지적하지 않는다.
