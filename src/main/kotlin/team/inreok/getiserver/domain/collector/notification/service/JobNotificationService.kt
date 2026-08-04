package team.inreok.getiserver.domain.collector.notification.service

import team.inreok.getiserver.domain.collector.eligibility.JobRelevanceCategory
import java.time.LocalDateTime

/**
 * Collector가 실제 Provider 수집으로 신규(CREATED) 등록한 공고에 대한 Discord 알림을 담당한다.
 * Job/Company 저장 Transaction과 분리해 호출한다 — Discord 발송 실패가 이미 Commit된 공고 등록을
 * 되돌리거나 Provider 재수집을 유발하지 않는다(Issue #62 확장 범위, "발송 시점과 Transaction
 * 분리" 참고).
 */
interface JobNotificationService {
    /**
     * 알림 조건(설정 활성화·초기 수집 억제 정책·중복 방지)을 확인하고, 조건을 만족하면 Delivery를
     * 만들어 즉시 발송을 시도한다. 조건을 만족하지 않으면 아무 것도 하지 않는다(예외를 던지지
     * 않는다 — 호출 측 공고 처리 결과 집계에 영향을 주지 않기 위함).
     */
    fun enqueueIfEligible(
        trigger: JobNotificationTrigger,
        isInitialImport: Boolean,
    )

    /** 재시도 대기 중이거나 아직 한 번도 시도하지 않은 Delivery를 찾아 재발송을 시도한다. */
    fun processDueRetries()
}

/** [JobNotificationService.enqueueIfEligible] 호출에 필요한 최소 표시 정보. */
data class JobNotificationTrigger(
    val jobId: Long,
    val sourceCode: String,
    val sourceDisplayName: String,
    val title: String,
    val companyName: String,
    val externalUrl: String?,
    val recruitmentEndedAt: LocalDateTime?,
    val employmentType: String? = null,
    val educationCondition: String? = null,
    val careerCondition: String? = null,
    val relevanceCategory: JobRelevanceCategory? = null,
)
