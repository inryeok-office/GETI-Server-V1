package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/** Notification이 수동 Discord 발송 대상 목록을 구성할 때 사용하는 Job 공개 조회 계약이다. */
@NamedInterface
interface JobDiscordSendTargetQueryPort {
    fun countPublished(
        targetName: String?,
        targetGrade: Int?,
    ): Long

    /** `afterCreatedAt`/`afterId` 이후의 다음 정렬 batch를 반환한다. */
    fun findPublished(
        targetName: String?,
        targetGrade: Int?,
        afterCreatedAt: LocalDateTime?,
        afterId: Long?,
        limit: Int,
    ): List<JobDiscordSendTargetSnapshot>
}

@NamedInterface
data class JobDiscordSendTargetSnapshot(
    val jobId: Long,
    val title: String,
    /** null은 기존 Job 계약의 "전체 학년" 의미를 유지한다. */
    val targetGrades: List<Int>?,
    val createdAt: LocalDateTime,
)
