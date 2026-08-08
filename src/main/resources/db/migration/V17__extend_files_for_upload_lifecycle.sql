-- File 도메인(docs/file/file-domain-plan.md, Issue #85)을 구현하기 위해 V2에서 만든 files
-- Table에 업로드 생명주기를 추가한다. 이 Table은 지금까지 어떤 Production 코드도 쓰지 않아
-- 비어 있으므로 NOT NULL Column 추가와 UNIQUE 제약 추가에 데이터 위험이 없다. 따라서 나중까지
-- 남을 'LEGACY' 같은 더미 Enum 값을 만들지 않는다.
--
-- 1) owner_type/owner_id NOT NULL 완화
--    GETI의 파일 업로드는 "먼저 업로드 -> 반환받은 fileId를 리소스에 연결"하는 2단계다. 즉
--    업로드 직후에는 아직 어떤 리소스에도 연결되지 않은 상태가 존재하는데, NOT NULL이면 이
--    상태 자체를 표현할 수 없다. 두 Column은 유지한다 -- 이것이 File과 Target의 연결 관계이며
--    별도 file_links Table을 만들지 않는 이유다(한 File은 한 리소스에만 연결된다).
-- 2) purpose / status / extension / linked_at / updated_at 추가
--    purpose는 업로드 시점에 정해지는 불변 정책 키(FilePurpose)이고, owner_type/owner_id는
--    이후에 바뀌는 연결 상태다. 둘은 역할이 다르므로 함께 존재한다.
--    updated_at은 docs/architecture/erd.md가 "files는 updated_at이 없다"고 기록한 상태를
--    해소한다(나머지 Table과 동일하게 @UpdateTimestamp로 관리).
-- 3) object_key UNIQUE
--    Storage Key는 파일을 식별하는 유일 값이다. 업로드가 PENDING Row를 먼저 커밋해 Key를
--    선점하는 구조라 DB가 중복을 막아준다.
--
-- contains_personal_information과 expires_at은 V2 그대로 두고 이번 범위에서 사용하지 않는다
-- (판단 주체와 보존 정책이 확정되지 않았다, 명세 §17 DECISION_REQUIRED).

ALTER TABLE files ALTER COLUMN owner_type DROP NOT NULL;
ALTER TABLE files ALTER COLUMN owner_id DROP NOT NULL;

ALTER TABLE files
    ADD COLUMN purpose VARCHAR(50) NOT NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL,
    ADD COLUMN extension VARCHAR(20),
    ADD COLUMN linked_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL;

ALTER TABLE files ADD CONSTRAINT uk_files_object_key UNIQUE (object_key);

-- 연결 상태와 owner_* 의 정합성을 DB가 강제한다. PENDING/UPLOADED/FAILED는 아직(또는 영영)
-- 어떤 리소스에도 연결되지 않은 상태이고, LINKED/DELETED는 연결 대상이 반드시 있어야 한다.
--
-- 주의: 미연결 상태에서 정리되는 파일은 DELETED로 남길 수 없다(owner_*가 없으므로). 이런
-- 파일은 감사 가치가 거의 없어 Metadata까지 Hard Delete하는 것을 전제로 한다(지시서 §38).
-- Cleanup Scheduler(Phase 5)를 구현할 때 이 제약을 함께 검토한다.
ALTER TABLE files ADD CONSTRAINT ck_files_link_state CHECK (
    (status IN ('PENDING', 'UPLOADED', 'FAILED') AND owner_type IS NULL AND owner_id IS NULL)
    OR
    (status IN ('LINKED', 'DELETED') AND owner_type IS NOT NULL AND owner_id IS NOT NULL)
);

CREATE INDEX idx_files_purpose ON files (purpose);

-- 미연결 파일 Cleanup(Phase 5)이 "status가 X이고 created_at이 기준 이전"으로 조회한다.
CREATE INDEX idx_files_status_created_at ON files (status, created_at);

-- idx_files_uploader_member_id (uploader_member_id)와 idx_files_owner (owner_type, owner_id),
-- ck_files_size_bytes (size_bytes >= 0)는 V2에 이미 있어 그대로 재사용한다.
