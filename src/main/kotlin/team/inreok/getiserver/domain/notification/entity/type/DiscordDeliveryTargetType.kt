package team.inreok.getiserver.domain.notification.entity.type

/**
 * Discord로 알릴 원본 리소스의 종류다(후속 요구사항 문서 §9).
 *
 * 값과 직렬화 문자열이 GETI-Bot-V1의 `TARGET_TYPES`(`src/internal-api/types.ts`)와 정확히
 * 일치해야 한다 — Bot이 `z.enum(TARGET_TYPES)`로 검증하므로 어긋나면 `400 INVALID_REQUEST`다.
 *
 * `APPLICATION` 등은 Discord Bot 초기 연동 범위에 넣지 않는다.
 */
enum class DiscordDeliveryTargetType {
    JOB,
    PROGRAM,
    INQUIRY,
}
