package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class ApplicationApplicantProfileQueryPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val port by lazy { ApplicationApplicantProfileQueryPortImpl(memberRepository) }

    @Test
    fun `id 집합이 비어 있으면 Repository를 호출하지 않고 빈 Map을 반환한다`() {
        val result = port.findAllByIds(emptySet())

        assertThat(result).isEmpty()
        org.mockito.Mockito.verifyNoInteractions(memberRepository)
    }

    @Test
    fun `존재하지 않는 id는 결과 Map에서 빠진다`() {
        given(memberRepository.findAllById(setOf(1L, 2L))).willReturn(listOf(memberOf(1L)))

        val result = port.findAllByIds(setOf(1L, 2L))

        assertThat(result.keys).containsExactly(1L)
    }

    @Test
    fun `Member의 표시 정보를 Snapshot으로 변환한다`() {
        given(memberRepository.findAllById(setOf(1L))).willReturn(listOf(memberOf(1L)))

        val result = port.findAllByIds(setOf(1L))

        assertThat(result).hasSize(1)
        val snapshot = requireNotNull(result[1L])
        assertThat(snapshot.memberId).isEqualTo(1L)
        assertThat(snapshot.name).isEqualTo("홍길동")
        assertThat(snapshot.profileImageFileId).isEqualTo(50L)
        assertThat(snapshot.cohort).isEqualTo(10)
        assertThat(snapshot.department).isEqualTo("SW_DEVELOPMENT")
    }

    private fun memberOf(id: Long): Member =
        Member(
            oauthProvider = OAuthProvider.DG,
            oauthSubject = "subject-$id",
            email = "student$id@example.com",
            status = MemberStatus.ACTIVE,
        ).apply {
            this.id = id
            name = "홍길동"
            profileImageFileId = 50L
            cohort = 10
            department = DepartmentType.SW_DEVELOPMENT
        }
}
