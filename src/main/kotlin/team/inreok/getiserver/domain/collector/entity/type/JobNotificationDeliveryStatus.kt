package team.inreok.getiserver.domain.collector.entity.type

// Notion 최신 Enum 목록에 이미 동일한 이름의 Enum(DISCORD_DELIVERY_STATUS)이 있는지 이번
// 세션에서는 확인하지 못했다(Notion 접근 불가, 최종 보고 참고). 요구된 4개 값(PENDING/
// SENDING/SENT/FAILED)만 정의했고, 이후 Notion과 다르면 별도로 맞춘다.
enum class JobNotificationDeliveryStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
}
