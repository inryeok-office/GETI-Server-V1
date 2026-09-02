-- Recommendation R2(Issue #148): Score Engine이 계산한 순위와 계산식 버전을 저장할 수 있도록
-- recommendations를 확장한다. 기존 V2 Migration은 수정하지 않는다.
--
-- rank: 같은 (member_id, recommendation_date) Batch 안에서의 순위(1부터 시작). Score
--   Engine이 score DESC -> published_at DESC -> job_id DESC로 이미 확정한 순서를 그대로
--   저장해, 조회 시점에 다시 정렬하지 않고 ORDER BY rank로 읽을 수 있게 한다.
-- algorithm_version: 계산 당시의 RECOMMENDATION_ALGORITHM_VERSION. Weight/계산식이 바뀌어도
--   과거 추천 결과를 구분할 수 있어야 한다(R1 설계 "algorithmVersion" 결정).
--
-- 두 Column 모두 기존 Row가 없는 상태(Recommendation은 아직 Production에서 생성된 적 없는
-- Persistence Skeleton, PR #43)에서 추가하므로 DEFAULT 없이 NOT NULL로 추가해도 안전하다.
ALTER TABLE recommendations
    ADD COLUMN rank INTEGER NOT NULL,
    ADD COLUMN algorithm_version INTEGER NOT NULL;

CREATE INDEX idx_recommendations_member_date_rank
    ON recommendations (member_id, recommendation_date, rank);
