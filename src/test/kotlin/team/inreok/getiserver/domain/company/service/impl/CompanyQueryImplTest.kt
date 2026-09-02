package team.inreok.getiserver.domain.company.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.file.link.FileUrlPort

/**
 * [CompanyQuery]가 다른 Domain(Job, Search)에 공개하는 조회 계약의 구현을 검증한다(Issue #92).
 * 로고 URL 발급은 요청자를 아는 문맥(Job 응답 조립)에서만 일어나고, 색인처럼 요청자가 없는
 * 문맥에서는 URL을 만들지 않는다는 계약이 핵심이다.
 */
@ExtendWith(MockitoExtension::class)
class CompanyQueryImplTest {
    @Mock
    private lateinit var companyRepository: CompanyRepository

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    private val query: CompanyQuery by lazy { CompanyQueryImpl(companyRepository, fileUrlPort) }

    // --- findActiveSummary ---

    @Test
    fun `requesterId 없이 조회하면 로고가 있어도 URL을 발급하지 않는다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf(logoFileId = LOGO_FILE_ID))

        val summary = query.findActiveSummary(1L)

        assertThat(summary?.logoUrl).isNull()
        verify(fileUrlPort, never()).presignedImageUrls(anyLong(), anyCollection())
    }

    @Test
    fun `requesterId를 전달하면 로고가 있는 기업은 URL을 발급한다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf(logoFileId = LOGO_FILE_ID))
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID))).willReturn(
            mapOf(LOGO_FILE_ID to LOGO_URL),
        )

        val summary = query.findActiveSummary(1L, REQUESTER_ID)

        assertThat(summary?.logoUrl).isEqualTo(LOGO_URL)
    }

    @Test
    fun `requesterId를 전달해도 로고가 없는 기업은 logoUrl이 null이고 File Port를 호출하지 않는다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf(logoFileId = null))

        val summary = query.findActiveSummary(1L, REQUESTER_ID)

        assertThat(summary?.logoUrl).isNull()
        verify(fileUrlPort, never()).presignedImageUrls(anyLong(), anyCollection())
    }

    @Test
    fun `없거나 삭제된 기업은 null을 반환한다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThat(query.findActiveSummary(999L, REQUESTER_ID)).isNull()
    }

    // --- findActiveSummaries (Batch) ---

    @Test
    fun `여러 기업을 한 번에 조회하면 로고 URL도 한 번의 배치 호출로 발급한다`() {
        val withLogo = companyOf(id = 1L, name = "로고있음", logoFileId = LOGO_FILE_ID)
        val withoutLogo = companyOf(id = 2L, name = "로고없음", logoFileId = null)
        given(
            companyRepository.findAllByIdInAndDeletedAtIsNull(setOf(1L, 2L)),
        ).willReturn(listOf(withLogo, withoutLogo))
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID))).willReturn(
            mapOf(LOGO_FILE_ID to LOGO_URL),
        )

        val result = query.findActiveSummaries(listOf(1L, 2L), REQUESTER_ID)

        assertThat(result[1L]?.logoUrl).isEqualTo(LOGO_URL)
        assertThat(result[2L]?.logoUrl).isNull()
        // 기업마다 단건 발급하면 대상 수만큼 반복된다(N+1). 정확히 한 번이어야 한다.
        verify(fileUrlPort, times(1)).presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID))
    }

    @Test
    fun `requesterId 없이 배치 조회하면 URL을 발급하지 않는다`() {
        given(companyRepository.findAllByIdInAndDeletedAtIsNull(setOf(1L)))
            .willReturn(listOf(companyOf(id = 1L, logoFileId = LOGO_FILE_ID)))

        val result = query.findActiveSummaries(listOf(1L))

        assertThat(result[1L]?.logoUrl).isNull()
        verify(fileUrlPort, never()).presignedImageUrls(anyLong(), anyCollection())
    }

    @Test
    fun `대상 ID가 비어 있으면 Repository를 조회하지 않고 빈 Map을 반환한다`() {
        val result = query.findActiveSummaries(emptyList(), REQUESTER_ID)

        assertThat(result).isEmpty()
        verify(companyRepository, never()).findAllByIdInAndDeletedAtIsNull(anySet())
    }

    // --- findActiveLogoFileId (색인 전용) ---

    @Test
    fun `색인용 조회는 URL이 아니라 File ID 자체를 반환한다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf(logoFileId = LOGO_FILE_ID))

        assertThat(query.findActiveLogoFileId(1L)).isEqualTo(LOGO_FILE_ID)
        verify(fileUrlPort, never()).presignedImageUrls(anyLong(), anyCollection())
    }

    @Test
    fun `로고가 없거나 기업이 없으면 색인용 조회는 null이다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf(logoFileId = null))
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThat(query.findActiveLogoFileId(1L)).isNull()
        assertThat(query.findActiveLogoFileId(999L)).isNull()
    }

    private fun companyOf(
        id: Long = 1L,
        name: String = "인력개발원",
        logoFileId: Long?,
    ) = Company(name = name, type = CompanyType.GENERAL, mouStatus = MouStatus.NONE).apply {
        this.id = id
        this.logoFileId = logoFileId
    }

    private companion object {
        private const val REQUESTER_ID = 7L
        private const val LOGO_FILE_ID = 43L
        private const val LOGO_URL = "https://storage.example/company-logo?signature=test"
    }
}
