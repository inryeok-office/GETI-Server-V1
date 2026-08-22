-- V30 이전 실행은 Job Upsert 결과를 생성/갱신으로 분리해 저장하지 않았으므로 복원할 수 없다.
-- 과거 Row는 NULL(집계 불가), V30 이후 신규 실행은 Application에서 0부터 실제 결과를 기록한다.
ALTER TABLE collection_runs
    ADD COLUMN created_count INTEGER,
    ADD COLUMN updated_count INTEGER;

ALTER TABLE collection_runs
    ADD CONSTRAINT ck_collection_runs_upsert_counts CHECK (
        (created_count IS NULL AND updated_count IS NULL)
        OR (
            created_count IS NOT NULL
            AND updated_count IS NOT NULL
            AND created_count >= 0
            AND updated_count >= 0
            AND created_count + updated_count <= success_count
        )
    );
