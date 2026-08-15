package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `auth` Module이 OAuth 로그인 성공 후 회원을 조회·생성하고 역할을 읽는 유일한 공개 계약이다
 * (Issue #48). `auth`는 이 Interface를 통해서만 Member를 다루고, `Member` Entity나
 * `MemberRepository`를 직접 참조하지 않는다(`docs/architecture/modularity.md`).
 *
 * 조회 전용인 다른 `*QueryPort`와 달리 [findOrCreateByOAuth]는 최초 로그인 시 회원을 **생성**한다.
 * 그 쓰기 책임을 `auth`가 가지지 않도록(회원 데이터·Role 부여는 Member 도메인 소관), 생성까지
 * 이 Port 뒤에 감춘다.
 */
@NamedInterface
interface OAuthMemberPort {
    /**
     * OAuth Provider 식별값으로 회원을 조회하고, 없으면 신규 생성한다(`status=PENDING`).
     *
     * @param provider URL Path Variable과 같은 provider 값(`google`, `dg`). Member 도메인이 자체
     *   [team.inreok.getiserver.domain.member.entity.type.OAuthProvider]로 매핑한다.
     * @param subject Provider가 발급한 사용자 고유 식별값.
     * @param email Provider UserInfo에서 조회한 이메일.
     * @throws team.inreok.getiserver.domain.member.exception.OAuthEmailAlreadyRegisteredException
     *   같은 이메일이 다른 OAuth 계정으로 이미 가입된 경우.
     */
    fun findOrCreateByOAuth(
        provider: String,
        subject: String,
        email: String,
    ): OAuthMemberIdentity

    /** 회원의 현재 Role 이름 목록이다. 회원이 없거나 Role이 없으면 빈 List. Token 재발급에 쓴다. */
    fun getRoles(memberId: Long): List<String>
}

/**
 * OAuth 로그인 대상 회원의 식별 결과다. Access Token Claim과 Frontend 진입 분기(신규/승인 대기)에
 * 필요한 최소 정보만 담는다.
 */
@NamedInterface
data class OAuthMemberIdentity(
    val memberId: Long,
    /** [team.inreok.getiserver.domain.member.entity.type.MemberStatus] 이름. 예: `PENDING`. */
    val status: String,
    /** [team.inreok.getiserver.domain.member.entity.type.RoleType] 이름 목록. 예: `["TEACHER"]`. */
    val roles: List<String>,
    /** 이번 로그인에서 새로 생성된 회원이면 true. */
    val isNewMember: Boolean,
)
