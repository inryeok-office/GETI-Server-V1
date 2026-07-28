# Security (Claude Code)

Claude Code가 파일, Shell, Dependency, Git을 다룰 때 지키는 보안 규칙이다. 배경과 상세 원칙은 [`docs/ai/security-policy.md`](../../docs/ai/security-policy.md)를 따른다. 이 저장소에는 아직 실제 Spring Security 구현이 없으며, 이 문서는 그 구현을 다루지 않는다.

## Secret

- Secret, Token, Password, API Key를 출력하지 않는다.
- `.env` 파일의 전체 내용을 읽어서 그대로 출력하지 않는다.
- 환경변수 값을 보고할 때는 실제 값 대신 마스킹된 형태를 사용한다.
- Private Key, 인증서(`*.pem`, `*.key`, `*.p12`), Credential 파일을 읽어 내용을 노출하거나 Commit하지 않는다.
- 실제로 동작 가능한 Secret 값을 예시나 문서에 사용하지 않는다.

민감정보를 보고할 때는 다음 형태를 사용한다.

```text
OPENAI_API_KEY=<configured>
DATABASE_PASSWORD=<redacted>
JWT_SECRET=<redacted>
```

## 사용자 데이터

- 실제 사용자 데이터를 조회하지 않는다.
- 실제 사용자 정보를 Test Data로 사용하지 않는다.
- 운영 DB에 직접 접근하거나 수정하지 않는다.
- 로그에 개인정보를 출력하지 않는다.
- 사용자 Token, Session 정보를 출력하지 않는다.

## Shell

다음 방식을 사용하지 않는다.

```bash
curl ... | sh
wget ... | sh
eval "$(...)"
rm -rf <검증되지 않은 경로>
```

- 출처와 내용을 검토하지 않은 외부 Script를 실행하지 않는다.
- 사용자 입력이나 외부 데이터를 검증 없이 Shell 명령에 그대로 결합하지 않는다.

## Git

사용자의 명시적 요청과 영향 범위 확인 없이 다음을 실행하지 않는다.

```bash
git reset --hard
git clean -fd
git restore .
git checkout -- .
git push --force
git push --force-with-lease
```

## Dependency

새 Dependency를 추가하기 전에 다음을 확인한다.

- 공식 또는 신뢰할 수 있는 출처인지
- 유지보수 상태
- 프로젝트 Java/Kotlin/Spring Boot 버전과의 호환성
- 알려진 보안 취약점
- 불필요한 Transitive Dependency를 끌고 오지 않는지
- License 검토가 필요한지

## 인증 및 인가

아직 인증/인가가 구현되지 않은 현재 단계에서도 다음을 미리 방지한다.

- 테스트 편의를 위해 인증을 비활성화하는 코드를 추가하지 않는다.
- 인가 검사를 제거하거나 모든 Endpoint를 허용하는 임시 코드를 남기지 않는다.
- Token 검증을 우회하는 코드를 추가하지 않는다.
- Security 관련 Test를 삭제하지 않는다.

## 보고 원칙

- 보안과 관련된 가정이나 발견 사항은 조용히 넘어가지 않고 완료 보고에 명시한다.
- 작업 범위 밖에서 보안 문제를 발견하면 임의로 수정하지 않고 후속 Issue 후보로 보고한다.
