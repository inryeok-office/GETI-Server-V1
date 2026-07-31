package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.exception.NameRequiredException
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class MemberSearchServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val service by lazy { MemberSearchService(memberRepository) }

    @Test
    fun `이름으로 검색하면 검색 결과를 Page 형태로 반환한다`() {
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-1",
                email = "student@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = true,
            ).apply {
                id = 1L
                name = "홍길동"
            }
        val pageable = PageRequest.of(0, 20)
        given(memberRepository.search("홍길동", null, null, null, null, null, pageable))
            .willReturn(PageImpl(listOf(member), pageable, 1))

        val result = service.search("홍길동", null, null, null, null, null, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].name).isEqualTo("홍길동")
        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.first).isTrue()
        assertThat(result.last).isTrue()
    }

    @Test
    fun `name이 없으면 NameRequiredException을 던진다`() {
        assertThatThrownBy { service.search(null, null, null, null, null, null, PageRequest.of(0, 20)) }
            .isInstanceOf(NameRequiredException::class.java)
    }

    @Test
    fun `name이 공백이면 NameRequiredException을 던진다`() {
        assertThatThrownBy { service.search("   ", null, null, null, null, null, PageRequest.of(0, 20)) }
            .isInstanceOf(NameRequiredException::class.java)
    }

    @Test
    fun `검색어에 LIKE Wildcard가 있으면 이스케이프해서 Repository에 전달한다`() {
        val pageable = PageRequest.of(0, 20)
        given(memberRepository.search("100\\%", null, null, null, null, null, pageable))
            .willReturn(PageImpl(emptyList(), pageable, 0))

        val result = service.search("100%", null, null, null, null, null, pageable)

        assertThat(result.content).isEmpty()
    }
}
