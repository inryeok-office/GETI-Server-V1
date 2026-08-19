package team.inreok.getiserver.domain.search.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.query.JobIndexQueryPort
import team.inreok.getiserver.domain.job.query.JobIndexSnapshot
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class JobIndexDocumentBuilderTest {
    @Mock
    private lateinit var jobIndexQueryPort: JobIndexQueryPort

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort

    private val builder: JobIndexDocumentBuilder by lazy {
        JobIndexDocumentBuilder(jobIndexQueryPort, companyQuery, aiAnalysisSearchQueryPort)
    }

    @Test
    fun `공개 대상이 아니면 null을 반환한다`() {
        given(jobIndexQueryPort.findById(1L)).willReturn(null)

        assertThat(builder.buildFor(1L)).isNull()
    }

    @Test
    fun `Job 원본과 기업 정보를 합쳐 Document를 만든다`() {
        given(jobIndexQueryPort.findById(1L)).willReturn(snapshotOf())
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(1L, "인력개발원"))
        given(companyQuery.findActiveType(1L)).willReturn(CompanyType.GENERAL)
        given(companyQuery.findActiveLogoFileId(1L)).willReturn(43L)
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L))).willReturn(emptyMap())

        val document = builder.buildFor(1L)

        assertThat(document).isNotNull
        assertThat(document!!.id).isEqualTo("1")
        assertThat(document.jobId).isEqualTo(1L)
        assertThat(document.title).isEqualTo("2026 상반기 백엔드 채용")
        assertThat(document.companyName).isEqualTo("인력개발원")
        assertThat(document.companyType).isEqualTo("GENERAL")
        // Presigned URL이 아니라 안정적인 File ID를 저장한다(Issue #92, 색인은 URL을 만들지 않는다).
        assertThat(document.companyLogoFileId).isEqualTo(43L)
        assertThat(document.postingType).isEqualTo("MOU")
        assertThat(document.status).isEqualTo("PUBLISHED")
        assertThat(document.location).isEqualTo("서울특별시 중구")
        assertThat(document.employmentType).isEqualTo("인턴")
    }

    @Test
    fun `기업이 삭제되어 조회되지 않으면 companyName을 null로 두고 실패하지 않는다`() {
        given(jobIndexQueryPort.findById(1L)).willReturn(snapshotOf())
        given(companyQuery.findActiveSummary(1L)).willReturn(null)
        given(companyQuery.findActiveType(1L)).willReturn(null)
        given(companyQuery.findActiveLogoFileId(1L)).willReturn(null)

        val document = builder.buildFor(1L)

        assertThat(document!!.companyName).isNull()
        assertThat(document.companyType).isNull()
        assertThat(document.companyLogoFileId).isNull()
    }

    @Test
    fun `기업에 로고가 없으면 companyLogoFileId를 null로 둔다`() {
        given(jobIndexQueryPort.findById(1L)).willReturn(snapshotOf())
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(1L, "인력개발원"))
        given(companyQuery.findActiveType(1L)).willReturn(CompanyType.GENERAL)
        given(companyQuery.findActiveLogoFileId(1L)).willReturn(null)

        val document = builder.buildFor(1L)

        assertThat(document!!.companyLogoFileId).isNull()
    }

    @Test
    fun `AI 분석이 COMPLETED면 검색에 안전한 필드만 채운다`() {
        given(jobIndexQueryPort.findById(1L)).willReturn(snapshotOf())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L))).willReturn(
            mapOf(
                1L to
                    AiAnalysisSearchSnapshot(
                        requiredTechStackIds = listOf(10L),
                        preferredTechStackIds = listOf(20L, 21L),
                        highSchoolGraduateFit = "SUITABLE",
                        entryLevelFit = "SUITABLE",
                        difficulty = "NORMAL",
                    ),
            ),
        )

        val document = builder.buildFor(1L)!!

        assertThat(document.requiredTechStackIds).containsExactly(10L)
        assertThat(document.preferredTechStackIds).containsExactly(20L, 21L)
        assertThat(document.highSchoolGraduateFit).isEqualTo("SUITABLE")
        assertThat(document.entryLevelFit).isEqualTo("SUITABLE")
        assertThat(document.difficulty).isEqualTo("NORMAL")
    }

    @Test
    fun `AI 분석 결과가 없으면(아직 분석 전이거나 COMPLETED가 아니면) AI 필드는 빈 값이다`() {
        given(jobIndexQueryPort.findById(1L)).willReturn(snapshotOf())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L))).willReturn(emptyMap())

        val document = builder.buildFor(1L)!!

        assertThat(document.requiredTechStackIds).isEmpty()
        assertThat(document.preferredTechStackIds).isEmpty()
        assertThat(document.highSchoolGraduateFit).isNull()
        assertThat(document.entryLevelFit).isNull()
        assertThat(document.difficulty).isNull()
    }

    @Test
    fun `build는 호출 측이 넘겨준 AI 조회 결과를 그대로 쓰고 다시 조회하지 않는다`() {
        // 전체 Reindex(SearchReindexServiceImpl)가 Page 단위로 Batch 조회해 넘겨주는 경로를
        // 재현한다 -- build가 내부에서 단건 조회를 다시 하면 N+1이 된다(Issue #144 요구사항).
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(1L, "인력개발원"))
        given(companyQuery.findActiveType(1L)).willReturn(CompanyType.GENERAL)
        given(companyQuery.findActiveLogoFileId(1L)).willReturn(null)
        val aiSnapshot =
            AiAnalysisSearchSnapshot(
                requiredTechStackIds = listOf(10L),
                preferredTechStackIds = emptyList(),
                highSchoolGraduateFit = "SUITABLE",
                entryLevelFit = "CONDITIONAL",
                difficulty = "HARD",
            )

        val document = builder.build(snapshotOf(), aiSnapshot)

        assertThat(document.requiredTechStackIds).containsExactly(10L)
        assertThat(document.difficulty).isEqualTo("HARD")
        verifyNoInteractions(aiAnalysisSearchQueryPort)
    }

    private fun snapshotOf() =
        JobIndexSnapshot(
            jobId = 1L,
            title = "2026 상반기 백엔드 채용",
            content = "## 모집 부문",
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            status = "PUBLISHED",
            companyId = 1L,
            targetGrade = 3,
            capacity = 2,
            firstComeServed = false,
            viewCount = 10,
            publishedAt = LocalDateTime.of(2026, 7, 25, 9, 0),
            startDate = null,
            endDate = null,
            sourceName = "MMA",
            location = "서울특별시 중구",
            employmentType = "인턴",
        )
}
