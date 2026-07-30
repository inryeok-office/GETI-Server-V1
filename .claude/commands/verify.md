---
description: 현재 변경 사항이 요구사항과 품질 기준을 충족하는지 검증한다 (Test, Build, 링크, Secret)
---

## 목적

현재 변경 사항을 종합적으로 검증하고 결과를 정확하게 판정한다.

## 참조

상세 기준은 [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md)을 따른다.

## 수행 절차

1. `git status`
2. 현재 Branch 확인
3. `git diff`
4. `git diff --check`
5. 변경 범위에 맞는 Test 실행
6. Kotlin 코드를 변경했다면 `spotlessCheck`, `detekt` 실행 (포맷 위반이 있으면 `spotlessApply`로 자동 정리 후 재확인)
7. 전체 Test 실행
8. Build 실행 (`check`에 `spotlessCheck`, `detekt`가 포함되어 함께 실행됨)
9. Markdown 상대 링크 및 설정 경로 확인
10. Secret 및 개인 환경 파일 포함 여부 확인
11. 불필요한 파일 포함 여부 확인
12. Issue의 완료 조건과 제외 범위 대조
13. 결과 보고

## Gradle 기본 검증

Windows:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat detekt
.\gradlew.bat clean test build
```

Git Bash 또는 Unix:

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew clean test build
```

커버리지 Report 확인이 필요하면 별도로 실행한다(`check`에는 포함되지 않는다).

```bash
./gradlew koverHtmlReport
./gradlew koverXmlReport
```

## 실패 분류

```text
컴파일 실패
테스트 실패
Spring Context 실패
설정 실패
Dependency 실패
포맷 위반 (spotlessCheck)
정적 분석 위반 (detekt)
문서 또는 경로 실패
외부 서비스 실패
로컬 환경 실패
권한 실패
```

## 금지 사항

- 실패 Test 삭제
- `@Disabled` 추가로 우회
- Assertion 제거
- Build Task를 생략하고 성공으로 보고
- 실행하지 않은 검증을 성공으로 처리
- 경고와 오류를 임의로 혼동

## 결과 상태

다음 중 하나로 명확히 표현한다 ([`docs/ai/completion-policy.md`](../../docs/ai/completion-policy.md) 참고).

```text
완료
부분 완료
검증 불가
실패
```
