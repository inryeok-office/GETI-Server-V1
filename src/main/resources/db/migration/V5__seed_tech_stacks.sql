-- tech_stacks 메타데이터 초기 Seed 데이터.
-- Seed 없이는 GET /metadata/tech-stacks가 항상 빈 배열이고, PATCH /me/tech-stacks는 어떤 ID를
-- 보내도 TECH_STACK_NOT_FOUND가 되어 기술 스택 선택 기능 자체가 동작할 수 없다(코드 리뷰 Major
-- 반영). 여기 담은 목록은 GETI 특정 커리큘럼을 반영한 것이 아니라 업계에 널리 알려진 대표 기술
-- 이름이다 — 실제 GETI 커리큘럼에 맞는 목록으로 조정이 필요하면 별도 Migration으로 추가/수정한다.
-- majors(전공)는 기관별 실제 전공명을 알 수 없어 이 PR에서 임의로 채우지 않았다(Gap으로 남김).
INSERT INTO tech_stacks (name, category) VALUES
    ('Java', 'BACKEND'),
    ('Kotlin', 'BACKEND'),
    ('Spring Boot', 'BACKEND'),
    ('Node.js', 'BACKEND'),
    ('Python', 'BACKEND'),
    ('Django', 'BACKEND'),
    ('JavaScript', 'FRONTEND'),
    ('TypeScript', 'FRONTEND'),
    ('React', 'FRONTEND'),
    ('Vue.js', 'FRONTEND'),
    ('Next.js', 'FRONTEND'),
    ('Swift', 'MOBILE'),
    ('Flutter', 'MOBILE'),
    ('React Native', 'MOBILE'),
    ('TensorFlow', 'AI'),
    ('PyTorch', 'AI'),
    ('scikit-learn', 'AI'),
    ('Docker', 'DEVOPS'),
    ('Kubernetes', 'DEVOPS'),
    ('GitHub Actions', 'DEVOPS'),
    ('Jenkins', 'DEVOPS'),
    ('Terraform', 'DEVOPS'),
    ('PostgreSQL', 'DATABASE'),
    ('MySQL', 'DATABASE'),
    ('MongoDB', 'DATABASE'),
    ('Redis', 'DATABASE'),
    ('Git', 'ETC'),
    ('Figma', 'ETC');
