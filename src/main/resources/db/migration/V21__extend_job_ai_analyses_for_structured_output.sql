-- AI Analysis Phase 1(GETI Notion/Figma 확정 계약, Issue #132)이 요구하는 구조화 결과 Column을
-- job_ai_analyses에 추가한다. eligibility_summary/extracted_skills는 각각
-- highSchoolGraduateFit+entryLevelFit(구조화 Enum), requiredSkills+preferredSkills(구조화
-- {techStackId,name} 목록)로 대체되어 더 이상 쓰지 않는다. 이 Migration이 적용되는 시점까지
-- Service/Controller가 없어 실사용 Row가 없었으므로(Persistence Skeleton 단계) 값 이관 없이
-- 바로 DROP한다.
--
-- status의 PARTIAL/INSUFFICIENT 값도 Phase 1 계약에서 제거됐지만, V2가 이 Column에 CHECK 제약을
-- 두지 않아 별도 제약 변경이 필요 없다(Kotlin Enum만 정리, JobAiAnalysis.kt 참고).

ALTER TABLE job_ai_analyses
    DROP COLUMN eligibility_summary,
    DROP COLUMN extracted_skills;

ALTER TABLE job_ai_analyses
    ADD COLUMN required_skills JSONB,
    ADD COLUMN preferred_skills JSONB,
    ADD COLUMN high_school_graduate_fit VARCHAR(30),
    ADD COLUMN entry_level_fit VARCHAR(30),
    ADD COLUMN difficulty VARCHAR(30),
    ADD COLUMN provider VARCHAR(30),
    ADD COLUMN model VARCHAR(100),
    ADD COLUMN prompt_version VARCHAR(50),
    ADD COLUMN analysis_version INTEGER;

ALTER TABLE job_ai_analyses
    ADD CONSTRAINT ck_job_ai_analyses_high_school_graduate_fit
        CHECK (high_school_graduate_fit IS NULL OR high_school_graduate_fit IN ('SUITABLE', 'CONDITIONAL', 'UNSUITABLE')),
    ADD CONSTRAINT ck_job_ai_analyses_entry_level_fit
        CHECK (entry_level_fit IS NULL OR entry_level_fit IN ('SUITABLE', 'CONDITIONAL', 'UNSUITABLE')),
    ADD CONSTRAINT ck_job_ai_analyses_difficulty
        CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'NORMAL', 'HARD'));
