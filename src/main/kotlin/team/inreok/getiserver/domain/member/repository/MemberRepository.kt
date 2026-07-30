package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?

    fun findByOauthProviderAndOauthSubject(
        oauthProvider: OAuthProvider,
        oauthSubject: String,
    ): Member?
}
