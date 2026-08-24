package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository

@ExtendWith(MockitoExtension::class)
class JobBookmarkAudienceQueryPortImplTest {
    @Mock
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    private val port by lazy { JobBookmarkAudienceQueryPortImpl(memberJobPreferenceRepository) }

    @Test
    fun `jobId를 북마크한 회원 id 목록을 그대로 반환한다`() {
        given(memberJobPreferenceRepository.findMemberIdsByJobIdAndBookmarkedTrue(1L)).willReturn(listOf(10L, 11L))

        val result = port.findBookmarkedMemberIds(1L)

        assertThat(result).containsExactly(10L, 11L)
    }

    @Test
    fun `북마크한 회원이 없으면 빈 목록을 반환한다`() {
        given(memberJobPreferenceRepository.findMemberIdsByJobIdAndBookmarkedTrue(1L)).willReturn(emptyList())

        val result = port.findBookmarkedMemberIds(1L)

        assertThat(result).isEmpty()
    }
}
