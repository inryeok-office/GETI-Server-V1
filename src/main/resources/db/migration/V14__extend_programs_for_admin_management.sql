-- Program 도메인 Phase 1(등록·수정·상태 관리) 확장. programs/program_applications 원본
-- Table은 V2에서 이미 생성되어 있고(요구사항 5절 최소 Column), 이번 Migration은 등록·수정·
-- 상태 관리 API가 실제로 필요로 하는 담당 교사·조회수·선착순 여부·Form 연결·Discord 연결·
-- 대상 학년 Column을 추가한다.
--
-- first_come_served는 클라이언트가 입력하지 않고 서버가 항상 true로 처리하는 값이라
-- (실행 프롬프트 1절/3.3절) DEFAULT TRUE에 NOT NULL만 걸고 별도 CHECK는 두지 않는다.
ALTER TABLE programs
    ADD COLUMN manager_member_id BIGINT,
    ADD COLUMN first_come_served BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN form_id BIGINT,
    ADD COLUMN discord_channel_id VARCHAR(255),
    ADD COLUMN discord_message_id VARCHAR(255);

ALTER TABLE programs
    ADD CONSTRAINT fk_programs_manager_member FOREIGN KEY (manager_member_id)
        REFERENCES members (id),
    -- forms(V11__create_form_tables.sql)를 참조한다. FormType=PROGRAM, FormStatus=ACTIVE 검증은
    -- Application 도메인이 공개한 Named Interface를 통해 Application 계층에서 수행한다.
    ADD CONSTRAINT fk_programs_form FOREIGN KEY (form_id)
        REFERENCES forms (id);

CREATE INDEX idx_programs_manager_member_id ON programs (manager_member_id);
CREATE INDEX idx_programs_form_id ON programs (form_id);

-- ============================================================
-- program_target_grades (신규)
-- ============================================================
-- 대상 학년은 Program 1건당 여러 값을 가질 수 있어(요구사항 6절 targetGrades 배열) member_majors/
-- member_tech_stacks와 동일하게 관계 Table로 분리한다(단일 Column 배열이 아니라 별도 Table을
-- 쓰는 기존 관례를 그대로 따름).
CREATE TABLE program_target_grades (
    program_id BIGINT NOT NULL,
    grade INTEGER NOT NULL,
    PRIMARY KEY (program_id, grade),
    CONSTRAINT fk_program_target_grades_program FOREIGN KEY (program_id)
        REFERENCES programs (id),
    CONSTRAINT ck_program_target_grades_grade CHECK (grade BETWEEN 1 AND 3)
);

CREATE INDEX idx_program_target_grades_program_id ON program_target_grades (program_id);
