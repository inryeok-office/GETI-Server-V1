package team.inreok.getiserver.domain.member.service.impl

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.OAuthEmailAlreadyRegisteredException
import team.inreok.getiserver.domain.member.query.OAuthMemberIdentity
import team.inreok.getiserver.domain.member.query.OAuthMemberPort
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository

/**
 * `auth` Module에 공개된 [OAuthMemberPort]의 구현이다. `MemberServiceImpl`이 프로필·검색 등으로
 * 이미 크고 이 책임(OAuth 회원 조회/생성)은 그와 무관하므로, 다른 `*QueryPortImpl`과 같은 방식으로
 * 분리한다.
 *
 * 최초 로그인은 회원을 `status=PENDING`으로 만들고 Provider에 따른 기본 Role을 부여한다. 승인 전까지
 * PENDING이므로 이 Role은 잠정값이며, 관리자가 승인 단계에서 실제 Role로 조정한다(Issue #48).
 */
@Service
class OAuthMemberPortImpl(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
) : OAuthMemberPort {
    private val log = LoggerFactory.getLogger(OAuthMemberPortImpl::class.java)

    @Transactional
    override fun findOrCreateByOAuth(
        provider: String,
        subject: String,
        email: String,
    ): OAuthMemberIdentity {
        val oauthProvider = OAuthProvider.valueOf(provider.uppercase())

        memberRepository.findByOauthProviderAndOauthSubject(oauthProvider, subject)?.let { existing ->
            return identityOf(existing, isNewMember = false)
        }

        // 같은 이메일이 다른 OAuth 계정으로 이미 가입돼 있으면 새 회원을 만들 수 없다(uk_members_email).
        if (memberRepository.findByEmail(email) != null) {
            throw OAuthEmailAlreadyRegisteredException(email)
        }

        return createMember(oauthProvider, subject, email)
    }

    @Transactional(readOnly = true)
    override fun getRoles(memberId: Long): List<String> =
        memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role.name }

    private fun createMember(
        oauthProvider: OAuthProvider,
        subject: String,
        email: String,
    ): OAuthMemberIdentity {
        val saved =
            try {
                memberRepository.saveAndFlush(
                    Member(oauthProvider = oauthProvider, oauthSubject = subject, email = email),
                )
            } catch (ex: DataIntegrityViolationException) {
                // 같은 사용자가 동시에 최초 로그인하면 한쪽이 UNIQUE 제약에 걸린다. 이 경우 먼저
                // 만들어진 회원을 재조회해 그대로 재사용한다(멱등). 재조회로도 없으면 이메일 경합
                // 등 다른 원인이므로 이메일 충돌로 보고한다.
                log.debug("최초 로그인 동시 생성으로 UNIQUE 제약 위반, 기존 회원을 재확인합니다(provider={})", oauthProvider, ex)
                val existing =
                    memberRepository.findByOauthProviderAndOauthSubject(oauthProvider, subject)
                        ?: throw OAuthEmailAlreadyRegisteredException(email)
                return identityOf(existing, isNewMember = false)
            }

        val memberId = requireNotNull(saved.id) { "저장된 Member는 id를 가져야 합니다." }
        val defaultRole = defaultRoleFor(oauthProvider)
        memberRoleRepository.save(MemberRole(MemberRoleId(memberId = memberId, role = defaultRole)))
        return OAuthMemberIdentity(
            memberId = memberId,
            status = saved.status.name,
            roles = listOf(defaultRole.name),
            isNewMember = true,
        )
    }

    private fun identityOf(
        member: Member,
        isNewMember: Boolean,
    ): OAuthMemberIdentity {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        return OAuthMemberIdentity(
            memberId = memberId,
            status = member.status.name,
            roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role.name },
            isNewMember = isNewMember,
        )
    }

    private companion object {
        /**
         * Provider별 최초 로그인 기본 Role. GOOGLE(교직원)은 TEACHER로 시작해 승인 시 관리자가
         * DEVELOPER 등으로 조정한다(Issue #48 확정). DG(학생 로그인)는 STUDENT다.
         */
        fun defaultRoleFor(provider: OAuthProvider): RoleType =
            when (provider) {
                OAuthProvider.GOOGLE -> RoleType.TEACHER
                OAuthProvider.DG -> RoleType.STUDENT
            }
    }
}
