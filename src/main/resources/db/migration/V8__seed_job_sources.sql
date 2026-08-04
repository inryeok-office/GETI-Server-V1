-- 확정된 7개 외부 수집원을 시드한다(Notion API 명세서 순서: MMA -> JOB_ALIO -> CLEAN_EYE ->
-- NARA_ILTEO -> IBK_ONE_JOB -> SARAMIN -> WORK24). 잡코리아는 재배포 조건 미확인으로 제외한다.
-- 모든 Provider가 미승인·미설정 상태이므로 approval_status=PENDING_APPROVAL, enabled=false로
-- 시작한다(V5__seed_tech_stacks.sql과 같은 시드 Migration Convention).
INSERT INTO job_sources (source_code, name, source_type, approval_status, enabled, created_at, updated_at)
VALUES
    ('MMA', '병역일터(산업기능요원·전문연구요원)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW()),
    ('JOB_ALIO', '공공기관 채용정보(ALIO)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW()),
    ('CLEAN_EYE', '지방공공기관 채용정보(클린아이)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW()),
    ('NARA_ILTEO', '공공부문 채용정보(나라일터)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW()),
    ('IBK_ONE_JOB', '중소기업 채용정보(IBK i-ONE Job)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW()),
    ('SARAMIN', '민간기업 채용정보(사람인)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW()),
    ('WORK24', '전국 구인정보(고용24)', 'EXTERNAL_API', 'PENDING_APPROVAL', FALSE, NOW(), NOW());
