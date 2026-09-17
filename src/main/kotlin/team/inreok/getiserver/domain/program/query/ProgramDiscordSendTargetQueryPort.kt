package team.inreok.getiserver.domain.program.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/** Notification이 수동 Discord 발송 대상 목록을 구성할 때 사용하는 Program 공개 조회 계약이다. */
@NamedInterface
interface ProgramDiscordSendTargetQueryPort {
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
    ): List<ProgramDiscordSendTargetSnapshot>
}

@NamedInterface
data class ProgramDiscordSendTargetSnapshot(
    val programId: Long,
    val title: String,
    val targetGrades: List<Int>,
    val createdAt: LocalDateTime,
)
