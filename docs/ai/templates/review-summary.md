# Template: Review 요약

[`docs/ai/code-review.md`](../code-review.md)의 "Finding이 없는 경우"와 "인라인 위치" 절이 사용하는 Template다. 인라인으로 남길 수 없는 내용과 전체 리뷰 결과 요약을 GitHub Pull Request Review 본문(`COMMENT`)에 함께 등록할 때 사용한다.

## 기본 형식

```markdown
## AI 코드리뷰 결과

### 요약

- P0:
- P1:
- P2:
- P3:

### 확인 범위

- 기능:
- API:
- Security:
- Architecture:
- Data/JPA:
- Performance:
- Test:
- Swagger/OpenAPI:

### 실행한 검증

- Build:
- Test:
- Static Analysis:
- Architecture:
- Swagger/OpenAPI:
- Integration:
- Docker:

### 참고한 문서

- 실제로 확인한 문서만 작성

### 결정 필요 사항

- `DECISION_REQUIRED` 항목

### 제한사항

- 실행하지 못한 검증과 이유

### 결론

- Merge를 차단할 수 있는 Finding 존재 여부
```

## 작성 규칙

- 각 항목은 실제로 확인하거나 실행한 내용만 채운다. 확인하지 않은 항목은 비워두지 않고 "확인하지 못함"과 이유를 명시한다.
- P0~P3 개수는 실제로 등록한 인라인 Finding 개수와 일치해야 한다.
- "참고한 문서"에는 실제로 읽은 문서만 나열한다(Notion 접근이 불가능했다면 그 사실을 "제한사항"에 남긴다).
- Finding이 하나도 없으면 "결론"에 다음처럼 작성한다.

```markdown
확인한 변경 범위에서는 Merge를 차단할 만한 Finding을 발견하지 못했습니다.
```

- "버그가 전혀 없음", "완벽하게 안전함", "모든 기능이 정상임", "무조건 Merge 가능"처럼 절대적인 표현은 사용하지 않는다.
- Diff 라인에 직접 연결할 수 없는 문제(여러 파일에 걸친 문제, 설계 수준 우려 등)는 "요약" 또는 "결론"에 기록하고, 인라인 코멘트로 억지로 위치를 지정하지 않는다.
