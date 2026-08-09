package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.NameRequiredException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.service.impl.MemberSearchServiceImpl

@ExtendWith(MockitoExtension::class)
class MemberSearchServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    private val service: MemberSearchService by lazy {
        MemberSearchServiceImpl(memberRepository, fileUrlPort)
    }

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
        given(
            memberRepository.search(
                "홍길동",
                MemberStatus.ACTIVE,
                RoleType.STUDENT,
                null,
                null,
                null,
                null,
                null,
                pageable,
            ),
        ).willReturn(PageImpl(listOf(member), pageable, 1))

        val result = service.search(REQUESTER_ID, "홍길동", null, null, null, null, null, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].name).isEqualTo("홍길동")
        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.first).isTrue()
        assertThat(result.last).isTrue()
    }

    @Test
    fun `name이 없으면 NameRequiredException을 던진다`() {
        assertThatThrownBy { service.search(REQUESTER_ID, null, null, null, null, null, null, PageRequest.of(0, 20)) }
            .isInstanceOf(NameRequiredException::class.java)
    }

    @Test
    fun `name이 공백이면 NameRequiredException을 던진다`() {
        assertThatThrownBy { service.search(REQUESTER_ID, "   ", null, null, null, null, null, PageRequest.of(0, 20)) }
            .isInstanceOf(NameRequiredException::class.java)
    }

    @Test
    fun `검색어에 LIKE Wildcard가 있으면 이스케이프해서 Repository에 전달한다`() {
        val pageable = PageRequest.of(0, 20)
        given(
            memberRepository.search(
                "100\\%",
                MemberStatus.ACTIVE,
                RoleType.STUDENT,
                null,
                null,
                null,
                null,
                null,
                pageable,
            ),
        ).willReturn(PageImpl(emptyList(), pageable, 0))

        val result = service.search(REQUESTER_ID, "100%", null, null, null, null, null, pageable)

        assertThat(result.content).isEmpty()
    }

    @Test
    fun `목록의 프로필 이미지는 한 번의 배치 호출로 URL로 바뀐다`() {
        val withImage = memberOf(id = 1L, name = "이미지있음").apply { profileImageFileId = FILE_ID }
        val withoutImage = memberOf(id = 2L, name = "이미지없음")
        val pageable = PageRequest.of(0, 20)
        given(
            memberRepository.search(
                "홍길동",
                MemberStatus.ACTIVE,
                RoleType.STUDENT,
                null,
                null,
                null,
                null,
                null,
                pageable,
            ),
        ).willReturn(PageImpl(listOf(withImage, withoutImage), pageable, 2))
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(FILE_ID)))
            .willReturn(mapOf(FILE_ID to IMAGE_URL))

        val result = service.search(REQUESTER_ID, "홍길동", null, null, null, null, null, pageable)

        assertThat(result.content[0].profileImageUrl).isEqualTo(IMAGE_URL)
        assertThat(result.content[1].profileImageUrl).isNull()
        // 회원마다 단건 발급하면 목록 크기만큼 반복된다(N+1). 정확히 한 번이어야 한다.
        verify(fileUrlPort, times(1)).presignedImageUrls(REQUESTER_ID, listOf(FILE_ID))
    }

    private fun memberOf(
        id: Long,
        name: String,
    ) = Member(
        oauthProvider = OAuthProvider.GOOGLE,
        oauthSubject = "subject-$id",
        email = "student$id@example.com",
        status = MemberStatus.ACTIVE,
        profilePublic = true,
    ).apply {
        this.id = id
        this.name = name
    }

    private companion object {
        private const val REQUESTER_ID = 100L
        private const val FILE_ID = 42L
        private const val IMAGE_URL = "https://storage.example/profile?signature=test"
    }
}
