-- ============================================================
-- 알림 보관·정리(Retention) Batch 정리 Query 전용 Index (Issue #194)
-- ============================================================
-- 기존 idx_notifications_recipient_read_created(recipient_member_id, is_read, created_at)는
-- 회원별 조회를 위한 Index라 recipient_member_id 없이 전역으로 도는 정리 대상 조회(soft delete /
-- read / unread)에는 맞지 않는다. 아래 세 Partial Index는 각 정리 Query(NotificationRepository의
-- findExpiredSoftDeletedIds/findExpiredReadIds/findExpiredUnreadIds)의 WHERE 조건과 정확히
-- 일치해 Index Only로 대상 id를 뽑을 수 있게 한다. 세 조건은 서로 배타적이라(deleted_at이 있으면
-- read/unread Index 대상에서 빠짐) Index 세 개가 겹치지 않는다.

CREATE INDEX idx_notifications_deleted_at_retention
    ON notifications (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_notifications_read_retention
    ON notifications (read_at)
    WHERE deleted_at IS NULL AND is_read = true;

CREATE INDEX idx_notifications_unread_retention
    ON notifications (created_at)
    WHERE deleted_at IS NULL AND is_read = false;
