-- Notification 도메인이 Discord 전달 상태의 Source of Truth가 되도록 두 Table을 추가한다
-- (docs/notification/notification_after_development.md §8·§12·§13,
--  docs/notification/discord-delivery-plan.md §6).
--
-- 기존 Migration은 수정하지 않는다. 두 Table 모두 신규라 데이터 위험이 없다.
--
-- 1) Enum 대신 VARCHAR + Enum mapping
--    저장소 관례이며(§13), 값 추가 시 Migration 없이 Enum만 늘리면 된다.
--
-- 2) Payload Snapshot Column이 없다
--    재시도할 때 target_type/target_id로 원본의 최신 데이터를 다시 조회해 의미 데이터를
--    새로 만든다(§19·§39). 실패 당시 내용을 그대로 보관하면 그 사이 수정된 제목이 반영되지
--    않고, 문의 본문 같은 개인정보를 중복 저장하게 된다.
--
-- 3) idempotency_key UNIQUE
--    같은 Domain Event가 중복 수신돼도 같은 논리적 Delivery가 여러 Row로 생기지 않게 한다
--    (§49). Application Event는 exactly-once가 아니므로 수신 측 멱등성이 DB 제약으로
--    보장되어야 한다. 키 형식은 다음과 같다(discord-delivery-plan.md §6.3).
--      CREATE / CLOSE_NOTICE / DELETE_NOTICE : {targetType}:{targetId}:{action}
--      UPDATE                                : {targetType}:{targetId}:UPDATE:{원본 updatedAt epochMilli}
--    앞의 셋은 대상당 영구히 최대 1건이라, Discord에서 수동 삭제된 메시지를 몰래 다시 만드는
--    일(§23 금지)이 구조적으로 불가능하다.
--
-- 4) processing_started_at
--    서버가 Bot 호출 도중 죽으면 PROCESSING에 영구히 머물러 재시도 대상에서 빠진다(§31).
--    Worker Sweep이 이 시각을 보고 임계값을 넘은 Row만 회수한다. Collector의 재기동 복구
--    Runner 방식(기동 시 PROCESSING 전부 되돌리기)은 단일 인스턴스 전제라 쓰지 않는다 --
--    인스턴스가 둘 이상이면 재기동한 쪽이 다른 인스턴스의 전송 중인 Row를 침범한다.
--
-- 5) Discord Snowflake(channel_id, discord_message_id, role_ids)는 숫자가 아닌 문자열이다(§8).
--    길이 64는 GETI-Bot-V1 idSchema 상한과 같다.
--
-- Index는 §13이 지정한 4종과 대응한다.
--   status + next_retry_at        : Worker가 처리 대상을 고르는 Query
--   target_type + target_id       : 대상별 최신 Delivery 조회
--   idempotency_key UNIQUE        : 중복 생성 방지
--   discord_delivery_id + created_at : 특정 Delivery의 시도 이력 조회

CREATE TABLE discord_deliveries
(
    id                    BIGSERIAL PRIMARY KEY,
    target_type           VARCHAR(20)  NOT NULL,
    target_id             BIGINT       NOT NULL,
    action                VARCHAR(20)  NOT NULL,
    template              VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    channel_id            VARCHAR(64)  NOT NULL,
    role_ids              VARCHAR(700),
    discord_message_id    VARCHAR(64),
    idempotency_key       VARCHAR(200) NOT NULL,
    automatic_retry_count INTEGER      NOT NULL DEFAULT 0,
    manual_retry_count    INTEGER      NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMP,
    processing_started_at TIMESTAMP,
    last_attempt_at       TIMESTAMP,
    delivered_at          TIMESTAMP,
    last_error_code       VARCHAR(50),
    last_error_message    VARCHAR(1000),
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT uk_discord_deliveries_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_discord_deliveries_status_next_retry ON discord_deliveries (status, next_retry_at);
CREATE INDEX idx_discord_deliveries_target ON discord_deliveries (target_type, target_id);

CREATE TABLE discord_delivery_attempts
(
    id                  BIGSERIAL PRIMARY KEY,
    discord_delivery_id BIGINT      NOT NULL,
    attempt_type        VARCHAR(20) NOT NULL,
    attempt_number      INTEGER     NOT NULL,
    request_id          VARCHAR(64) NOT NULL,
    started_at          TIMESTAMP   NOT NULL,
    finished_at         TIMESTAMP   NOT NULL,
    result              VARCHAR(20) NOT NULL,
    error_code          VARCHAR(50),
    retryable           BOOLEAN,
    created_at          TIMESTAMP   NOT NULL,
    CONSTRAINT fk_discord_delivery_attempts_delivery
        FOREIGN KEY (discord_delivery_id) REFERENCES discord_deliveries (id)
);

CREATE INDEX idx_discord_delivery_attempts_delivery_created
    ON discord_delivery_attempts (discord_delivery_id, created_at);
