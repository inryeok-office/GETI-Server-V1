package team.inreok.getiserver.domain.company.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.audit.query.AuditLogWriter
import team.inreok.getiserver.domain.audit.query.CompanyAuditQueryPort
import team.inreok.getiserver.domain.audit.query.CompanyAuditSnapshot
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.exception.CompanyNameRequiredException
import team.inreok.getiserver.domain.company.exception.CompanyNotFoundException
import team.inreok.getiserver.domain.company.exception.DuplicateCompanyException
import team.inreok.getiserver.domain.company.exception.MouPeriodInvalidException
import team.inreok.getiserver.domain.company.query.CompanyAdminJobQueryPort
import team.inreok.getiserver.domain.company.query.CompanyAdminJobSnapshot
import team.inreok.getiserver.domain.company.query.CompanyApplicationCountQueryPort
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.company.service.impl.CompanyServiceImpl
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class CompanyServiceTest {
    @Mock
    private lateinit var companyRepository: CompanyRepository

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    @Mock
    private lateinit var companyAdminJobQueryPort: CompanyAdminJobQueryPort

    @Mock
    private lateinit var companyApplicationCountQueryPort: CompanyApplicationCountQueryPort

    @Mock
    private lateinit var companyAuditQueryPort: CompanyAuditQueryPort

    @Mock
    private lateinit var auditLogWriter: AuditLogWriter

    @Mock
    private lateinit var inquiryMemberSnapshotQueryPort: InquiryMemberSnapshotQueryPort

    private val service: CompanyService by lazy {
        CompanyServiceImpl(
            companyRepository,
            fileLinkPort,
            fileUrlPort,
            companyAdminJobQueryPort,
            companyApplicationCountQueryPort,
            companyAuditQueryPort,
            auditLogWriter,
            inquiryMemberSnapshotQueryPort,
        )
    }

    // 이 Class의 기존 Test는 로고를 다루지 않는다. 로고 연결·URL 발급은 아래 별도 Test들에서
    // 검증하며, 그 외 경로에서는 fileUrlPort가 빈 Map을 돌려주므로 logoUrl이 null이다.
    private fun companyOf(
        id: Long = 1L,
        name: String = "인력개발원",
        type: CompanyType = CompanyType.GENERAL,
        mouStatus: MouStatus = MouStatus.NONE,
    ) = Company(name = name, type = type, mouStatus = mouStatus).apply { this.id = id }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(TokenServiceImplTest의 anyLocalDateTime()과 동일한 방식).
    private fun anyCompany(): Company = any(Company::class.java) ?: Company(name = "", type = CompanyType.GENERAL)

    // Repository의 중복 확인 메서드 이름이 길어 Stub 구문을 짧게 유지하기 위한 Helper다.
    private fun givenDuplicateCheck(
        name: String,
        type: CompanyType,
    ) = given(companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull(name, type))

    private fun givenDuplicateCheckExcludingSelf(
        name: String,
        type: CompanyType,
        id: Long,
    ) = given(companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(name, type, id))

    @Test
    fun `기업을 등록하면 저장된 기업 정보를 반환한다`() {
        val request =
            CompanyCreateRequest(
                name = "인력개발원",
                companyType = CompanyType.PUBLIC_INSTITUTION,
                mouStatus = MouStatus.ACTIVE,
                sourceName = "manual",
                homepageUrl = "https://example.com",
                description = "교육 기관",
                industry = "소프트웨어 개발",
                address = "대구광역시 남구 대명동",
                mouStartDate = LocalDate.of(2026, 3, 1),
                mouEndDate = LocalDate.of(2027, 2, 28),
            )
        givenDuplicateCheck("인력개발원", CompanyType.PUBLIC_INSTITUTION)
            .willReturn(false)
        given(companyRepository.saveAndFlush(anyCompany())).willAnswer { invocation ->
            (invocation.arguments[0] as Company).apply { id = 10L }
        }

        val result = service.create(request, REQUESTER_ID)

        assertThat(result.companyId).isEqualTo(10L)
        assertThat(result.name).isEqualTo("인력개발원")
        assertThat(result.companyType).isEqualTo(CompanyType.PUBLIC_INSTITUTION)
        assertThat(result.mouStatus).isEqualTo(MouStatus.ACTIVE)
        assertThat(result.sourceName).isEqualTo("manual")
        assertThat(result.homepageUrl).isEqualTo("https://example.com")
        assertThat(result.industry).isEqualTo("소프트웨어 개발")
        assertThat(result.address).isEqualTo("대구광역시 남구 대명동")
        assertThat(result.mouStartDate).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(result.mouEndDate).isEqualTo(LocalDate.of(2027, 2, 28))
        // File 도메인 연동 전이라 logoUrl은 항상 null이다.
        assertThat(result.logoUrl).isNull()
    }

    @Test
    fun `기업명이 공백만 있으면 CompanyNameRequiredException을 던진다`() {
        val request = CompanyCreateRequest(name = "   ", companyType = CompanyType.GENERAL)

        assertThatThrownBy { service.create(request, REQUESTER_ID) }
            .isInstanceOf(CompanyNameRequiredException::class.java)

        verify(companyRepository, never()).saveAndFlush(anyCompany())
    }

    @Test
    fun `이름과 유형이 같은 기업이 이미 있으면 DuplicateCompanyException을 던진다`() {
        val request = CompanyCreateRequest(name = "인력개발원", companyType = CompanyType.GENERAL)
        givenDuplicateCheck("인력개발원", CompanyType.GENERAL).willReturn(true)

        assertThatThrownBy { service.create(request, REQUESTER_ID) }
            .isInstanceOf(DuplicateCompanyException::class.java)

        verify(companyRepository, never()).saveAndFlush(anyCompany())
    }

    @Test
    fun `MOU 시작일이 종료일보다 늦으면 MouPeriodInvalidException을 던진다`() {
        val request =
            CompanyCreateRequest(
                name = "인력개발원",
                companyType = CompanyType.GENERAL,
                mouStartDate = LocalDate.of(2027, 3, 1),
                mouEndDate = LocalDate.of(2026, 2, 28),
            )

        assertThatThrownBy { service.create(request, REQUESTER_ID) }
            .isInstanceOf(MouPeriodInvalidException::class.java)

        verify(companyRepository, never()).saveAndFlush(anyCompany())
    }

    @Test
    fun `등록 시 기업명의 앞뒤 공백을 제거한다`() {
        val request = CompanyCreateRequest(name = "  인력개발원  ", companyType = CompanyType.GENERAL)
        givenDuplicateCheck("인력개발원", CompanyType.GENERAL).willReturn(false)
        given(companyRepository.saveAndFlush(anyCompany())).willAnswer { invocation ->
            (invocation.arguments[0] as Company).apply { id = 11L }
        }

        val result = service.create(request, REQUESTER_ID)

        assertThat(result.name).isEqualTo("인력개발원")
    }

    @Test
    fun `기업을 단건 조회하면 기업 정보를 반환한다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        val result = service.get(1L, REQUESTER_ID)

        assertThat(result.companyId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("인력개발원")
    }

    @Test
    fun `존재하지 않거나 삭제된 기업을 조회하면 CompanyNotFoundException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThatThrownBy { service.get(999L, REQUESTER_ID) }
            .isInstanceOf(CompanyNotFoundException::class.java)
    }

    @Test
    fun `기업 목록을 조회하면 Page 정보와 함께 목록을 반환한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search("인력", null, null, null, pageable))
            .willReturn(PageImpl(listOf(companyOf()), pageable, 1))

        val result = service.search(REQUESTER_ID, "인력", null, null, null, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].companyId).isEqualTo(1L)
        assertThat(result.content[0].name).isEqualTo("인력개발원")
        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.first).isTrue()
        assertThat(result.last).isTrue()
    }

    @Test
    fun `검색 결과가 없으면 빈 목록을 반환한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search("없는기업", null, null, null, pageable))
            .willReturn(PageImpl(emptyList(), pageable, 0))

        val result = service.search(REQUESTER_ID, "없는기업", null, null, null, pageable)

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isZero()
    }

    @Test
    fun `검색어가 공백이면 이름 조건 없이 조회한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search(null, null, null, null, pageable))
            .willReturn(PageImpl(listOf(companyOf()), pageable, 1))

        val result = service.search(REQUESTER_ID, "   ", null, null, null, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun `검색어에 LIKE Wildcard가 있으면 이스케이프해서 Repository에 전달한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search("100\\%", null, null, null, pageable))
            .willReturn(PageImpl(emptyList(), pageable, 0))

        val result = service.search(REQUESTER_ID, "100%", null, null, null, pageable)

        assertThat(result.content).isEmpty()
    }

    @Test
    fun `기업 유형과 MOU 상태 필터를 Repository에 그대로 전달한다`() {
        val pageable = PageRequest.of(0, 20)
        given(
            companyRepository.search(null, CompanyType.PUBLIC_ENTERPRISE, MouStatus.ACTIVE, "collector", pageable),
        ).willReturn(PageImpl(emptyList(), pageable, 0))

        val result =
            service.search(REQUESTER_ID, null, CompanyType.PUBLIC_ENTERPRISE, MouStatus.ACTIVE, "collector", pageable)

        assertThat(result.content).isEmpty()
    }

    @Test
    fun `전달한 Field만 수정하고 나머지는 기존 값을 유지한다`() {
        val company =
            companyOf().apply {
                description = "기존 소개"
                websiteUrl = "https://old.example.com"
            }
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)

        val result = service.update(1L, CompanyUpdateRequest(description = "새 소개"), REQUESTER_ID)

        assertThat(result.description).isEqualTo("새 소개")
        assertThat(result.homepageUrl).isEqualTo("https://old.example.com")
        assertThat(result.name).isEqualTo("인력개발원")
    }

    @Test
    fun `MOU 상태와 기간을 수정할 수 있다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        val result =
            service.update(
                1L,
                CompanyUpdateRequest(
                    mouStatus = MouStatus.ACTIVE,
                    mouStartDate = LocalDate.of(2026, 3, 1),
                    mouEndDate = LocalDate.of(2027, 2, 28),
                ),
                REQUESTER_ID,
            )

        assertThat(result.mouStatus).isEqualTo(MouStatus.ACTIVE)
        assertThat(result.mouStartDate).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(result.mouEndDate).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun `수정 결과 MOU 시작일이 기존 종료일보다 늦어지면 MouPeriodInvalidException을 던진다`() {
        val company = companyOf().apply { mouEndedOn = LocalDate.of(2026, 2, 28) }
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)

        assertThatThrownBy {
            service.update(1L, CompanyUpdateRequest(mouStartDate = LocalDate.of(2027, 3, 1)), REQUESTER_ID)
        }.isInstanceOf(MouPeriodInvalidException::class.java)
    }

    @Test
    fun `수정 시 기업명을 공백으로 보내면 CompanyNameRequiredException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        assertThatThrownBy { service.update(1L, CompanyUpdateRequest(name = "   "), REQUESTER_ID) }
            .isInstanceOf(CompanyNameRequiredException::class.java)
    }

    @Test
    fun `수정 시 이름과 유형이 같은 다른 기업이 있으면 DuplicateCompanyException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())
        givenDuplicateCheckExcludingSelf("다른기업", CompanyType.GENERAL, 1L).willReturn(true)

        assertThatThrownBy { service.update(1L, CompanyUpdateRequest(name = "다른기업"), REQUESTER_ID) }
            .isInstanceOf(DuplicateCompanyException::class.java)
    }

    @Test
    fun `이름과 유형을 바꾸지 않으면 중복 검사를 하지 않는다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        service.update(1L, CompanyUpdateRequest(description = "새 소개"), REQUESTER_ID)

        verify(companyRepository, never())
            .existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot("인력개발원", CompanyType.GENERAL, 1L)
    }

    @Test
    fun `존재하지 않는 기업을 수정하면 CompanyNotFoundException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThatThrownBy { service.update(999L, CompanyUpdateRequest(description = "새 소개"), REQUESTER_ID) }
            .isInstanceOf(CompanyNotFoundException::class.java)
    }

    @Test
    fun `기업을 삭제하면 deletedAt이 기록된다`() {
        val company = companyOf()
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)

        service.delete(1L)

        assertThat(company.deletedAt).isNotNull()
    }

    @Test
    fun `이미 삭제되었거나 없는 기업을 삭제하면 CompanyNotFoundException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThatThrownBy { service.delete(999L) }
            .isInstanceOf(CompanyNotFoundException::class.java)
    }

    @Test
    fun `로고 파일 ID를 보내면 기업을 저장한 뒤 그 기업에 연결한다`() {
        val request =
            CompanyCreateRequest(
                name = "인력개발원",
                companyType = CompanyType.GENERAL,
                logoFileId = LOGO_FILE_ID,
            )
        givenDuplicateCheck("인력개발원", CompanyType.GENERAL).willReturn(false)
        given(companyRepository.saveAndFlush(anyCompany())).willAnswer { invocation ->
            (invocation.arguments[0] as Company).apply { id = 10L }
        }
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID)))
            .willReturn(mapOf(LOGO_FILE_ID to LOGO_URL))

        val result = service.create(request, REQUESTER_ID)

        // 연결 대상 ID(ownerId)는 저장 후에야 생긴다. 저장 전 연결은 불가능하다.
        verify(fileLinkPort).validateAndLink(REQUESTER_ID, listOf(LOGO_FILE_ID), FilePurpose.COMPANY_LOGO, 10L)
        assertThat(result.logoUrl).isEqualTo(LOGO_URL)
    }

    @Test
    fun `로고를 교체하면 기존 연결을 먼저 해제한다`() {
        val company = companyOf().apply { logoFileId = OLD_LOGO_FILE_ID }
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)

        service.update(1L, CompanyUpdateRequest(logoFileId = LOGO_FILE_ID), REQUESTER_ID)

        // 순서가 중요하다. 해제보다 연결이 먼저면 새 파일이 이미 연결된 상태에서 방금 붙인
        // 연결까지 함께 풀려 로고가 사라진다(unlinkAllOf는 해당 기업의 파일을 모두 푼다).
        val ordered = inOrder(fileLinkPort)
        ordered.verify(fileLinkPort).unlinkAllOf(FileOwnerType.COMPANY, 1L)
        ordered.verify(fileLinkPort).validateAndLink(
            REQUESTER_ID,
            listOf(LOGO_FILE_ID),
            FilePurpose.COMPANY_LOGO,
            1L,
        )
        assertThat(company.logoFileId).isEqualTo(LOGO_FILE_ID)
    }

    @Test
    fun `로고를 보내지 않으면 기존 로고를 그대로 두고 연결을 건드리지 않는다`() {
        val company = companyOf().apply { logoFileId = OLD_LOGO_FILE_ID }
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)

        service.update(1L, CompanyUpdateRequest(description = "새 소개"), REQUESTER_ID)

        verifyNoInteractions(fileLinkPort)
        assertThat(company.logoFileId).isEqualTo(OLD_LOGO_FILE_ID)
    }

    @Test
    fun `목록의 로고 URL은 한 번의 배치 호출로 변환한다`() {
        val pageable = PageRequest.of(0, 20)
        val withLogo = companyOf(id = 1L, name = "로고있음").apply { logoFileId = LOGO_FILE_ID }
        val withoutLogo = companyOf(id = 2L, name = "로고없음")
        given(companyRepository.search(null, null, null, null, pageable))
            .willReturn(PageImpl(listOf(withLogo, withoutLogo), pageable, 2))
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID)))
            .willReturn(mapOf(LOGO_FILE_ID to LOGO_URL))

        val result = service.search(REQUESTER_ID, null, null, null, null, pageable)

        assertThat(result.content[0].logoUrl).isEqualTo(LOGO_URL)
        assertThat(result.content[1].logoUrl).isNull()
        // 기업마다 단건 발급하면 목록 크기만큼 반복된다(N+1). 정확히 한 번이어야 한다.
        verify(fileUrlPort, times(1)).presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID))
    }

    @Test
    fun `권한이 없어 URL이 발급되지 않으면 logoUrl은 null이다`() {
        // FileUrlPort는 접근할 수 없는 파일을 결과 Map에서 빼고 예외를 던지지 않는다.
        val company = companyOf().apply { logoFileId = LOGO_FILE_ID }
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(LOGO_FILE_ID))).willReturn(emptyMap())

        assertThat(service.get(1L, REQUESTER_ID).logoUrl).isNull()
    }

    @Test
    fun `관리자 기업 상세는 연결 공고와 집계를 Port에서 조립한다`() {
        val company =
            companyOf().apply {
                representativeEmail = "contact@example.com"
                representativePhone = "02-1234-5678"
                memo = "관리 메모"
                updatedAt = LocalDateTime.of(2026, 3, 2, 9, 0)
            }
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(company)
        given(companyAdminJobQueryPort.findByCompanyId(1L)).willReturn(
            listOf(
                CompanyAdminJobSnapshot(10L, "초안", "GENERAL", "DRAFT", null),
                CompanyAdminJobSnapshot(11L, "모집 중", "MOU", "PUBLISHED", LocalDateTime.now().plusDays(1)),
                CompanyAdminJobSnapshot(12L, "마감", "SCHOOL", "CLOSED", LocalDateTime.now().minusDays(1)),
            ),
        )
        given(companyApplicationCountQueryPort.countByJobIds(setOf(10L, 11L, 12L)))
            .willReturn(mapOf(10L to 0L, 11L to 4L, 12L to 3L))
        given(companyAuditQueryPort.findRecentChanges(1L, 5)).willReturn(
            listOf(CompanyAuditSnapshot(100L, "COMPANY_UPDATED", REQUESTER_ID, company.updatedAt)),
        )
        given(inquiryMemberSnapshotQueryPort.findAllByIds(setOf(REQUESTER_ID))).willReturn(emptyMap())

        val result = service.getAdminDetail(1L, REQUESTER_ID)

        assertThat(result.representativeEmail).isEqualTo("contact@example.com")
        assertThat(result.memo).isEqualTo("관리 메모")
        assertThat(result.lastEditedAt).isEqualTo(company.updatedAt)
        assertThat(result.connectedJobs).hasSize(3)
        assertThat(result.stats.totalConnectedJobs).isEqualTo(3L)
        assertThat(result.stats.activeJobCount).isEqualTo(1L)
        assertThat(result.stats.totalApplicationCount).isEqualTo(7L)
        assertThat(result.recentChanges).hasSize(1)
    }

    private companion object {
        private const val REQUESTER_ID = 7L
        private const val LOGO_FILE_ID = 43L
        private const val OLD_LOGO_FILE_ID = 42L
        private const val LOGO_URL = "https://storage.example/company-logo?signature=test"
    }
}
