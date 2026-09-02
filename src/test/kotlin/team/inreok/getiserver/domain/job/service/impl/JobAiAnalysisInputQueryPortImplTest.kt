package team.inreok.getiserver.domain.job.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository

@ExtendWith(MockitoExtension::class)
class JobAiAnalysisInputQueryPortImplTest {
    @Mock
    private lateinit var jobRepository: JobRepository

    @Mock
    private lateinit var companyQuery: CompanyQuery

    private val port by lazy { JobAiAnalysisInputQueryPortImpl(jobRepository, companyQuery) }

    @Test
    fun `존재하지 않거나 삭제된 공고는 null을 반환한다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThat(port.findById(1L)).isNull()
    }

    @Test
    fun `AI 분석에 필요한 값만 채워서 반환한다`() {
        val job =
            Job(
                companyId = 10L,
                type = PostingType.MOU,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "백엔드 개발자 채용",
                status = JobStatus.PUBLISHED,
            ).apply {
                id = 1L
                bodyMarkdown = "본문"
                targetGrade = 3
            }
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(job)
        given(companyQuery.findActiveSummary(10L)).willReturn(CompanySummary(10L, "인력개발원"))

        val snapshot = port.findById(1L)

        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.status).isEqualTo("PUBLISHED")
        assertThat(snapshot.companyName).isEqualTo("인력개발원")
        assertThat(snapshot.content).isEqualTo("본문")
        assertThat(snapshot.targetGrade).isEqualTo(3)
    }

    @Test
    fun `기업이 삭제됐으면 companyName은 null이다`() {
        val job =
            Job(
                companyId = 10L,
                type = PostingType.MOU,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "제목",
                status = JobStatus.PUBLISHED,
            ).apply { id = 1L }
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(job)
        given(companyQuery.findActiveSummary(10L)).willReturn(null)

        assertThat(port.findById(1L)!!.companyName).isNull()
    }
}
