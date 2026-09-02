package team.inreok.getiserver.domain.notification.entity.type

/**
 * 푸시 기기의 OS 플랫폼이다(Issue #189 확정 계약, Notion API 명세서의 PUSH_PLATFORM과 동일한 값
 * 집합). 이 두 값 외에 추가 플랫폼(Web Push 등)은 이번 범위에 없다.
 */
enum class PushPlatform {
    IOS,
    ANDROID,
}
