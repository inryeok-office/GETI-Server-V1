# GETI-Server

## 브랜치 전략 (Git Flow)

- `main`: 운영/배포 가능한 안정 버전입니다. 직접 Push하지 않습니다.
- `develop`: 다음 개발 버전을 통합하는 기본 개발 브랜치입니다. 직접 Push하지 않습니다.
- `main`, `develop`은 GitHub Branch Protection이 적용되어 있어 직접 Push와 강제 Push, 브랜치 삭제가 차단됩니다. Pull Request는 **작성자 본인이 아닌 다른 리뷰어의 승인 1건 이상**이 있어야 Merge할 수 있으며, 이 규칙은 저장소 관리자에게도 동일하게 적용됩니다(`enforce_admins`).
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

상세한 컨벤션(코드 스타일, 리뷰 정책 등)은 별도 문서 또는 후속 PR에서 확장할 예정입니다.

## Commit Convention

모든 일반 커밋 메시지는 Conventional Commits 형식을 사용하며, 작업 내용은 한글로 작성합니다.

```text
<type>: <한글 작업 내용>
```

예시:

```text
feat: 채용 공고 북마크 기능 추가
fix: 로그인 Token 재발급 오류 수정
refactor: 사용자 권한 검증 로직 분리
chore: 프로젝트 기본 설정 구성
docs: Git Flow 협업 규칙 추가
test: 공고 조회 통합 테스트 추가
config: 로컬 실행 환경 설정 정리
build: Gradle 빌드 설정 개선
ci: GitHub Actions 빌드 워크플로 추가
perf: 공고 검색 쿼리 성능 개선
style: 코드 포맷 정리
revert: 사용자 조회 변경 사항 되돌리기
```

기술명, 클래스명, 라이브러리명과 같은 고유명사는 영문 표기를 유지할 수 있습니다.

```text
build: QueryDSL 의존성 및 생성 경로 설정
config: PostgreSQL 연결 환경변수 추가
```

허용 Type:

| Type       | 용도                  |
| ---------- | ------------------- |
| `feat`     | 새로운 기능 추가           |
| `fix`      | 버그 수정               |
| `refactor` | 기능 변화 없는 코드 구조 개선   |
| `chore`    | 일반 유지보수 및 기타 작업     |
| `docs`     | 문서 변경               |
| `test`     | 테스트 코드 및 테스트 환경 변경  |
| `config`   | 애플리케이션 및 개발 환경 설정   |
| `build`    | 빌드 시스템 및 의존성 변경     |
| `ci`       | CI/CD 설정 변경         |
| `perf`     | 성능 개선               |
| `style`    | 코드 동작에 영향 없는 스타일 변경 |
| `revert`   | 이전 커밋 되돌리기          |

작성 규칙:

- Type은 영문 소문자로, 뒤에 콜론과 공백을 붙여 작성합니다.
- 제목 설명은 한글로 작성하고, 끝에 마침표를 붙이지 않습니다.
- 한 커밋에는 하나의 논리적 변경만 담습니다.
- `수정`, `작업`, `변경`처럼 의미가 불분명한 단어만 사용하지 않고, 무엇을 왜 변경했는지 알아볼 수 있게 작성합니다.
- `WIP`, `update`, `수정함`, `최종`, `진짜 최종` 같은 메시지는 사용하지 않습니다.
- Issue 종료는 커밋이 아닌 Pull Request 본문의 `Closes #번호`로 처리합니다. 필요한 경우에만 Footer에 `Refs: #번호`를 추가합니다.

Merge 시 불필요한 Merge Commit을 줄이기 위해 Squash and Merge 또는 Rebase and Merge를 우선 검토합니다. 어떤 방식을 저장소의 기본 Merge 옵션으로 강제할지(Merge Button 옵션 제한 등)는 후속 PR(Repository 정책 작업)에서 다룹니다. Squash Merge를 사용하는 경우 최종 Squash Commit도 한글 규칙을 따릅니다. (예: `chore: 프로젝트 기본 및 협업 기반 설정 (#1)`)

커밋 메시지 자동 검증(Commitlint 등)은 이번 단계에서 도입하지 않으며, 후속 CI/Repository 정책 작업에서 별도로 검토합니다.

## 라벨 체계

Issue와 Pull Request는 `{emoji} {label-name}` 형식의 라벨을 사용합니다. 작업 유형, 작업 상태, 우선순위, 작업 규모, 영향 영역, 특별 관리 분류로 구성되어 있으며 전체 목록은 저장소의 [Labels 페이지](../../labels)에서 확인할 수 있습니다.

- 상태 라벨(`📋 backlog` ~ `⛔ blocked`)은 Issue에만 적용합니다. 현재는 GitHub Project를 별도로 확인하지 못해 라벨로 상태를 관리하며, 추후 GitHub Project를 도입하면 상태 관리 방식을 정리할 예정입니다.
- 우선순위 라벨은 Issue 하나당 하나만 사용합니다.
- 영향 영역(`area:`) 라벨은 Issue 하나에 여러 개를 적용할 수 있습니다.

## AI 기반 개발

Claude Code, Codex 등의 AI 개발 도구를 사용할 때는 [`AGENTS.md`](./AGENTS.md)와 [`docs/ai`](./docs/ai/README.md)의 규칙을 따릅니다.

## 빌드

```bash
./gradlew clean test build
```