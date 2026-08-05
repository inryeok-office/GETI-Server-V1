-- Application 도메인 Phase 2(Epic #75, Issue #78): 공고-양식 연결과 지원서 초안·임시저장에
-- 필요한 구조다.
--
-- job_application_forms: 공고 하나당 활성 양식 하나(1:1). jobs 테이블은 건드리지 않고
-- Application 도메인 쪽에 매핑을 둔다(계획 문서 §6.2, 사용자 확인 완료). job_id는 다른 Domain
-- 소유 Table을 가리키는 FK라 물리 제약을 걸지 않고(docs/architecture/erd.md 원칙), form_id는
-- 같은 Application Module 안이라 물리 FK를 건다. 재연결은 UPSERT로 처리한다(job_id가 PK).
CREATE TABLE job_application_forms (
    job_id BIGINT PRIMARY KEY,
    form_id BIGINT NOT NULL,
    linked_by_member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_job_application_forms_form FOREIGN KEY (form_id)
        REFERENCES forms (id)
);

-- job_applications 확장: Form 연결(제출 당시 스냅샷 목적, Phase 3에서 본격 사용)과 지원자
-- 스냅샷을 담는다. 기존 Row가 없어(Foundation 단계에서 아직 실사용되지 않음) NOT NULL 없이
-- 모두 nullable로 추가한다.
ALTER TABLE job_applications ADD COLUMN form_id BIGINT REFERENCES forms (id);
ALTER TABLE job_applications ADD COLUMN form_version INTEGER;
ALTER TABLE job_applications ADD COLUMN privacy_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_applications ADD COLUMN applicant_name VARCHAR(100);
ALTER TABLE job_applications ADD COLUMN applicant_cohort INTEGER;
ALTER TABLE job_applications ADD COLUMN applicant_department VARCHAR(30);
ALTER TABLE job_applications ADD COLUMN applicant_majors JSONB;
ALTER TABLE job_applications ADD COLUMN applicant_desired_job VARCHAR(255);
ALTER TABLE job_applications ADD COLUMN applicant_tech_stacks JSONB;
