-- Push Provider 연동(Issue #190)을 위한 push_deliveries Table을 추가한다.
--
-- Discord Delivery(V19)와 같은 문제(외부 전달 + 상태·재시도 관리)를 풀지만, 이번 Issue 범위에는
-- 관리자 조회·수동 재시도 API가 없어(Issue #190에 명시된 작업 범위 참고) discord_delivery_attempts
-- 같은 별도 이력 Table과 admin_retry_count류 Column을 두지 않는다(과설계 금지, 최소 구조).
--
-- notification_id + device_id 한 쌍마다 Row 하나다 -- 회원 1명이 기기를 여러 대 등록하면
-- 알림 1건이 기기 수만큼의 Row로 나뉘어, 기기 하나의 실패가 다른 기기의 발송에 영향을 주지
-- 않는다(Issue #190 확정 계약: 한 기기 실패가 다른 기기 전송을 막지 않는다).
--
-- device_id에는 물리 FK를 걸지 않는다 -- 무효 Token(만료·삭제된 기기) 정리가 이 Table의 Row가
-- 아니라 notification_devices의 Row를 지우는 방식이라(Issue #190 작업 범위), FK가 있으면 이미
-- FAILED로 끝난 과거 Row가 그 삭제를 막는다(discord_deliveries의 target_type/target_id와 같은
-- 다형적 참조 관례를 그대로 따른 것은 아니지만 같은 이유 -- 참조 대상의 생애주기가 이 Table의
-- Row 생애주기와 다르다).
--
-- notification_id는 notifications를 그대로 참조한다(같은 Table 내 참조라 다형적이지 않다).
-- notifications가 Soft Delete만 하고 물리 삭제하지 않으므로 CASCADE 삭제가 실제로 발생할 일은
-- 없지만, 다른 Table의 관례(FK + CASCADE)를 그대로 따른다.
--
-- retry_count 상한(기본 3)과 재시도 백오프는 Application 계층(PushDeliveryRetryPolicy)이
-- 계산한다 -- discord_deliveries의 automatic_retry_count/next_retry_at과 같은 방식이다.
--
-- processing_started_at은 Worker가 이 Row를 선점한 시각이다. 서버가 Provider 호출 도중 죽으면
-- PROCESSING에 영구히 머무를 수 있어, 이 시각이 임계값보다 오래되면 회수한다
-- (discord_deliveries.processing_started_at과 같은 이유).

CREATE TABLE push_deliveries
(
    id                     BIGSERIAL PRIMARY KEY,
    notification_id        BIGINT       NOT NULL,
    member_id              BIGINT       NOT NULL,
    device_id              BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    retry_count            INTEGER      NOT NULL DEFAULT 0,
    next_retry_at          TIMESTAMP,
    processing_started_at  TIMESTAMP,
    last_attempt_at        TIMESTAMP,
    sent_at                TIMESTAMP,
    last_error_code        VARCHAR(50),
    last_error_message     VARCHAR(1000),
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    CONSTRAINT fk_push_deliveries_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_push_deliveries_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

-- Worker가 처리 대상을 고르는 Query(discord_deliveries와 같은 목적의 Index).
CREATE INDEX idx_push_deliveries_status_next_retry ON push_deliveries (status, next_retry_at);

-- 특정 알림의 Push 전달 현황을 살펴볼 때 쓴다(이번 범위에 조회 API는 없지만, 운영 중 DB로 직접
-- 확인할 상황에 대비한다).
CREATE INDEX idx_push_deliveries_notification ON push_deliveries (notification_id);
