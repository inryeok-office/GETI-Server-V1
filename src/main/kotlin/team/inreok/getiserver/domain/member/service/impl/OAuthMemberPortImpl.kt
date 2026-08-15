package team.inreok.getiserver.domain.member.service.impl

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberLoginNotAllowedException
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
 * 최초 로그인 시 Provider별로 상태를 다르게 만든다(Issue #99): 학생(DG)은 즉시 활성화(ACTIVE)해
 * STUDENT Role을 부여하고, 교직원(GOOGLE)은 승인 대기(PENDING)로 두며 Role을 **부여하지 않는다**.
 * `SecurityConfig`가 status가 아니라 Role만으로 인가하므로, PENDING 회원에게 권한 Role을 미리 주면
 * 승인 절차를 우회한다. 교직원 Role은 이후 승인 시점에 부여한다.
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
            return existingIdentity(existing)
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
                    Member(
                        oauthProvider = oauthProvider,
                        oauthSubject = subject,
                        email = email,
                        status = initialStatusFor(oauthProvider),
                    ),
                )
            } catch (ex: DataIntegrityViolationException) {
                // 같은 사용자가 동시에 최초 로그인하면 한쪽이 UNIQUE 제약에 걸린다. 이 경우 먼저
                // 만들어진 회원을 재조회해 그대로 재사용한다(멱등). 재조회로도 없으면 이메일 경합
                // 등 다른 원인이므로 이메일 충돌로 보고한다.
                log.debug("최초 로그인 동시 생성으로 UNIQUE 제약 위반, 기존 회원을 재확인합니다(provider={})", oauthProvider, ex)
                val existing =
                    memberRepository.findByOauthProviderAndOauthSubject(oauthProvider, subject)
                        ?: throw OAuthEmailAlreadyRegisteredException(email)
                return existingIdentity(existing)
            }

        val memberId = requireNotNull(saved.id) { "저장된 Member는 id를 가져야 합니다." }
        // 승인 대기(PENDING) 회원에게는 Role을 부여하지 않는다 -- 부여하면 status를 보지 않는
        // 인가(SecurityConfig)에서 승인 없이 권한을 갖게 된다. 즉시 활성화되는 회원만 부여한다.
        val grantedRoles =
            autoGrantRoleFor(oauthProvider)?.let { role ->
                memberRoleRepository.save(MemberRole(MemberRoleId(memberId = memberId, role = role)))
                listOf(role.name)
            } ?: emptyList()
        return OAuthMemberIdentity(
            memberId = memberId,
            status = saved.status.name,
            roles = grantedRoles,
            isNewMember = true,
        )
    }

    /**
     * 기존 회원의 로그인 결과를 만든다. 로그인이 허용되지 않는 상태(REJECTED/SUSPENDED/WITHDRAWN)면
     * Token을 발급하지 않고 거부한다(Issue #104). 이렇게 하지 않으면 정지·탈퇴된 회원이 재로그인으로
     * 이전 Role이 담긴 유효 Token을 계속 받는다.
     */
    private fun existingIdentity(member: Member): OAuthMemberIdentity {
        if (member.status !in LOGIN_ALLOWED_STATUSES) {
            throw MemberLoginNotAllowedException(member.status)
        }
        return identityOf(member, isNewMember = false)
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
         * 로그인이 허용되는 회원 상태(Issue #104). ACTIVE(정상)와 PENDING(승인 대기 -- 로그인은 되되
         * #103에서 이미 무권한)만 허용하고, 그 외(REJECTED/SUSPENDED/WITHDRAWN)는 거부한다. 새 상태가
         * 추가돼도 명시적으로 허용하기 전까지 기본 차단되도록 Allowlist로 둔다.
         */
        val LOGIN_ALLOWED_STATUSES = setOf(MemberStatus.ACTIVE, MemberStatus.PENDING)

        /**
         * Provider별 최초 로그인 상태. 학생(DG)은 즉시 활성화(ACTIVE)해 로그인 후 바로 서비스를
         * 이용하고, 교직원(GOOGLE)은 승인 대기(PENDING)로 생성한다(Issue #99 완료 조건).
         */
        fun initialStatusFor(provider: OAuthProvider): MemberStatus =
            when (provider) {
                OAuthProvider.GOOGLE -> MemberStatus.PENDING
                OAuthProvider.DG -> MemberStatus.ACTIVE
            }

        /**
         * 가입 즉시 활성화되는 회원에게 부여할 기본 Role. 학생(DG)은 STUDENT를 바로 부여한다.
         * 교직원(GOOGLE)은 PENDING이라 여기서 Role을 주지 않고(승인 우회 방지), 승인 시점에 부여한다.
         */
        fun autoGrantRoleFor(provider: OAuthProvider): RoleType? =
            when (provider) {
                OAuthProvider.GOOGLE -> null
                OAuthProvider.DG -> RoleType.STUDENT
            }
    }
}
