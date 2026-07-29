---
description: 현재 Issue의 기능, 설정 또는 문서 변경을 구현한다
argument-hint: [구현할 내용에 대한 추가 설명 (선택)]
---

## 목적

현재 작업 Branch와 연결된 Issue의 요구사항을 최소 범위로 구현한다. 추가 설명이 필요하면 `$ARGUMENTS`를 참고한다.

## 참조

시작 전 [`AGENTS.md`](../../AGENTS.md), 현재 Issue, 관련 [`.claude/rules/`](../rules/)를 확인한다. 상세 기준은 [`spring-boot-change` Skill](../skills/spring-boot-change/SKILL.md)과 [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md)을 따른다.

## 수행 절차

1. 현재 Issue와 작업 Branch를 확인한다.
2. 관련 코드, Test, 설정, 문서를 검색한다.
3. 기존 유사 구현과 Naming을 확인한다.
4. 영향 범위를 분석한다.
5. 최소 변경 계획을 세운다. 범위가 크면 논리적 단계로 나눈다.
6. 계획에 따라 구현한다.
7. 관련 Test를 작성하거나 수정한다.
8. 변경 범위에 해당하는 Test를 실행한다.
9. Kotlin 코드를 변경했다면 `./gradlew spotlessApply`로 포맷을 정리하고 `./gradlew detekt`로 정적 분석을 확인한다.
10. 전체 Test와 Build를 실행한다 (`check`에 `spotlessCheck`, `detekt`, `koverVerify`가 포함됨).
11. `git diff --check`를 실행한다.
12. Diff를 직접 리뷰한다.
13. 결과를 보고한다.

Commit과 Push는 사용자가 명시적으로 요청한 경우에만 수행한다.

## 금지 사항

- Issue 범위 밖 기능 추가
- 관련 없는 Refactoring
- 기존 구현을 확인하지 않은 중복 구현
- 사용되지 않는 Class 생성
- 미래 기능을 위한 Placeholder 생성
- 핵심 요구사항을 TODO로 남기고 완료 처리
- 근거 없는 새 Dependency 추가
- Test 삭제 또는 비활성화
- 컴파일만 통과시키는 임시 구현

## 결과 보고

- 분석 결과
- 구현 내용
- 변경 파일
- 주요 판단과 가정
- Test와 Build 결과
- 실행하지 못한 검증
- Commit 및 Push 여부
- 남은 문제
