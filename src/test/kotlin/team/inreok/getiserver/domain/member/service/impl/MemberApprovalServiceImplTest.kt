package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.dto.ApprovalAction
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotApprovalTargetException
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberNotPendingException
import team.inreok.getiserver.domain.member.exception.RejectionReasonRequiredException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository

@ExtendWith(MockitoExtension::class)
class MemberApprovalServiceImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var memberRoleRepository: MemberRoleRepository

    private val service: MemberApprovalServiceImpl by lazy {
        MemberApprovalServiceImpl(memberRepository, memberRoleRepository)
    }

    private fun member(
        id: Long? = 42L,
        provider: OAuthProvider = OAuthProvider.GOOGLE,
        status: MemberStatus = MemberStatus.PENDING,
    ): Member =
        Member(
            oauthProvider = provider,
            oauthSubject = "subject-$id",
            email = "user$id@example.com",
            status = status,
        ).also { it.id = id }

    private fun anyMemberRole(): MemberRole =
        org.mockito.ArgumentMatchers.any(MemberRole::class.java) ?: MemberRole(MemberRoleId(0L, RoleType.TEACHER))

    @Test
    fun `PENDING 교직원을 승인하면 ACTIVE로 바꾸고 TEACHER Role을 부여한다`() {
        val target = member(status = MemberStatus.PENDING)
        given(memberRepository.findByIdForUpdate(42L)).willReturn(target)
        given(memberRoleRepository.existsById(MemberRoleId(memberId = 42L, role = RoleType.TEACHER))).willReturn(false)

        val result = service.process(42L, MemberApprovalRequest(action = ApprovalAction.APPROVE))

        assertThat(result.memberId).isEqualTo(42L)
        assertThat(result.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(result.reason).isNull()
        assertThat(target.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(target.approvedAt).isNotNull()

        val captor = ArgumentCaptor.forClass(MemberRole::class.java)
        verify(memberRoleRepository).save(captor.capture())
        assertThat(captor.value.id).isEqualTo(MemberRoleId(memberId = 42L, role = RoleType.TEACHER))
    }

    @Test
    fun `이미 TEACHER Role이 있으면 중복 저장하지 않는다`() {
        given(memberRepository.findByIdForUpdate(42L)).willReturn(member(status = MemberStatus.PENDING))
        given(memberRoleRepository.existsById(MemberRoleId(memberId = 42L, role = RoleType.TEACHER))).willReturn(true)

        service.process(42L, MemberApprovalRequest(action = ApprovalAction.APPROVE))

        verify(memberRoleRepository, never()).save(anyMemberRole())
    }

    @Test
    fun `PENDING 교직원을 거절하면 REJECTED로 바꾸고 사유를 저장하며 Role을 부여하지 않는다`() {
        val target = member(status = MemberStatus.PENDING)
        given(memberRepository.findByIdForUpdate(42L)).willReturn(target)

        val result = service.process(42L, MemberApprovalRequest(action = ApprovalAction.REJECT, reason = "  자료 미확인  "))

        assertThat(result.status).isEqualTo(MemberStatus.REJECTED)
        assertThat(result.reason).isEqualTo("자료 미확인")
        assertThat(target.status).isEqualTo(MemberStatus.REJECTED)
        assertThat(target.rejectionReason).isEqualTo("자료 미확인")
        assertThat(target.approvedAt).isNull()
        verify(memberRoleRepository, never()).save(anyMemberRole())
    }

    @Test
    fun `거절인데 사유가 공백이면 400 예외를 던지고 상태를 바꾸지 않는다`() {
        val target = member(status = MemberStatus.PENDING)
        given(memberRepository.findByIdForUpdate(42L)).willReturn(target)

        assertThatThrownBy {
            service.process(42L, MemberApprovalRequest(action = ApprovalAction.REJECT, reason = "   "))
        }.isInstanceOf(RejectionReasonRequiredException::class.java)

        assertThat(target.status).isEqualTo(MemberStatus.PENDING)
        verify(memberRoleRepository, never()).save(anyMemberRole())
    }

    @Test
    fun `없는 회원이면 MemberNotFoundException을 던진다`() {
        given(memberRepository.findByIdForUpdate(999L)).willReturn(null)

        assertThatThrownBy {
            service.process(999L, MemberApprovalRequest(action = ApprovalAction.APPROVE))
        }.isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `PENDING이 아니면 MemberNotPendingException을 던지고 아무것도 바꾸지 않는다`() {
        val target = member(status = MemberStatus.ACTIVE)
        given(memberRepository.findByIdForUpdate(42L)).willReturn(target)

        assertThatThrownBy {
            service.process(42L, MemberApprovalRequest(action = ApprovalAction.APPROVE))
        }.isInstanceOf(MemberNotPendingException::class.java)

        assertThat(target.status).isEqualTo(MemberStatus.ACTIVE)
        verify(memberRoleRepository, never()).save(anyMemberRole())
    }

    @Test
    fun `교직원 승인 대상이 아닌 DG 학생이면 MemberNotApprovalTargetException을 던진다`() {
        val target = member(provider = OAuthProvider.DG, status = MemberStatus.PENDING)
        given(memberRepository.findByIdForUpdate(42L)).willReturn(target)

        assertThatThrownBy {
            service.process(42L, MemberApprovalRequest(action = ApprovalAction.APPROVE))
        }.isInstanceOf(MemberNotApprovalTargetException::class.java)

        assertThat(target.status).isEqualTo(MemberStatus.PENDING)
        verify(memberRoleRepository, never()).save(anyMemberRole())
    }
}
