# GETI-Server

## 브랜치 전략 (Git Flow)

- `main`: 운영/배포 가능한 안정 버전입니다. 직접 Push하지 않습니다.
- `develop`: 다음 개발 버전을 통합하는 기본 개발 브랜치입니다. 직접 Push하지 않습니다.
- 작업 브랜치는 `develop`에서 분기하며 아래 형식을 사용합니다.

  ```text
  feature/{issue-number}-{short-description}
  fix/{issue-number}-{short-description}
  refactor/{issue-number}-{short-description}
  chore/{issue-number}-{short-description}
  docs/{issue-number}-{short-description}
  hotfix/{issue-number}-{short-description}
  ```

  예: `chore/1-project-foundation`

## 협업 절차

1. 작업 전에 GitHub Issue를 먼저 생성합니다.
2. Issue 번호를 포함한 작업 브랜치를 `develop` 기준으로 생성합니다.
3. 작업 후 `develop`을 대상으로 Pull Request를 생성합니다.
4. PR 본문에서 `Closes #{issue-number}` 형식으로 연관 Issue를 연결합니다.
5. 커밋 메시지는 하나의 명확한 작업 단위로 작성합니다.

상세한 컨벤션(커밋 메시지 규칙, 코드 스타일, 리뷰 정책 등)은 별도 문서 또는 후속 PR에서 확장할 예정입니다.

## 빌드

```bash
./gradlew clean test build
```