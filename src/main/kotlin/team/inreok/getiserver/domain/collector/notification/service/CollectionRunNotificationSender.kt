package team.inreok.getiserver.domain.collector.notification.service

import team.inreok.getiserver.domain.collector.notification.discord.CollectionRunSummaryEmbedInput

/**
 * Collector 실행(CollectionRun) 하나가 끝날 때마다 결과 요약을 Discord로 알린다. 개별 신규 공고
 * 알림([JobNotificationService])과 달리 DB 기반 재시도 대상이 아니다 — 기존 CD 배포 알림과 같은
 * "설정 없으면 Skip, 전송 실패해도 Run 결과에 영향 없음" 원칙의 Best Effort 알림이다.
 */
interface CollectionRunNotificationSender {
    fun notify(input: CollectionRunSummaryEmbedInput)
}
