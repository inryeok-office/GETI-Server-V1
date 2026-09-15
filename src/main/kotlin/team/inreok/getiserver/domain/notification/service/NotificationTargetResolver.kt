package team.inreok.getiserver.domain.notification.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.application.query.JobApplicationNotificationTargetQueryPort
import team.inreok.getiserver.domain.application.query.JobApplicationNotificationTargetSnapshot
import team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetQueryPort
import team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetSnapshot
import team.inreok.getiserver.domain.job.query.JobNotificationTargetQueryPort
import team.inreok.getiserver.domain.job.query.JobNotificationTargetSnapshot
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.portfolio.query.PortfolioRequestNotificationTargetQueryPort
import team.inreok.getiserver.domain.portfolio.query.PortfolioRequestNotificationTargetSnapshot
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetQueryPort
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetSnapshot

/**
 * 알림이 가리키는 원본 리소스로 지금 이동할 수 있는지 서버에서 계산한다(원본 요구사항 문서
 * 17절). 클라이언트가 원본의 공개·삭제·권한 상태를 직접 조합해 판단하지 않게 하는 것이 목적이다.
 *
 * 다른 Domain의 Repository를 직접 쓰지 않고 각 Domain이 공개한 Query Port만 사용한다. 목록
 * 응답 한 번에 최대 100건이 실릴 수 있으므로 대상을 [NotificationTargetType]별로 모아 Domain당
 * 최대 한 번만 조회한다(N+1 방지).
 *
 * 판정 기준은 대상에 따라 세 갈래다.
 * - 공개 리소스([NotificationTargetType.JOB], [NotificationTargetType.PROGRAM]): 상태가 공개
 *   대상인지로 판정한다. 인증된 사용자면 누구나 볼 수 있어 요청자가 누구인지는 상관없다.
 * - 소유 리소스([NotificationTargetType.INQUIRY], [NotificationTargetType.JOB_APPLICATION]):
 *   요청자가 소유자인지로 판정한다(Issue #187). 둘 다 Soft Delete Column이 없어 Row가 없으면
 *   [NotificationTargetUnavailableReason.DELETED]다.
 * - 대상 지정 리소스([NotificationTargetType.PORTFOLIO_REQUEST]): 공개 리소스와 같은 상태 판정을
 *   먼저 거친 뒤, 요청자가 대상 학생인지로 판정한다(Issue #332). `PortfolioRequestService.getDetail`
 *   의 학생 판정 순서(DRAFT면 404, 대상이 아니면 403)를 그대로 따른다. 이 알림은 대상 학생에게만
 *   생성되므로(`PortfolioRequestPublishedNotificationListener`) 교사·개발자 역할 예외는 두지 않는다.
 *
 * [NotificationTargetType.MEMBER_APPROVAL]은 아직 해석하지 않는다 -- 승인 결과는 이동해서 볼 상세
 * 화면 자체가 없다. 그때까지 이 대상은 "이동 불가, 이유 없음"으로 내려간다(없는 판정을 지어내지
 * 않기 위해서다).
 */
