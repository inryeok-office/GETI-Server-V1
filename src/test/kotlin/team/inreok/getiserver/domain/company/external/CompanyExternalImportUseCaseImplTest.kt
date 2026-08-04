package team.inreok.getiserver.domain.company.external

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.exception.CompanyNameRequiredException
import team.inreok.getiserver.domain.company.external.impl.CompanyExternalImportUseCaseImpl
import team.inreok.getiserver.domain.company.repository.CompanyRepository

@ExtendWith(MockitoExtension::class)
class CompanyExternalImportUseCaseImplTest {
    @Mock
    private lateinit var companyRepository: CompanyRepository

    private val useCase: CompanyExternalImportUseCase by lazy { CompanyExternalImportUseCaseImpl(companyRepository) }

    private fun companyOf(
        id: Long,
        name: String,
    ) = Company(name = name, type = CompanyType.GENERAL).apply { this.id = id }

    @Test
    fun `기업명이 비어 있으면 CompanyNameRequiredException이 발생한다`() {
        assertThatThrownBy { useCase.findOrCreateExternal(CompanyExternalImportCommand("   ", "MMA")) }
            .isInstanceOf(CompanyNameRequiredException::class.java)
    }

    @Test
    fun `동일 이름·GENERAL 유형의 미삭제 기업이 있으면 재사용하고 새로 만들지 않는다`() {
        given(companyRepository.findByNameIgnoreCaseAndTypeAndDeletedAtIsNull("인력개발원", CompanyType.GENERAL))
            .willReturn(companyOf(1L, "인력개발원"))

        val result = useCase.findOrCreateExternal(CompanyExternalImportCommand("인력개발원", "MMA"))

        assertThat(result.companyId).isEqualTo(1L)
        assertThat(result.created).isFalse()
    }

    @Test
    fun `없으면 GENERAL·NONE으로 새 기업을 만든다`() {
        given(companyRepository.findByNameIgnoreCaseAndTypeAndDeletedAtIsNull("새기업", CompanyType.GENERAL))
            .willReturn(null)
        given(companyRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Company::class.java))).willAnswer {
            (it.arguments[0] as Company).apply { id = 5L }
        }

        val result = useCase.findOrCreateExternal(CompanyExternalImportCommand("  새기업   ", "JOB_ALIO"))

        assertThat(result.companyId).isEqualTo(5L)
        assertThat(result.created).isTrue()
    }

    @Test
    fun `동시 생성 경쟁으로 Unique 제약을 위반하면 기존 기업을 재조회해 반환한다`() {
        given(companyRepository.findByNameIgnoreCaseAndTypeAndDeletedAtIsNull("경쟁기업", CompanyType.GENERAL))
            .willReturn(null, companyOf(9L, "경쟁기업"))
        val constraintViolation =
            ConstraintViolationException("dup", java.sql.SQLException(), "uk_companies_name_type_active")
        given(companyRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Company::class.java)))
            .willThrow(DataIntegrityViolationException("dup", constraintViolation))

        val result = useCase.findOrCreateExternal(CompanyExternalImportCommand("경쟁기업", "MMA"))

        assertThat(result.companyId).isEqualTo(9L)
        assertThat(result.created).isFalse()
    }
}
