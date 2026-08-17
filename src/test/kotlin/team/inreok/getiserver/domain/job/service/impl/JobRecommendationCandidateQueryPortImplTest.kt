package team.inreok.getiserver.domain.job.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class JobRecommendationCandidateQueryPortImplTest {
    @Mock
    private lateinit var jobRepository: JobRepository

    private val port by lazy { JobRecommendationCandidateQueryPortImpl(jobRepository) }

    private fun jobOf(id: Long): Job =
        Job(
            companyId = 100L,
            type = PostingType.GENERAL,
            applicationMethod = ApplicationMethod.INTERNAL,
            title = "백엔드 개발자 $id",
            status = JobStatus.PUBLISHED,
        ).apply {
            this.id = id
            targetGrade = 3
            publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0)
            recruitmentEndedAt = LocalDateTime.of(2026, 9, 1, 0, 0)
        }

    @Test
    fun `PUBLISHED이고 삭제되지 않은 공고만 Snapshot으로 변환해 반환한다`() {
        given(jobRepository.findAllByStatusAndDeletedAtIsNull(JobStatus.PUBLISHED)).willReturn(
            listOf(jobOf(1L), jobOf(2L)),
        )

        val result = port.findAllPublished()

        assertThat(result).hasSize(2)
        assertThat(result[0].jobId).isEqualTo(1L)
        assertThat(result[0].status).isEqualTo("PUBLISHED")
        assertThat(result[0].targetGrade).isEqualTo(3)
        assertThat(result[0].recruitmentEndedAt).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0))
        assertThat(result[0].postingType).isEqualTo(PostingType.GENERAL)
        assertThat(result[0].applicationMethod).isEqualTo(ApplicationMethod.INTERNAL)
        assertThat(result[0].viewCount).isEqualTo(0)
    }

    @Test
    fun `후보가 없으면 빈 목록을 반환한다`() {
        given(jobRepository.findAllByStatusAndDeletedAtIsNull(JobStatus.PUBLISHED)).willReturn(emptyList())

        val result = port.findAllPublished()

        assertThat(result).isEmpty()
    }

    @Test
    fun `id로 조회하면 PUBLISHED 여부와 무관하게 삭제되지 않은 Job을 Map으로 반환한다`() {
        val closedJob =
            Job(
                companyId = 100L,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.INTERNAL,
                title = "마감된 공고",
                status = JobStatus.CLOSED,
            ).apply { id = 2L }
        given(jobRepository.findAllByIdInAndDeletedAtIsNull(setOf(1L, 2L))).willReturn(listOf(jobOf(1L), closedJob))

        val result = port.findAllByIds(setOf(1L, 2L))

        assertThat(result).hasSize(2)
        assertThat(result.getValue(1L).status).isEqualTo("PUBLISHED")
        assertThat(result.getValue(2L).status).isEqualTo("CLOSED")
    }

    @Test
    fun `삭제되었거나 존재하지 않는 id는 결과 Map에서 빠진다`() {
        given(jobRepository.findAllByIdInAndDeletedAtIsNull(setOf(1L, 999L))).willReturn(listOf(jobOf(1L)))

        val result = port.findAllByIds(setOf(1L, 999L))

        assertThat(result).hasSize(1)
        assertThat(result).containsKey(1L)
    }

    @Test
    fun `빈 id 집합을 넘기면 빈 Map을 반환하고 Repository를 호출하지 않는다`() {
        val result = port.findAllByIds(emptySet())

        assertThat(result).isEmpty()
        verifyNoInteractions(jobRepository)
    }
}
