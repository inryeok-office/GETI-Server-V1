package team.inreok.getiserver.domain.job.upsert

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.upsert.impl.CollectedJobUpsertUseCaseImpl

@ExtendWith(MockitoExtension::class)
class CollectedJobUpsertUseCaseImplTest {
    @Mock
    private lateinit var jobRepository: JobRepository

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private val useCase: CollectedJobUpsertUseCase by lazy {
        CollectedJobUpsertUseCaseImpl(
            jobRepository,
            companyQuery,
            eventPublisher,
        )
    }

    private fun commandOf(
        externalJobId: String = "EXT-1",
        publish: Boolean = true,
        content: String? = "본문",
        externalUrl: String? = "https://example.com/job/1",
        location: String? = null,
        employmentType: String? = null,
    ) = CollectedJobUpsertCommand(
        companyId = 1L,
        sourceName = "MMA",
        externalJobId = externalJobId,
        title = "백엔드 개발자",
        content = content,
        externalUrl = externalUrl,
        startDate = null,
        endDate = null,
        publish = publish,
        location = location,
        employmentType = employmentType,
    )

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(CompanyServiceTest.anyCompany()와 동일한 방식).
    // ArgumentCaptor.capture()의 Elvis 대체값으로 anyJob()을 쓰면 Matcher가 두 번 등록되어
    // InvalidUseOfMatchersException이 난다. capture()에는 Matcher가 아닌 평범한 객체를 쓴다
    // (JobServiceTest.newJob()과 같은 방식).
    private fun fallbackJob(): Job =
        Job(
            companyId = 0,
            type = PostingType.GENERAL,
            applicationMethod = ApplicationMethod.EXTERNAL,
            title = "",
        )

    private fun anyJob(): Job =
        any(Job::class.java)
            ?: Job(
                companyId = 0,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "",
            )

    @Test
    fun `존재하지 않는 기업을 참조하면 JobCompanyNotFoundException이 발생한다`() {
        given(companyQuery.findActiveSummary(1L)).willReturn(null)

        assertThatThrownBy { useCase.upsert(commandOf()) }
            .isInstanceOf(JobCompanyNotFoundException::class.java)
    }

    @Test
    fun `동일 출처·외부 ID가 없으면 새 공고를 생성한다`() {
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(null)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer {
            (it.arguments[0] as Job).apply { id = 10L }
        }

        val result = useCase.upsert(commandOf())

        assertThat(result.outcome).isEqualTo(JobImportOutcome.CREATED)
        assertThat(result.jobId).isEqualTo(10L)
        assertThat(result.published).isTrue()
    }

    @Test
    fun `동일 출처·외부 ID가 이미 있으면 기존 공고를 갱신하고 새로 생성하지 않는다`() {
        val existing =
            Job(
                companyId = 1L,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "이전 제목",
                status = JobStatus.PUBLISHED,
            ).apply {
                id = 20L
                sourceName = "MMA"
                externalJobId = "EXT-1"
            }
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(existing)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer { it.arguments[0] as Job }

        val result = useCase.upsert(commandOf(content = "갱신된 본문"))

        assertThat(result.outcome).isEqualTo(JobImportOutcome.UPDATED)
        assertThat(result.jobId).isEqualTo(20L)
        assertThat(existing.bodyMarkdown).isEqualTo("갱신된 본문")
    }

    @Test
    fun `동일 출처·외부 ID의 내용이 그대로면 UNCHANGED를 반환하고 저장하지 않는다`() {
        val existing =
            Job(
                companyId = 1L,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "백엔드 개발자",
                status = JobStatus.PUBLISHED,
            ).apply {
                id = 30L
                sourceName = "MMA"
                externalJobId = "EXT-1"
                bodyMarkdown = "본문"
                externalUrl = "https://example.com/job/1"
            }
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(existing)

        val result = useCase.upsert(commandOf())

        assertThat(result.outcome).isEqualTo(JobImportOutcome.UNCHANGED)
        assertThat(result.jobId).isEqualTo(30L)
        verify(jobRepository, never()).saveAndFlush(anyJob())
    }

    @Test
    fun `Provider가 준 근무지역과 고용형태를 Job에 반영한다`() {
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(null)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer {
            (it.arguments[0] as Job).apply { id = 10L }
        }

        useCase.upsert(commandOf(location = " 경상남도 창원시 ", employmentType = "일반정규직"))

        val captor = org.mockito.ArgumentCaptor.forClass(Job::class.java)
        verify(jobRepository).saveAndFlush(captor.capture() ?: fallbackJob())
        assertThat(captor.value.location).isEqualTo("경상남도 창원시")
        assertThat(captor.value.employmentType).isEqualTo("일반정규직")
    }

    // Provider 값은 우리가 고칠 수 없으므로 길이 초과를 예외로 다루면 표시용 문자열 하나 때문에
    // 공고 전체가 CollectionRunError로 버려진다. 잘라서라도 저장한다(Issue #169).
    @Test
    fun `255자를 넘는 근무지역은 잘라서 저장한다`() {
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(null)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer {
            (it.arguments[0] as Job).apply { id = 10L }
        }

        useCase.upsert(commandOf(location = "가".repeat(300)))

        val captor = org.mockito.ArgumentCaptor.forClass(Job::class.java)
        verify(jobRepository).saveAndFlush(captor.capture() ?: fallbackJob())
        assertThat(captor.value.location).hasSize(255)
    }

    @Test
    fun `공백뿐인 고용형태는 null로 저장한다`() {
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(null)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer {
            (it.arguments[0] as Job).apply { id = 10L }
        }

        useCase.upsert(commandOf(employmentType = "   "))

        val captor = org.mockito.ArgumentCaptor.forClass(Job::class.java)
        verify(jobRepository).saveAndFlush(captor.capture() ?: fallbackJob())
        assertThat(captor.value.employmentType).isNull()
    }

    // 근무지역만 바뀌었는데 UNCHANGED로 판정하면 이미 수집된 공고는 영원히 값을 받지 못한다.
    @Test
    fun `근무지역만 바뀌어도 UPDATED로 갱신한다`() {
        val existing =
            Job(
                companyId = 1L,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "백엔드 개발자",
                status = JobStatus.PUBLISHED,
            ).apply {
                id = 40L
                sourceName = "MMA"
                externalJobId = "EXT-1"
                bodyMarkdown = "본문"
                externalUrl = "https://example.com/job/1"
            }
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(existing)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer { it.arguments[0] as Job }

        val result = useCase.upsert(commandOf(location = "서울특별시 중구"))

        assertThat(result.outcome).isEqualTo(JobImportOutcome.UPDATED)
        assertThat(existing.location).isEqualTo("서울특별시 중구")
    }

    @Test
    fun `게시 필수값이 없으면 publish 요청이어도 DRAFT로 저장된다`() {
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(jobRepository.findBySourceNameAndExternalJobId("MMA", "EXT-1")).willReturn(null)
        given(jobRepository.saveAndFlush(anyJob())).willAnswer { (it.arguments[0] as Job).apply { id = 11L } }

        // 품질 경고(PARTIAL) 공고를 흉내낸다: 본문이 없어 게시 필수값을 만족하지 못한다.
        val result = useCase.upsert(commandOf(content = null, publish = true))

        assertThat(result.published).isFalse()
    }
}
