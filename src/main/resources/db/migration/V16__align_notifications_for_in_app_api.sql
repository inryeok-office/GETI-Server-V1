-- 인앱 알림 API(Notification Core, docs/Notification/notification-core-plan.md)를 구현하기 위해
-- V2에서 만든 notifications Table을 실제 API 계약에 맞춘다. 이 Table은 지금까지 어떤 Production
-- 코드도 쓰지 않아 비어 있으므로 Column RENAME에 데이터 위험이 없다.
--
-- 1) resource_type/resource_id -> target_type/target_id
--    API 응답 필드명(targetType/targetId)과 DB 어휘를 일치시킨다. 같은 다형적 참조인
--    async_operations.target_type/target_id, audit_logs.target_type/target_id와도 같아진다
--    (files.owner_type/owner_id만 소유 관계라 계속 owner_*를 쓴다).
-- 2) updated_at 추가
--    읽음 처리(is_read/read_at)로 Row가 변경되는데 갱신 시각을 남길 Column이 없었다. files를
--    제외한 나머지 Table과 동일하게 @UpdateTimestamp로 관리한다.
--
-- type Column은 VARCHAR(100) 그대로 둔다 — NotificationType의 최장 값이 30자
-- (JOB_APPLICATION_STATUS_CHANGED)라 길이를 줄이지 않아도 Enum Mapping에 문제가 없다.
-- deleted_at은 유지하되 이번 범위에서는 사용하지 않는다(알림 삭제 API는 요구사항에 없다).
ALTER TABLE notifications RENAME COLUMN resource_type TO target_type;
ALTER TABLE notifications RENAME COLUMN resource_id TO target_id;

-- 기존 Row가 있어도 안전하도록 nullable로 추가 -> 채움 -> NOT NULL 순서로 나눈다.
ALTER TABLE notifications ADD COLUMN updated_at TIMESTAMP;
UPDATE notifications SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE notifications ALTER COLUMN updated_at SET NOT NULL;

-- idx_notifications_recipient_read_created (recipient_member_id, is_read, created_at)가 V2에
-- 이미 있어 목록 조회와 읽지 않은 개수 조회를 모두 커버한다. 새 Index는 추가하지 않는다.
