package team.inreok.getiserver.domain.recommendation.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository

/**
 * [JobBookmarkAccessorImpl]이 [MemberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn]을
 * 그대로 감싸는지 검증한다(Issue #171). 새 계산 로직이 없으므로 위임 자체와 경계 조건만 다룬다.
 */
@ExtendWith(MockitoExtension::class)
class JobBookmarkAccessorImplTest {
    @Mock
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    private val accessor by lazy { JobBookmarkAccessorImpl(memberJobPreferenceRepository) }

    @Test
    fun `jobIds 중 북마크한 jobId만 Set으로 돌려준다`() {
        given(memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn(REQUESTER_ID, setOf(1L, 2L, 3L)))
            .willReturn(listOf(2L))

        val result = accessor.findAllByJobIds(setOf(1L, 2L, 3L), REQUESTER_ID)

        assertThat(result).containsExactly(2L)
    }

    @Test
    fun `jobIds가 비어 있으면 Repository를 호출하지 않고 빈 Set을 돌려준다`() {
        val result = accessor.findAllByJobIds(emptySet(), REQUESTER_ID)

        assertThat(result).isEmpty()
        verify(memberJobPreferenceRepository, never())
            .findBookmarkedJobIdsByMemberIdAndJobIdIn(anyLong(), anySet() ?: emptySet())
    }

    @Test
    fun `다른 회원이 북마크한 jobId는 포함하지 않는다`() {
        given(memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn(REQUESTER_ID, setOf(1L)))
            .willReturn(emptyList())

        val result = accessor.findAllByJobIds(setOf(1L), REQUESTER_ID)

        assertThat(result).isEmpty()
    }

    private companion object {
        private const val REQUESTER_ID = 7L
    }
}
