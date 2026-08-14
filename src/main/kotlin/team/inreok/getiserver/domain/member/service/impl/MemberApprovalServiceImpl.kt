package team.inreok.getiserver.domain.member.service.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.ApprovalAction
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.event.MemberApprovalProcessedEvent
import team.inreok.getiserver.domain.member.exception.MemberNotApprovalTargetException
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberNotPendingException
import team.inreok.getiserver.domain.member.exception.RejectionReasonRequiredException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import java.time.LocalDateTime

/**
 * [MemberApprovalService] 구현이다(Issue #99).
 *
 * 동시성: [MemberRepository.findByIdForUpdate]로 회원 Row에 Pessimistic Write Lock을 건 뒤 상태를
 * 확인·전이한다. 두 개발자가 같은 PENDING 회원을 동시에 승인/거절하면 한 Transaction이 먼저 Lock을
 * 잡아 PENDING -> ACTIVE/REJECTED로 바꾸고 Commit하며, 나중 Transaction은 Lock을 얻은 뒤 이미
 * PENDING이 아닌 상태를 보고 [MemberNotPendingException](409)으로 거부된다. 따라서 최종적으로 하나의
 * 상태 전이만 성공한다.
 *
 * 무결성: 상태 변경(Dirty Checking)과 TEACHER Role 저장이 같은 `@Transactional` 안에서 이뤄져,
 * "status는 ACTIVE인데 Role 저장 실패" 같은 부분 성공 상태가 남지 않는다.
 *
 * 알림: 처리 결과를 [MemberApprovalProcessedEvent]로 발행해 `domain.notification`이 대상 회원에게
 * 인앱 알림을 만들게 한다(Issue #118). 이 Transaction 안에서 발행하지만 구독 측이 `AFTER_COMMIT`
 * 이므로, 아래 상태 전이가 Rollback되면 알림도 만들어지지 않는다.
 */
@Service
class MemberApprovalServiceImpl(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : MemberApprovalService {
    @Transactional
    override fun process(
        memberId: Long,
        request: MemberApprovalRequest,
    ): MemberApprovalResponse {
        val member = memberRepository.findByIdForUpdate(memberId) ?: throw MemberNotFoundException(memberId)
        validateApprovable(member)

        return when (request.action) {
            ApprovalAction.APPROVE -> approve(member)
            ApprovalAction.REJECT -> reject(member, request.reason)
        }
    }

    private fun validateApprovable(member: Member) {
        // 승인/거절은 PENDING 회원에 대해서만 허용한다. 이미 승인/거절됐거나 다른 상태이면 409로 거부한다.
        if (member.status != MemberStatus.PENDING) {
            throw MemberNotPendingException(member.status)
        }
        // 교직원은 Google OAuth로 가입하므로 GOOGLE 회원만 승인 대상이다. DG 학생이 비정상적으로
        // PENDING으로 존재하더라도 이 API로 TEACHER가 되어서는 안 되므로 Provider를 함께 검증한다.
        if (member.oauthProvider != OAuthProvider.GOOGLE) {
            throw MemberNotApprovalTargetException(member.oauthProvider)
        }
    }

    private fun approve(member: Member): MemberApprovalResponse {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val processedAt = LocalDateTime.now()
        member.status = MemberStatus.ACTIVE
        member.approvedAt = processedAt

        // 승인 시점에 TEACHER Role을 부여한다. PENDING 교직원은 승인 우회 방지를 위해 Role이 없으므로
        // (OAuthMemberPortImpl 참고) 새로 저장한다. 만일을 대비해 이미 있으면 중복 저장하지 않는다.
        val roleId = MemberRoleId(memberId = memberId, role = RoleType.TEACHER)
        if (!memberRoleRepository.existsById(roleId)) {
            memberRoleRepository.save(MemberRole(roleId))
        }

        eventPublisher.publishEvent(
            MemberApprovalProcessedEvent(memberId = memberId, approved = true, rejectionReason = null),
        )

        return MemberApprovalResponse(
            memberId = memberId,
            status = member.status,
            reason = null,
            processedAt = processedAt,
        )
    }

    private fun reject(
        member: Member,
        reason: String?,
    ): MemberApprovalResponse {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val trimmedReason = reason?.trim()
        if (trimmedReason.isNullOrEmpty()) {
            throw RejectionReasonRequiredException()
        }

        val processedAt = LocalDateTime.now()
        member.status = MemberStatus.REJECTED
        member.rejectionReason = trimmedReason
        // PENDING 교직원은 Role이 없으므로(위 approve 주석 참고) 거절 시 TEACHER Role은 애초에
        // 존재하지 않는다. 별도 Role 제거 없이도 "거절된 회원에게 TEACHER Role 없음" 불변식이 유지된다.

        eventPublisher.publishEvent(
            MemberApprovalProcessedEvent(memberId = memberId, approved = false, rejectionReason = trimmedReason),
        )

        return MemberApprovalResponse(
            memberId = memberId,
            status = member.status,
            reason = trimmedReason,
            processedAt = processedAt,
        )
    }
}
