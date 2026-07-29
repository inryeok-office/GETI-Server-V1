# Prompt: Verify Changes

현재 변경 사항이 Issue 요구사항과 저장소 품질 기준을 충족하는지 검증하기 위한 Prompt Template이다.

## 참조

[`AGENTS.md`](../../AGENTS.md)를 참고한다.

---

## Prompt 본문

```text
GETI-Server 저장소의 현재 변경 사항을 검증해줘. 코드를 새로 구현하지 말고 검증만 해.

다음 순서로 진행해.

1. git status로 Working Tree를 확인해.
2. 현재 Branch를 확인해.
3. 현재 Issue의 완료 조건과 제외 범위를 확인해.
4. git diff로 변경 내용을 확인해.
5. git diff --check를 실행해.
6. 변경 범위에 맞는 Test를 실행해 (./gradlew test).
7. Kotlin 코드를 변경했다면 ./gradlew spotlessCheck와 ./gradlew detekt를 실행해 (포맷 위반은 ./gradlew spotlessApply로 정리).
8. 전체 Test를 실행해.
9. Build를 실행해 (./gradlew clean test build, check에 spotlessCheck/detekt/koverVerify 포함됨). 커버리지 Report가 필요하면 ./gradlew koverHtmlReport, ./gradlew koverXmlReport를 별도로 실행해 (check에는 포함 안 됨).
10. 문서를 변경했다면 상대 링크와 경로가 유효한지 확인해.
11. Secret이나 개인 환경 파일이 포함되지 않았는지 확인해.
12. 불필요한 파일이 포함되지 않았는지 확인해.
13. Issue의 완료 조건과 제외 범위에 실제로 부합하는지 대조해.

실패하면 다음 중 어디에 해당하는지 분류해서 보고해:
컴파일 실패, 테스트 실패, Spring Context 실패, 설정 실패, Dependency 실패, 포맷 위반(spotlessCheck), 정적 분석 위반(detekt), 문서/경로 실패, 외부 서비스 실패, 로컬 환경 실패, 권한 실패.

하지 말아야 할 것:
- 실패 Test 삭제
- @Disabled 추가로 우회
- Assertion 제거
- Build Task를 생략하고 성공으로 보고
- 실행하지 않은 검증을 성공으로 처리
- 경고와 오류를 임의로 혼동

마지막에 결과 상태를 다음 중 하나로 명확히 표현해: 완료 / 부분 완료 / 검증 불가 / 실패. 그리고 실행한 명령, 각 결과, 실패 원인, 남은 위험을 함께 보고해.
```
