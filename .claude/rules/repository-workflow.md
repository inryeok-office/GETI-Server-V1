# Repository Workflow (Claude Code)

Claude Code가 GETI-Server 저장소에서 작업할 때 따르는 실행 절차다. [`AGENTS.md`](../../AGENTS.md)의 공통 Workflow(`docs/ai/workflow.md`)를 Claude Code 관점의 구체적인 행동 규칙으로 정리한다.

## 작업 시작

다음을 확인한다.

```bash
git status
git branch --show-current
git log --oneline --decorate -10
```

필요한 경우:

```bash
gh issue view {issue-number}
gh pr status
```

확인 사항:

- 올바른 작업 Branch인지
- 미커밋 변경이 있는지, 있다면 누구의 변경인지
- 선행 작업(다른 PR, Issue)이 실제로 반영되어 있는지
- 현재 Issue의 완료 조건과 제외 범위

## 탐색 우선

파일을 수정하기 전에 다음을 수행한다.

- 관련 파일 검색
- 유사 구현 검색
- 기존 테스트 검색
- 기존 Naming과 Package 구조 확인
- Build 설정 확인
- 관련 문서(`AGENTS.md`, `docs/ai/`, `README.md`) 확인

코드를 먼저 생성한 뒤 저장소 구조에 맞추는 방식은 금지한다.

## 계획 수립

수정 전에 다음을 정리한다.

- 변경 목적
- 변경 파일
- 영향 범위
- 테스트 계획
- 제외할 변경
- 주요 가정

간단한 작업에는 과도한 설계 문서를 만들지 않는다.

## 구현

- 최소 변경으로 요구사항을 충족한다.
- 관련 없는 파일을 수정하지 않는다.
- 기존 패턴을 우선한다.
- 임시 Code와 빈 Class를 생성하지 않는다.
- 핵심 요구사항을 TODO로 남기지 않는다.
- 새 Dependency를 추가하기 전에 기존 대안을 확인한다.

## 검증

- 변경 범위에 해당하는 Test를 실행한다.
- 전체 Test와 Build를 실행한다.
- `git diff --check`를 실행한다.
- Diff를 직접 리뷰한다.
- Secret과 임시 파일이 포함되지 않았는지 확인한다.

## Commit 및 Push

- 사용자의 요청 또는 현재 Prompt의 명시적 요청이 있을 때만 수행한다.
- 관련된 파일만 Stage한다.
- Commit Type은 영문, 설명은 한글로 작성한다 ([`git-and-github.md`](./git-and-github.md) 참고).
- Push 전 현재 Branch를 확인한다.
- Force Push하지 않는다.

## 자율 판단 기준

Claude Code가 불필요하게 모든 사소한 결정을 질문하지 않도록 다음을 따른다.

- 기존 코드와 명세로 안전하게 판단 가능한 사항은 스스로 결정한다.
- 단순 Naming, 파일 배치처럼 기존 패턴으로 결정 가능한 사항은 질문하지 않는다.
- 중요한 제품 정책, 데이터 정책, 보안 정책이 불명확할 때만 질문한다.
- 안전한 기본값을 선택한 경우 완료 보고에 가정을 명시한다.
- 작업을 진행할 수 있는데 사소한 선택을 이유로 중단하지 않는다.

단, 위험한 Git 작업, Secret 접근, 운영 데이터 변경은 자율 판단으로 진행하지 않고 [`security.md`](./security.md)를 따른다.
