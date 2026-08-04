package team.inreok.getiserver.domain.company.external.impl

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.exception.CompanyNameRequiredException
import team.inreok.getiserver.domain.company.external.CompanyExternalImportCommand
import team.inreok.getiserver.domain.company.external.CompanyExternalImportResult
import team.inreok.getiserver.domain.company.external.CompanyExternalImportUseCase
import team.inreok.getiserver.domain.company.repository.CompanyRepository

@Service
class CompanyExternalImportUseCaseImpl(
    private val companyRepository: CompanyRepository,
) : CompanyExternalImportUseCase {
    @Transactional
    override fun findOrCreateExternal(command: CompanyExternalImportCommand): CompanyExternalImportResult {
        val name = normalize(command.companyName)
        if (name.isEmpty()) throw CompanyNameRequiredException()

        // 공식 데이터로 기업 유형을 확정할 수 없으므로 GENERAL로 고정한다(Issue #62 확정 정책).
        // 기존 기업 등록(CompanyServiceImpl.create)과 동일한 (name, type) 미삭제 Unique 기준을
        // 그대로 재사용해 Collector 전용 중복 정책을 별도로 만들지 않는다.
        companyRepository.findByNameIgnoreCaseAndTypeAndDeletedAtIsNull(name, CompanyType.GENERAL)?.let {
            return CompanyExternalImportResult(companyId = requireNotNull(it.id), created = false)
        }

        val company =
            Company(name = name, type = CompanyType.GENERAL, mouStatus = MouStatus.NONE).apply {
                source = command.sourceCode
            }

        return try {
            val saved = companyRepository.saveAndFlush(company)
            CompanyExternalImportResult(companyId = requireNotNull(saved.id), created = true)
        } catch (ex: DataIntegrityViolationException) {
            // 동시 수집으로 같은 기업명이 경쟁 조건에서 두 번 Insert되면 uk_companies_name_type_active
            // Unique Index가 막는다. 이 경우 다시 조회해 먼저 성공한 기업을 재사용한다.
            recoverFromDuplicate(name, ex)
        }
    }

    private fun recoverFromDuplicate(
        name: String,
        ex: DataIntegrityViolationException,
    ): CompanyExternalImportResult {
        val existing = companyRepository.findByNameIgnoreCaseAndTypeAndDeletedAtIsNull(name, CompanyType.GENERAL)
        val constraintName = (ex.cause as? ConstraintViolationException)?.constraintName
        val isNameTypeDuplicate =
            existing != null &&
                (
                    constraintName?.equals(NAME_TYPE_UNIQUE_INDEX, ignoreCase = true) == true ||
                        ex.message?.contains(NAME_TYPE_UNIQUE_INDEX, ignoreCase = true) == true
                )
        if (!isNameTypeDuplicate) throw ex
        return CompanyExternalImportResult(companyId = requireNotNull(existing.id), created = false)
    }

    // 앞뒤 공백 제거와 연속 공백 정리만 한다. "(주)"/"주식회사"/지점명/괄호 내용/영문 표기 차이는
    // 근거 없이 제거하지 않는다(Issue #62 확정 정책 — 과도한 기업명 정규화 금지).
    private fun normalize(raw: String): String = raw.trim().replace(WHITESPACE_REGEX, " ")

    private companion object {
        const val NAME_TYPE_UNIQUE_INDEX = "uk_companies_name_type_active"
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
