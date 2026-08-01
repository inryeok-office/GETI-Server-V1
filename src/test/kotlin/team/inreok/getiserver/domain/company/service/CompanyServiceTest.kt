package team.inreok.getiserver.domain.company.service

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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.exception.CompanyNameRequiredException
import team.inreok.getiserver.domain.company.exception.CompanyNotFoundException
import team.inreok.getiserver.domain.company.exception.DuplicateCompanyException
import team.inreok.getiserver.domain.company.exception.MouPeriodInvalidException
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.company.service.impl.CompanyServiceImpl
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CompanyServiceTest {
    @Mock
    private lateinit var companyRepository: CompanyRepository

    private val service: CompanyService by lazy { CompanyServiceImpl(companyRepository) }

    private fun companyOf(
        id: Long = 1L,
        name: String = "인력개발원",
        type: CompanyType = CompanyType.GENERAL,
        mouStatus: MouStatus = MouStatus.NONE,
    ) = Company(name = name, type = type, mouStatus = mouStatus).apply { this.id = id }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(TokenServiceImplTest의 anyLocalDateTime()과 동일한 방식).
    private fun anyCompany(): Company =
        any(Company::class.java) ?: Company(name = "", type = CompanyType.GENERAL)

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

        val result = service.create(request)

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

        assertThatThrownBy { service.create(request) }
            .isInstanceOf(CompanyNameRequiredException::class.java)

        verify(companyRepository, never()).saveAndFlush(anyCompany())
    }

    @Test
    fun `이름과 유형이 같은 기업이 이미 있으면 DuplicateCompanyException을 던진다`() {
        val request = CompanyCreateRequest(name = "인력개발원", companyType = CompanyType.GENERAL)
        givenDuplicateCheck("인력개발원", CompanyType.GENERAL).willReturn(true)

        assertThatThrownBy { service.create(request) }
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

        assertThatThrownBy { service.create(request) }
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

        val result = service.create(request)

        assertThat(result.name).isEqualTo("인력개발원")
    }

    @Test
    fun `기업을 단건 조회하면 기업 정보를 반환한다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        val result = service.get(1L)

        assertThat(result.companyId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("인력개발원")
    }

    @Test
    fun `존재하지 않거나 삭제된 기업을 조회하면 CompanyNotFoundException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThatThrownBy { service.get(999L) }
            .isInstanceOf(CompanyNotFoundException::class.java)
    }

    @Test
    fun `기업 목록을 조회하면 Page 정보와 함께 목록을 반환한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search("인력", null, null, null, pageable))
            .willReturn(PageImpl(listOf(companyOf()), pageable, 1))

        val result = service.search("인력", null, null, null, pageable)

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

        val result = service.search("없는기업", null, null, null, pageable)

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isZero()
    }

    @Test
    fun `검색어가 공백이면 이름 조건 없이 조회한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search(null, null, null, null, pageable))
            .willReturn(PageImpl(listOf(companyOf()), pageable, 1))

        val result = service.search("   ", null, null, null, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun `검색어에 LIKE Wildcard가 있으면 이스케이프해서 Repository에 전달한다`() {
        val pageable = PageRequest.of(0, 20)
        given(companyRepository.search("100\\%", null, null, null, pageable))
            .willReturn(PageImpl(emptyList(), pageable, 0))

        val result = service.search("100%", null, null, null, pageable)

        assertThat(result.content).isEmpty()
    }

    @Test
    fun `기업 유형과 MOU 상태 필터를 Repository에 그대로 전달한다`() {
        val pageable = PageRequest.of(0, 20)
        given(
            companyRepository.search(null, CompanyType.PUBLIC_ENTERPRISE, MouStatus.ACTIVE, "collector", pageable),
        ).willReturn(PageImpl(emptyList(), pageable, 0))

        val result =
            service.search(null, CompanyType.PUBLIC_ENTERPRISE, MouStatus.ACTIVE, "collector", pageable)

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

        val result = service.update(1L, CompanyUpdateRequest(description = "새 소개"))

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
            service.update(1L, CompanyUpdateRequest(mouStartDate = LocalDate.of(2027, 3, 1)))
        }.isInstanceOf(MouPeriodInvalidException::class.java)
    }

    @Test
    fun `수정 시 기업명을 공백으로 보내면 CompanyNameRequiredException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        assertThatThrownBy { service.update(1L, CompanyUpdateRequest(name = "   ")) }
            .isInstanceOf(CompanyNameRequiredException::class.java)
    }

    @Test
    fun `수정 시 이름과 유형이 같은 다른 기업이 있으면 DuplicateCompanyException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())
        givenDuplicateCheckExcludingSelf("다른기업", CompanyType.GENERAL, 1L).willReturn(true)

        assertThatThrownBy { service.update(1L, CompanyUpdateRequest(name = "다른기업")) }
            .isInstanceOf(DuplicateCompanyException::class.java)
    }

    @Test
    fun `이름과 유형을 바꾸지 않으면 중복 검사를 하지 않는다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(companyOf())

        service.update(1L, CompanyUpdateRequest(description = "새 소개"))

        verify(companyRepository, never())
            .existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot("인력개발원", CompanyType.GENERAL, 1L)
    }

    @Test
    fun `존재하지 않는 기업을 수정하면 CompanyNotFoundException을 던진다`() {
        given(companyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

        assertThatThrownBy { service.update(999L, CompanyUpdateRequest(description = "새 소개")) }
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
}
