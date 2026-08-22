-- majors 메타데이터 초기 Seed 데이터.
-- Seed 없이는 GET /api/v1/metadata/majors가 항상 빈 배열을 반환해 학생이 전공을 선택할 방법이
-- 없고, 학생 검색의 majorId Filter도 실질적으로 쓸모가 없다(Issue #200).
-- 여기 담은 목록은 GETI Notion PRD로 최종 확정된 값이 아니라, Client(GETI-Client-V1)
-- src/views/my-profile/model/mock.ts의 MY_PROFILE_MAJORS를 잠정 후보로 그대로 반영한 것이다.
-- 실제 기관 전공명이 이와 다르게 확정되면 별도 Migration으로 값을 추가/비활성화(active=false)한다.
INSERT INTO majors (name) VALUES
    ('백엔드'),
    ('프론트엔드'),
    ('디자인'),
    ('플러터'),
    ('AI'),
    ('IoT'),
    ('DevOps'),
    ('iOS'),
    ('기능반'),
    ('기타');
