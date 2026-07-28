# Prompt: Prepare PR

검증된 변경을 Commit, Push하고 `develop` 대상 Draft PR로 연결하기 위한 Prompt Template이다. **사용자가 Commit, Push, Draft PR 생성을 명시적으로 요청한 경우에만 사용한다.**

## Placeholder

```text
{ISSUE_NUMBER}
```

## 참조

[`AGENTS.md`](../../AGENTS.md), [`../policies/execution-policy.md`](../policies/execution-policy.md)를 참고한다.

---

## Prompt 본문

```text
GETI-Server 저장소, Issue #{ISSUE_NUMBER} 작업을 Commit, Push하고 develop 대상 Draft PR로 연결해줘.
(이 작업은 내가 명시적으로 요청했을 때만 수행하는 것을 전제로 해.)

다음 순서로 진행해.

1. AGENTS.md와 현재 Issue를 확인해.
2. 현재 Branch를 확인해.
3. gh pr list --head <현재 Branch>로 동일 Head Branch의 기존 PR이 있는지 확인해. 있으면 새로 만들지 말고 기존 PR 본문을 갱신해.
4. git status, git diff로 Working Tree를 확인해.
5. Issue 요구사항과 제외 범위를 대조해.
6. 관련 Test를 실행해.
7. Kotlin 코드를 변경했다면 ./gradlew spotlessCheck와 ./gradlew detekt를 확인해.
8. 전체 Test와 Build를 실행해 (./gradlew clean test build, check에 spotlessCheck/detekt 포함됨).
9. git diff --check를 실행해.
10. Secret과 불필요한 파일이 포함되지 않았는지 확인해.
11. 관련된 파일만 Stage해.
12. Commit 메시지를 작성해: <type>: <한글 작업 내용> 형식, Type은 영문 소문자.
13. Commit해.
14. Push해 (Force Push 금지).
15. develop을 대상으로 Draft PR을 생성해 (또는 기존 PR을 갱신해).
16. PR 본문에 Closes #{ISSUE_NUMBER}로 Issue를 연결해.
17. 저장소에 실제 존재하는 Label만 gh label list로 확인해서 PR에 적용해.
18. Issue 상태 Label을 review로 바꿔.

PR 본문은 다음 구조를 사용해:

## 작업 배경
## 변경 내용
## 주요 판단
## 검증
## 영향 범위
## 제외 범위
## 체크리스트
## 관련 Issue
Closes #{ISSUE_NUMBER}

체크리스트는 실제로 검증한 항목만 체크해.

사용자 요청 없이 Merge하지 마.

마지막에 다음을 보고해: 검증 결과(Test/Build), Commit hash, Push 결과, PR 번호와 URL, base/head, Draft 여부, Issue Label 변경, 남은 작업.
```
