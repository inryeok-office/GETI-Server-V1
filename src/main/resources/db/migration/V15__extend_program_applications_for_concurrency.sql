-- Program 도메인 Phase 3(신청·취소·동시성) 확장.
--
-- 재신청 정책은 사용자 확인 완료: 이력 보존형(취소해도 Row를 남기고, 재신청은 새 Row를 만든다).
-- 동일 학생·Program 조합의 "활성"(status='APPLIED') 신청은 최대 1건만 허용해야 하므로
-- uk_job_applications_active_singleton(V13)과 동일한 방식으로 Partial Unique Index를 건다.
-- Repository의 exists 확인만으로는 동시 요청에서 두 요청 모두 통과할 수 있어(TOCTOU) 이
-- Index가 최종 방어선이고, ProgramServiceImpl은 이와 별도로 Program Row에 대한 Pessimistic
-- Lock으로 정원(capacity) 초과를 막는다(요구사항 11절 동시성).
ALTER TABLE program_applications
    ADD COLUMN form_id BIGINT,
    ADD COLUMN form_version INTEGER,
    ADD COLUMN updated_at TIMESTAMP;

-- 기존 Row(현재는 없지만 향후 재실행 환경 대비)의 updated_at을 applied_at으로 채운 뒤 NOT NULL로 고정한다.
UPDATE program_applications SET updated_at = applied_at WHERE updated_at IS NULL;
ALTER TABLE program_applications ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE program_applications
    ADD CONSTRAINT fk_program_applications_form FOREIGN KEY (form_id)
        REFERENCES forms (id);

CREATE UNIQUE INDEX uk_program_applications_active_singleton
    ON program_applications (program_id, applicant_member_id)
    WHERE status = 'APPLIED';
