-- Application Phase 5(Epic #75, Issue #133): 지원서 상태 이력과 제출 Snapshot을 보강한다.
--
-- job_application_status_histories: 지원서 상태가 바뀌는 모든 지점(학생 SUBMIT/REQUEST_EDIT/
-- RESUBMIT/WITHDRAW, 교사 ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)에 이력 1건을 남긴다.
-- action은 학생 Action(JobApplicationAction)과 교사 Action(JobApplicationAdminAction) 두
-- 서로 다른 Enum을 모두 담아야 해서 관계형 FK 없이 그 이름(.name) 그대로 저장한다(status
-- Column과 동일한 VARCHAR + JPA @Enumerated(STRING) 관례를 action에는 적용하지 않는 이유).
-- application_id는 같은 Application Module 안이라 물리 FK를 건다.
CREATE TABLE job_application_status_histories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_applications (id),
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_member_id BIGINT NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_application_status_histories_application_id
    ON job_application_status_histories (application_id);

-- job_application_submissions: SUBMIT/RESUBMIT 시점의 답변(answers)을 불변으로 고정한다.
-- job_applications.answers는 그 이후 saveDraft로 재작성될 수 있는 "현재 값"이라 재제출 이력을
-- 그대로 재구성할 수 없다(요구사항 "Snapshot" 절). form_id/form_version은 createDraft 이후
-- 바뀌지 않아 job_applications 쪽 값으로 Form 질문 구조(FormVersion.schemaData)를 항상
-- 재현할 수 있고, 지원자 정보(applicant_*)도 초안 생성 시점에 고정돼(V12 Migration 주석 참고)
-- 이미 제출 시점 기준과 동일하므로 이 Table에서 중복 저장하지 않는다.
CREATE TABLE job_application_submissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_applications (id),
    submission_number INTEGER NOT NULL,
    form_id BIGINT,
    form_version INTEGER,
    answers JSONB NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_job_application_submissions_application_number
        UNIQUE (application_id, submission_number)
);
