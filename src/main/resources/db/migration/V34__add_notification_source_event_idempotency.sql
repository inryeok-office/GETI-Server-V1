-- ============================================================
-- Notification Idempotency(Issue #193) — source_event_type/source_event_id 추가
-- ============================================================
-- 같은 Domain Event가 두 번 도착해도(동시 처리, Event 재시도 등) 같은 알림이 중복 생성되지
-- 않도록 DB UNIQUE 제약 수준으로 해결한다(원본 요구사항 문서 40절 -- 대규모 Dedup Infra를
-- 새로 두지 않는다). Idempotency Identity는 "누가(recipient_member_id) + 어떤 Domain Event
-- 종류(source_event_type) + 그 Event의 안정적 식별자(source_event_id)"로 확정한다. 별도
-- idempotency_key 문자열은 발급하지 않는다(NotificationCreateCommand.kt의 기존 KDoc이 이
-- 결정을 유보해 두었던 지점).
--
-- 1) source_event_type/source_event_id 추가 (nullable)
--    이 Table에는 이미 실제 사용자 알림 Row가 있어(V16 이후 DELETE API까지 반영된 상태)
--    과거 Row에는 두 값이 없다. 과거 Row에 추측값을 채워 넣지 않고(원본 Event를 다시 알 수
--    없다) NULL로 남긴다. 이후 Notification 생성(NotificationService.create)은 두 값을
--    필수로 요구하도록 Kotlin 계약(NotificationCreateCommand)에서 강제하며, 이 Migration은
--    DB 수준에서는 강제하지 않는다(과거 Row와의 호환성 유지가 우선이다).
--
-- 2) (recipient_member_id, source_event_type, source_event_id) UNIQUE
--    PostgreSQL의 일반 UNIQUE 제약은 NULL을 서로 다른 값으로 취급해 여러 NULL 조합을
--    허용한다 -- 과거 Row(둘 다 NULL)끼리는 절대 충돌하지 않고, 두 값을 모두 채운 새 Row만
--    실제로 중복 판정을 받는다. 그래서 Partial/Filtered Index가 아니라 평범한 UNIQUE
--    제약으로 충분하다.
--
--    이 제약은 Notification을 Soft Delete(deleted_at)해도 그대로 유지된다 -- 사용자가
--    알림을 지웠다는 사실이 "같은 원본 Event를 다시 알려도 된다"는 뜻은 아니기 때문이다
--    (요구사항 확정 계약). deleted_at을 제약 조건에서 제외하지 않는 이유가 바로 이것이다.
ALTER TABLE notifications ADD COLUMN source_event_type VARCHAR(100);
ALTER TABLE notifications ADD COLUMN source_event_id BIGINT;

ALTER TABLE notifications
    ADD CONSTRAINT uk_notifications_recipient_source_event
        UNIQUE (recipient_member_id, source_event_type, source_event_id);
