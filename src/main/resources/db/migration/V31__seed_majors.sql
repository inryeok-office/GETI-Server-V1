-- Issue #200에서 확정된 Major 메타데이터 초기 Seed.
-- Seed 없이는 GET /api/v1/metadata/majors가 항상 빈 배열을 반환해 학생이 전공(관심 개발 분야)을
-- 선택할 방법이 없고, 학생 검색의 majorId Filter도 실질적으로 쓸모가 없다.
-- 여기 담은 목록은 학교 공식 학과명이 아니라, 서비스 내 학생 전공/관심 개발 분야를 나타내는
-- Major 메타데이터이며 GET /api/v1/metadata/majors 및 학생 Profile/Search의 선택값으로 사용한다.
INSERT INTO majors (name) VALUES
    ('백엔드'),
    ('프론트엔드'),
    ('디자인'),
    ('AI'),
    ('IoT'),
    ('DevOps'),
    ('클라우드 컴퓨팅'),
    ('모바일 앱 개발 (Flutter)'),
    ('네이티브 앱 개발 (Android)'),
    ('네이티브 앱 개발 (iOS)'),
    ('사이버 보안'),
    ('모바일 로보틱스'),
    ('IT 네트워크'),
    ('크로스 플랫폼 앱 개발 (React Native)'),
    ('기타');
