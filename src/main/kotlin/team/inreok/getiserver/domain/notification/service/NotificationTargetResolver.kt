package team.inreok.getiserver.domain.notification.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.job.query.JobNotificationTargetQueryPort
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetQueryPort

/**
 * 알림이 가리키는 원본 리소스로 지금 이동할 수 있는지 서버에서 계산한다(원본 요구사항 문서
 * 17절). 클라이언트가 원본의 공개·삭제·권한 상태를 직접 조합해 판단하지 않게 하는 것이 목적이다.
 *
 * 다른 Domain의 Repository를 직접 쓰지 않고 각 Domain이 공개한 Query Port만 사용한다. 목록
 * 응답 한 번에 최대 100건이 실릴 수 있으므로 대상을 [NotificationTargetType]별로 모아 Domain당
 * 최대 한 번만 조회한다(N+1 방지).
 *
 * 현재 해석할 수 있는 대상은 [NotificationTargetType.JOB]과 [NotificationTargetType.PROGRAM]
 * 뿐이다. `inquiry`/`portfolio`는 Domain에 Service 계층 자체가 없고, `JOB_APPLICATION`과
 * `MEMBER_APPROVAL`은 Domain Event를 연결하는 시점에 함께 붙인다. 그때까지 이 대상들은
 * "이동 불가, 이유 없음"으로 내려간다 — 없는 권한 판정을 지어내지 않기 위해서다.
 */
@Component
class NotificationTargetResolver(
    private val jobNotificationTargetQueryPort: JobNotificationTargetQueryPort,
    private val programNotificationTargetQueryPort: ProgramNotificationTargetQueryPort,
) {
    /**
     * [targets]에 담긴 (대상 유형, 대상 id) 쌍을 한 번에 해석한다. 결과 Map에는 [targets]의 모든
     * 항목이 그대로 담긴다.
     *
     * `viewerMemberId`는 계약에만 남기고 아직 판정에 쓰지 않는다 — 소유자·역할 기반으로
     * [NotificationTargetUnavailableReason.FORBIDDEN]을 판정해야 하는 대상(JOB_APPLICATION 등)이
     * 아직 없기 때문이다. 공고·프로그램은 인증된 사용자면 누구나 볼 수 있다(SecurityConfig).
     */
    fun resolveAll(
        targets: Set<NotificationTargetRef>,
        @Suppress("UNUSED_PARAMETER") viewerMemberId: Long,
    ): Map<NotificationTargetRef, NotificationTargetAvailability> {
        if (targets.isEmpty()) return emptyMap()

        // 해당 유형의 대상이 하나도 없으면 Port를 아예 호출하지 않는다 — 다른 Module에 빈 질의를
        // 보내지 않기 위해서다("Domain당 최대 1회, 필요할 때만").
        val jobIds = targets.idsOf(NotificationTargetType.JOB)
        val programIds = targets.idsOf(NotificationTargetType.PROGRAM)
        val jobs = if (jobIds.isEmpty()) emptyMap() else jobNotificationTargetQueryPort.findAllByIds(jobIds)
        val programs =
            if (programIds.isEmpty()) emptyMap() else programNotificationTargetQueryPort.findAllByIds(programIds)

        return targets.associateWith { target ->
            when (target.targetType) {
                NotificationTargetType.JOB -> {
                    jobs[target.targetId]?.let { availabilityOf(target, it.status, it.deleted) } ?: DELETED_AVAILABILITY
                }

                NotificationTargetType.PROGRAM -> {
                    programs[target.targetId]?.let { availabilityOf(target, it.status, it.deleted) }
                        ?: DELETED_AVAILABILITY
                }

                else -> {
                    UNSUPPORTED_AVAILABILITY
                }
            }
        }
    }

    private fun availabilityOf(
        target: NotificationTargetRef,
        status: String,
        deleted: Boolean,
    ): NotificationTargetAvailability =
        when {
            deleted || status == STATUS_DELETED -> {
                DELETED_AVAILABILITY
            }

            status !in PUBLICLY_VISIBLE_STATUS_NAMES -> {
                NotificationTargetAvailability(
                    available = false,
                    reason = NotificationTargetUnavailableReason.NOT_VISIBLE,
                    deepLink = null,
                )
            }

            else -> {
                NotificationTargetAvailability(
                    available = true,
                    reason = null,
                    deepLink = NotificationDeepLink.of(target.targetType, target.targetId),
                )
            }
        }

    private fun Set<NotificationTargetRef>.idsOf(targetType: NotificationTargetType): Set<Long> =
        asSequence().filter { it.targetType == targetType }.map { it.targetId }.toSet()

    companion object {
        /**
         * `JobStatus`/`ProgramStatus`가 값 집합이 같아(DRAFT/PUBLISHED/CLOSED/DELETED) 이름으로
         * 함께 판정한다. 공개 대상 기준은 `job`의 `PUBLIC_VISIBLE_STATUSES`(PUBLISHED, CLOSED)와
         * 같다 — 마감된 공고·프로그램은 여전히 열어볼 수 있어야 한다. Port가 Enum이 아닌 이름
         * 문자열을 돌려주는 이유는 각 Port의 KDoc(순환 의존 회피) 참고.
         */
        private val PUBLICLY_VISIBLE_STATUS_NAMES = setOf("PUBLISHED", "CLOSED")
        private const val STATUS_DELETED = "DELETED"

        private val DELETED_AVAILABILITY =
            NotificationTargetAvailability(
                available = false,
                reason = NotificationTargetUnavailableReason.DELETED,
                deepLink = null,
            )

        /** 아직 해석을 지원하지 않는 대상. 이유를 지어내지 않고 null로 둔다. */
        private val UNSUPPORTED_AVAILABILITY =
            NotificationTargetAvailability(
                available = false,
                reason = null,
                deepLink = null,
            )
    }
}

data class NotificationTargetRef(
    val targetType: NotificationTargetType,
    val targetId: Long,
)

data class NotificationTargetAvailability(
    val available: Boolean,
    val reason: NotificationTargetUnavailableReason?,
    val deepLink: String?,
)
