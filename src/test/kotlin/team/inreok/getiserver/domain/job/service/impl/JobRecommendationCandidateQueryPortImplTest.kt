package team.inreok.getiserver.domain.job.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
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
    }

    @Test
    fun `후보가 없으면 빈 목록을 반환한다`() {
        given(jobRepository.findAllByStatusAndDeletedAtIsNull(JobStatus.PUBLISHED)).willReturn(emptyList())

        val result = port.findAllPublished()

        assertThat(result).isEmpty()
    }
}
