package team.inreok.getiserver.domain.portfolio.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.archive.FileArchivePort
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberProfile
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberProfileQueryPort
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequestTarget
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioMaterialType
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.NoSubmissionsToExportException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.service.impl.PortfolioSubmissionAdminServiceImpl
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PortfolioSubmissionAdminServiceTest {
    @Mock
    private lateinit var requestRepository: PortfolioRequestRepository

    @Mock
    private lateinit var targetRepository: PortfolioRequestTargetRepository

    @Mock
    private lateinit var submissionRepository: PortfolioSubmissionRepository

    @Mock
    private lateinit var profileQueryPort: PortfolioTargetMemberProfileQueryPort

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var fileArchivePort: FileArchivePort

    private val service by lazy {
        PortfolioSubmissionAdminServiceImpl(
            requestRepository,
            targetRepository,
            submissionRepository,
            profileQueryPort,
            fileLinkPort,
            fileArchivePort,
        )
    }

    // --- 제출 현황 목록 ---

    @Test
    fun `대상 학생 전체를 미제출 포함해 반환하고 제출 여부와 자료 종류를 파생한다`() {
        givenRequestExists()
        givenTargets(7L, 8L, 9L)
        givenProfiles(
            profile(7L, "홍길동", 6, "SW_DEVELOPMENT"),
            profile(8L, "김철수", 6, "AI"),
            profile(9L, "이영희", 7, "SMART_IOT"),
        )
        // 7: 제출 완료 + URL·파일 => BOTH, 8: 임시저장 + 파일만 => FILE, 9: 제출물 없음 => 미제출
        given(submissionRepository.findAllByRequestId(REQUEST_ID))
            .willReturn(
                listOf(
                    submission(
                        700L,
                        7L,
                        PortfolioSubmissionStatus.SUBMITTED,
                        url = "https://p7",
                        submittedAt = SUBMITTED_AT,
                    ),
                    submission(800L, 8L, PortfolioSubmissionStatus.DRAFT, url = null),
                ),
            )
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, listOf(700L, 800L)))
            .willReturn(mapOf(700L to listOf(snapshot(1L)), 800L to listOf(snapshot(2L))))

        val result =
            service.getSubmissionStatuses(
                REQUEST_ID,
                submitted = null,
                name = null,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.totalElements).isEqualTo(3)
        val byMember = result.content.associateBy { it.memberId }
        assertThat(byMember[7L]?.submitted).isTrue()
        assertThat(byMember[7L]?.status).isEqualTo(PortfolioSubmissionStatus.SUBMITTED)
        assertThat(byMember[7L]?.materialType).isEqualTo(PortfolioMaterialType.BOTH)
        assertThat(byMember[7L]?.submittedAt).isEqualTo(SUBMITTED_AT)
        assertThat(byMember[8L]?.submitted).isFalse()
        assertThat(byMember[8L]?.status).isEqualTo(PortfolioSubmissionStatus.DRAFT)
        assertThat(byMember[8L]?.materialType).isEqualTo(PortfolioMaterialType.FILE)
        assertThat(byMember[9L]?.submitted).isFalse()
        assertThat(byMember[9L]?.status).isNull()
        assertThat(byMember[9L]?.materialType).isNull()
        assertThat(byMember[9L]?.studentName).isEqualTo("이영희")
    }

    @Test
    fun `submitted true 필터는 제출 완료 학생만 남긴다`() {
        givenRequestExists()
        givenTargets(7L, 8L, 9L)
        givenProfiles(profile(7L, "홍길동"), profile(8L, "김철수"), profile(9L, "이영희"))
        given(submissionRepository.findAllByRequestId(REQUEST_ID))
            .willReturn(
                listOf(
                    submission(700L, 7L, PortfolioSubmissionStatus.SUBMITTED, url = "https://p7"),
                    submission(800L, 8L, PortfolioSubmissionStatus.DRAFT, url = null),
                ),
            )
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, listOf(700L, 800L)))
            .willReturn(emptyMap())

        val result =
            service.getSubmissionStatuses(
                REQUEST_ID,
                submitted = true,
                name = null,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.memberId }).containsExactly(7L)
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun `name 필터는 이름 부분 일치로 좁힌다`() {
        givenRequestExists()
        givenTargets(7L, 8L)
        givenProfiles(profile(7L, "홍길동"), profile(8L, "김철수"))
        given(submissionRepository.findAllByRequestId(REQUEST_ID)).willReturn(emptyList())

        val result =
            service.getSubmissionStatuses(
                REQUEST_ID,
                submitted = null,
                name = "길동",
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.studentName }).containsExactly("홍길동")
    }

    @Test
    fun `Pagination은 필터 후 전체 개수를 기준으로 나눈다`() {
        givenRequestExists()
        givenTargets(7L, 8L, 9L)
        givenProfiles(profile(7L, "AAA"), profile(8L, "BBB"), profile(9L, "CCC"))
        given(submissionRepository.findAllByRequestId(REQUEST_ID)).willReturn(emptyList())

        val secondPage =
            service.getSubmissionStatuses(
                REQUEST_ID,
                submitted = null,
                name = null,
                pageable = PageRequest.of(1, 2),
            )

        assertThat(secondPage.page).isEqualTo(1)
        assertThat(secondPage.totalElements).isEqualTo(3)
        assertThat(secondPage.totalPages).isEqualTo(2)
        assertThat(secondPage.content).hasSize(1)
        assertThat(secondPage.last).isTrue()
    }

    @Test
    fun `대상이 없으면 빈 목록을 반환한다`() {
        givenRequestExists()
        given(targetRepository.findAllByRequestId(REQUEST_ID)).willReturn(emptyList())

        val result =
            service.getSubmissionStatuses(
                REQUEST_ID,
                submitted = null,
                name = null,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0)
        verifyNoInteractions(profileQueryPort)
    }

    @Test
    fun `없거나 삭제된 요청의 현황 조회는 404를 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(REQUEST_ID)).willReturn(null)

        assertThatThrownBy {
            service.getSubmissionStatuses(REQUEST_ID, submitted = null, name = null, pageable = PageRequest.of(0, 20))
        }.isInstanceOf(PortfolioRequestNotFoundException::class.java)
    }

    // --- 일괄 다운로드 ---

    @Test
    fun `Export는 학생별 파일을 memberId와 이름이 포함된 이름으로 담는다`() {
        givenRequestExists()
        given(submissionRepository.findAllByRequestId(REQUEST_ID))
            .willReturn(
                listOf(
                    submission(700L, 7L, PortfolioSubmissionStatus.SUBMITTED, url = "https://p7"),
                    submission(800L, 8L, PortfolioSubmissionStatus.DRAFT, url = null),
                ),
            )
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, listOf(700L, 800L)))
            .willReturn(mapOf(700L to listOf(snapshot(11L, "a.pdf")), 800L to listOf(snapshot(22L, "b.pdf"))))
        givenProfiles(profile(7L, "홍길동"), profile(8L, "김철수"))

        val entries = service.buildExportEntries(REQUEST_ID, submittedOnly = false)

        assertThat(entries.map { it.fileId }).containsExactly(11L, 22L)
        assertThat(entries.map { it.displayName })
            .containsExactly("student-7_홍길동_a.pdf", "student-8_김철수_b.pdf")
    }

    @Test
    fun `submittedOnly true면 제출 완료 제출물의 파일만 담는다`() {
        givenRequestExists()
        given(submissionRepository.findAllByRequestId(REQUEST_ID))
            .willReturn(
                listOf(
                    submission(700L, 7L, PortfolioSubmissionStatus.SUBMITTED, url = null),
                    submission(800L, 8L, PortfolioSubmissionStatus.DRAFT, url = null),
                ),
            )
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, listOf(700L)))
            .willReturn(mapOf(700L to listOf(snapshot(11L, "a.pdf"))))
        givenProfiles(profile(7L, "홍길동"))

        val entries = service.buildExportEntries(REQUEST_ID, submittedOnly = true)

        assertThat(entries.map { it.fileId }).containsExactly(11L)
    }

    @Test
    fun `제출물이 없으면 NO_SUBMISSIONS_TO_EXPORT를 던진다`() {
        givenRequestExists()
        given(submissionRepository.findAllByRequestId(REQUEST_ID)).willReturn(emptyList())

        assertThatThrownBy { service.buildExportEntries(REQUEST_ID, submittedOnly = false) }
            .isInstanceOf(NoSubmissionsToExportException::class.java)
    }

    @Test
    fun `제출물은 있으나 연결 파일이 하나도 없으면 NO_SUBMISSIONS_TO_EXPORT를 던진다`() {
        givenRequestExists()
        given(submissionRepository.findAllByRequestId(REQUEST_ID))
            .willReturn(listOf(submission(700L, 7L, PortfolioSubmissionStatus.SUBMITTED, url = "https://p7")))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, listOf(700L))).willReturn(emptyMap())
        givenProfiles(profile(7L, "홍길동"))

        assertThatThrownBy { service.buildExportEntries(REQUEST_ID, submittedOnly = false) }
            .isInstanceOf(NoSubmissionsToExportException::class.java)
    }

    @Test
    fun `writeZip은 FileArchivePort에 위임한다`() {
        val entries = listOf(FileArchiveEntry(11L, "student-7_홍길동_a.pdf"))
        val out = ByteArrayOutputStream()

        service.writeZip(entries, out)

        org.mockito.BDDMockito
            .then(fileArchivePort)
            .should()
            .writeZip(entries, out)
    }

    // --- helpers ---

    private fun givenRequestExists() {
        given(requestRepository.findByIdAndDeletedAtIsNull(REQUEST_ID))
            .willReturn(
                PortfolioRequest(
                    createdByMemberId = 1L,
                    title = "2026 상반기 포트폴리오 수합",
                    dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
                    status = PortfolioRequestStatus.PUBLISHED,
                ).apply { id = REQUEST_ID },
            )
    }

    private fun givenTargets(vararg memberIds: Long) {
        given(targetRepository.findAllByRequestId(REQUEST_ID))
            .willReturn(memberIds.map { PortfolioRequestTarget(requestId = REQUEST_ID, studentMemberId = it) })
    }

    private fun givenProfiles(vararg profiles: PortfolioTargetMemberProfile) {
        given(profileQueryPort.findProfiles(profiles.map { it.memberId }.toSet()))
            .willReturn(profiles.associateBy { it.memberId })
    }

    private fun profile(
        memberId: Long,
        name: String?,
        cohort: Int? = 6,
        department: String? = "SW_DEVELOPMENT",
    ) = PortfolioTargetMemberProfile(memberId = memberId, name = name, cohort = cohort, department = department)

    private fun submission(
        id: Long,
        memberId: Long,
        status: PortfolioSubmissionStatus,
        url: String?,
        submittedAt: LocalDateTime? = null,
    ) = PortfolioSubmission(requestId = REQUEST_ID, memberId = memberId, status = status).apply {
        this.id = id
        this.portfolioUrl = url
        this.submittedAt = submittedAt
    }

    private fun snapshot(
        fileId: Long,
        originalName: String = "file$fileId.pdf",
    ) = FileSnapshot(fileId = fileId, originalName = originalName, contentType = "application/pdf", size = 1024L)

    companion object {
        private const val REQUEST_ID = 1L
        private val SUBMITTED_AT: LocalDateTime = LocalDateTime.of(2026, 9, 20, 10, 0)
    }
}