@Component
class NotificationTargetResolver(
    private val jobNotificationTargetQueryPort: JobNotificationTargetQueryPort,
    private val programNotificationTargetQueryPort: ProgramNotificationTargetQueryPort,
    private val inquiryNotificationTargetQueryPort: InquiryNotificationTargetQueryPort,
    private val jobApplicationNotificationTargetQueryPort: JobApplicationNotificationTargetQueryPort,
    private val portfolioRequestNotificationTargetQueryPort: PortfolioRequestNotificationTargetQueryPort,
) {
    /**
     * [targets]에 담긴 (대상 유형, 대상 id) 쌍을 한 번에 해석한다. 결과 Map에는 [targets]의 모든
     * 항목이 그대로 담긴다.
     *
     * [viewerMemberId]는 소유 리소스의 소유자 판정에 쓴다. 알림 수신자 본인의 권한으로만
     * 계산해야 하며, 다른 사용자의 권한으로 판정하지 않는다.
     */
    fun resolveAll(
        targets: Set<NotificationTargetRef>,
        viewerMemberId: Long,
    ): Map<NotificationTargetRef, NotificationTargetAvailability> {
        if (targets.isEmpty()) return emptyMap()

        val snapshots = loadSnapshots(targets, viewerMemberId)
        return targets.associateWith { target -> resolveOne(target, snapshots, viewerMemberId) }
    }

    /**
     * 대상 Domain별로 한 번씩만 조회한다. 해당 유형의 대상이 하나도 없으면 Port를 아예 호출하지
     * 않는다 — 다른 Module에 빈 질의를 보내지 않기 위해서다("Domain당 최대 1회, 필요할 때만").
     */
    private fun loadSnapshots(
        targets: Set<NotificationTargetRef>,
        viewerMemberId: Long,
    ): TargetSnapshots {
        val jobIds = targets.idsOf(NotificationTargetType.JOB)
        val programIds = targets.idsOf(NotificationTargetType.PROGRAM)
        val inquiryIds = targets.idsOf(NotificationTargetType.INQUIRY)
        val applicationIds = targets.idsOf(NotificationTargetType.JOB_APPLICATION)
        val portfolioRequestIds = targets.idsOf(NotificationTargetType.PORTFOLIO_REQUEST)
        return TargetSnapshots(
            jobs = if (jobIds.isEmpty()) emptyMap() else jobNotificationTargetQueryPort.findAllByIds(jobIds),
            programs =
                if (programIds.isEmpty()) emptyMap() else programNotificationTargetQueryPort.findAllByIds(programIds),
            inquiries =
                if (inquiryIds.isEmpty()) emptyMap() else inquiryNotificationTargetQueryPort.findAllByIds(inquiryIds),
            applications =
                if (applicationIds.isEmpty()) {
                    emptyMap()
                } else {
                    jobApplicationNotificationTargetQueryPort.findAllByIds(applicationIds)
                },
            portfolioRequests =
                if (portfolioRequestIds.isEmpty()) {
                    emptyMap()
                } else {
                    portfolioRequestNotificationTargetQueryPort.findAllByIds(portfolioRequestIds, viewerMemberId)
                },
        )
    }

    private fun resolveOne(
        target: NotificationTargetRef,
        snapshots: TargetSnapshots,
        viewerMemberId: Long,
    ): NotificationTargetAvailability =
        when (target.targetType) {
            NotificationTargetType.JOB -> {
                snapshots.jobs.availabilityFor(target) { availabilityOf(target, it.status, it.deleted) }
            }

            NotificationTargetType.PROGRAM -> {
                snapshots.programs.availabilityFor(target) { availabilityOf(target, it.status, it.deleted) }
            }

            NotificationTargetType.INQUIRY -> {
                snapshots.inquiries.availabilityFor(target) {
                    ownedAvailabilityOf(target, it.authorMemberId, viewerMemberId)
                }
            }

            NotificationTargetType.JOB_APPLICATION -> {
                snapshots.applications.availabilityFor(target) {
                    ownedAvailabilityOf(target, it.applicantMemberId, viewerMemberId)
                }
            }

            NotificationTargetType.PORTFOLIO_REQUEST -> {
                snapshots.portfolioRequests.availabilityFor(target) {
                    availabilityOf(target, it.status, it.deleted, it.targetedToViewer)
                }
            }

            else -> {
                UNSUPPORTED_AVAILABILITY
            }
        }

    /** 조회 결과에 Row가 없으면 원본이 사라진 것이므로 [DELETED_AVAILABILITY]로 판정한다. */
    private inline fun <T> Map<Long, T>.availabilityFor(
        target: NotificationTargetRef,
        judge: (T) -> NotificationTargetAvailability,
    ): NotificationTargetAvailability = this[target.targetId]?.let(judge) ?: DELETED_AVAILABILITY

    /**
     * 상태·삭제 여부로 먼저 판정하고, 볼 수 있는 상태일 때만 [accessibleToViewer]를 본다. 공개
     * 리소스는 요청자와 무관하므로 기본값 true로 호출한다.
     */
    private fun availabilityOf(
        target: NotificationTargetRef,
        status: String,
        deleted: Boolean,
        accessibleToViewer: Boolean = true,
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

            !accessibleToViewer -> {
                FORBIDDEN_AVAILABILITY
            }

            else -> {
                availableAt(target)
            }
        }

    /**
     * 소유자만 열람할 수 있는 대상을 판정한다. 상태는 보지 않는다 -- 문의는 상태와 무관하게
     * 작성자가 볼 수 있고, 지원서도 지원자 본인은 DRAFT를 포함해 볼 수 있기 때문이다.
     */
    private fun ownedAvailabilityOf(
        target: NotificationTargetRef,
        ownerMemberId: Long,
        viewerMemberId: Long,
    ): NotificationTargetAvailability =
        if (ownerMemberId == viewerMemberId) {
            availableAt(target)
        } else {
            FORBIDDEN_AVAILABILITY
        }

    private fun availableAt(target: NotificationTargetRef): NotificationTargetAvailability =
        NotificationTargetAvailability(
            available = true,
            reason = null,
            deepLink = NotificationDeepLink.of(target.targetType, target.targetId),
        )

    private fun Set<NotificationTargetRef>.idsOf(targetType: NotificationTargetType): Set<Long> =
        asSequence().filter { it.targetType == targetType }.map { it.targetId }.toSet()

    /** 한 번의 해석에 필요한 대상 Domain 조회 결과를 함께 들고 다니기 위한 값이다. */
    private data class TargetSnapshots(
        val jobs: Map<Long, JobNotificationTargetSnapshot>,
        val programs: Map<Long, ProgramNotificationTargetSnapshot>,
        val inquiries: Map<Long, InquiryNotificationTargetSnapshot>,
        val applications: Map<Long, JobApplicationNotificationTargetSnapshot>,
        val portfolioRequests: Map<Long, PortfolioRequestNotificationTargetSnapshot>,
    )

    companion object {
        /**
         * `JobStatus`/`ProgramStatus`/`PortfolioRequestStatus`가 값 집합이 같아
         * (DRAFT/PUBLISHED/CLOSED/DELETED) 이름으로 함께 판정한다. 공개 대상 기준은 `job`의
         * `PUBLIC_VISIBLE_STATUSES`(PUBLISHED, CLOSED)와 같다 — 마감된 공고·프로그램·수합 요청은
         * 여전히 열어볼 수 있어야 한다. Port가 Enum이 아닌 이름
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

        /**
         * 대상은 남아 있지만 요청자가 소유자(또는 수합 요청의 대상 학생)가 아닌 경우. 알림 수신자와
         * 열람 권한자가 다를 때만 생긴다.
         */
        private val FORBIDDEN_AVAILABILITY =
            NotificationTargetAvailability(
                available = false,
                reason = NotificationTargetUnavailableReason.FORBIDDEN,
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
