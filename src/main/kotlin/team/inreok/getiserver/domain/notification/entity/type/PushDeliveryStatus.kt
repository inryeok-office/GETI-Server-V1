package team.inreok.getiserver.domain.notification.entity.type

/**
 * Push 전달(기기 1대 기준)의 상태다(Issue #190). `DiscordDeliveryStatus`(PENDING/PROCESSING/
 * DELIVERED/FAILED)와 같은 개념 집합이지만, DELIVERED 대신 SENT를 쓴다 -- Provider(FCM)가 실제
 * 기기 수신을 확인해 주지 않고 "전송 요청을 접수했다"까지만 보장하기 때문이다.
 */
enum class PushDeliveryStatus {
    /** 생성됐고 아직 Worker가 집어가지 않았다. 자동 재시도 대기 상태도 여기에 포함된다. */
    PENDING,

    /** Worker가 선점해 현재 Provider로 전송 처리 중이다. */
    PROCESSING,

    /** Provider가 전송 요청을 성공적으로 접수했다. */
    SENT,

    /** 허용된 재시도를 모두 소진했거나, 재시도해도 달라지지 않는 오류(예: 무효 Token)를 만났다. */
    FAILED,
}
