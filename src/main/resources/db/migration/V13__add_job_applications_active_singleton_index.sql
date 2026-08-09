-- Application 도메인 Phase 2(Epic #75, Issue #78) PR #79 Review 반영: JobApplicationServiceImpl
-- 의 hasActiveApplication() 확인과 createDraft()의 INSERT 사이에는 DB 잠금이 없어(TOCTOU), 같은
-- 학생이 같은 공고에 거의 동시에 지원서 생성을 두 번 요청하면(중복 클릭 등) 두 요청 모두 확인을
-- 통과해 활성 지원서(job_id, applicant_member_id 조합)가 2개 이상 생길 수 있다.
-- uk_job_applications_job_applicant_attempt(job_id, applicant_member_id, attempt_number)는
-- attempt_number까지 포함해 이 경우를 막지 못한다.
--
-- search_reindex_runs의 uk_search_reindex_runs_active_singleton(V10 Migration)과 같은 방식으로,
-- 활성 상태(요구사항 22절 기준 재지원 가능한 WITHDRAWN/REJECTED 제외) Row가 (job_id,
-- applicant_member_id) 조합당 최대 1건이도록 DB 제약으로 강제한다. 두 번째 INSERT는 Unique 제약
-- 위반으로 실패하고 JobApplicationServiceImpl.createDraft()가 이를
-- ACTIVE_APPLICATION_EXISTS(409)로 변환한다.
CREATE UNIQUE INDEX uk_job_applications_active_singleton
    ON job_applications (job_id, applicant_member_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'EDIT_REQUESTED', 'EDIT_ALLOWED', 'REVISION_REQUESTED', 'APPROVED');
