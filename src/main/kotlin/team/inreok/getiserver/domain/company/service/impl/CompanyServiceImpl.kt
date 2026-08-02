package team.inreok.getiserver.domain.company.service.impl

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanySearchResponse
import team.inreok.getiserver.domain.company.dto.CompanySummaryResponse
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.exception.CompanyNameRequiredException
import team.inreok.getiserver.domain.company.exception.CompanyNotFoundException
import team.inreok.getiserver.domain.company.exception.DuplicateCompanyException
import team.inreok.getiserver.domain.company.exception.MouPeriodInvalidException
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.company.service.CompanyService
import team.inreok.getiserver.domain.company.service.escapeLikePattern
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CompanyServiceImpl(
    private val companyRepository: CompanyRepository,
) : CompanyService {
    @Transactional
    override fun create(request: CompanyCreateRequest): CompanyResponse {
        val name = request.name.trim()
        if (name.isEmpty()) throw CompanyNameRequiredException()
        validateMouPeriod(request.mouStartDate, request.mouEndDate)
        if (companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull(name, request.companyType)) {
            throw DuplicateCompanyException()
        }

        val company =
            Company(
                name = name,
                type = request.companyType,
                mouStatus = request.mouStatus,
            ).apply {
                source = request.sourceName
                websiteUrl = request.homepageUrl
                description = request.description
                industry = request.industry
                address = request.address
                mouStartedOn = request.mouStartDate
                mouEndedOn = request.mouEndDate
            }

        // @CreationTimestamp/@UpdateTimestamp는 Flush 시점에 채워진다. 응답의 createdAt/updatedAt이
        // null로 나가지 않도록 저장과 동시에 Flush한다.
        return try {
            CompanyResponse.from(companyRepository.saveAndFlush(company))
        } catch (ex: DataIntegrityViolationException) {
            throwDuplicateOrRethrow(ex)
        }
    }

    @Transactional(readOnly = true)
    override fun get(companyId: Long): CompanyResponse = CompanyResponse.from(findActive(companyId))

    @Transactional(readOnly = true)
    override fun search(
        query: String?,
        companyType: CompanyType?,
        mouStatus: MouStatus?,
        sourceName: String?,
        pageable: Pageable,
    ): CompanySearchResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "이름 조건 없음"으로 취급한다.
        val escapedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLikePattern)
        val page =
            companyRepository.search(
                escapedQuery,
                companyType,
                mouStatus,
                sourceName?.trim()?.takeIf { it.isNotEmpty() },
                pageable,
            )
        return CompanySearchResponse(
            content = page.content.map(CompanySummaryResponse::from),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional
    override fun update(
        companyId: Long,
        request: CompanyUpdateRequest,
    ): CompanyResponse {
        val company = findActive(companyId)

        // 전달하지 않았거나 null인 Field는 기존 값을 유지한다(CompanyUpdateRequest 참고).
        val newName = request.name?.trim()?.also { if (it.isEmpty()) throw CompanyNameRequiredException() }

        validateMouPeriod(
            request.mouStartDate ?: company.mouStartedOn,
            request.mouEndDate ?: company.mouEndedOn,
        )
        // 이름이나 유형이 바뀔 때만 바뀐 값 기준으로 중복을 다시 확인한다.
        if (newName != null || request.companyType != null) {
            val name = newName ?: company.name
            val type = request.companyType ?: company.type
            if (companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(name, type, companyId)) {
                throw DuplicateCompanyException()
            }
        }

        applyChanges(company, request, newName)
        // @UpdateTimestamp가 Flush 시점에 갱신되므로, 응답에 낡은 updatedAt이 담기지 않도록
        // 응답을 만들기 전에 Flush한다.
        try {
            companyRepository.flush()
        } catch (ex: DataIntegrityViolationException) {
            throwDuplicateOrRethrow(ex)
        }
        return CompanyResponse.from(company)
    }

    @Transactional
    override fun delete(companyId: Long) {
        // 연결된 공개 공고가 있을 때의 차단(COMPANY_HAS_ACTIVE_JOBS)은 domain.job 조회가 필요해
        // 이번 범위에서 제외했다(Issue #56, Modulith Module 경계). Job 도메인 연동 후 추가한다.
        findActive(companyId).deletedAt = LocalDateTime.now()
    }

    private fun findActive(companyId: Long): Company =
        companyRepository.findByIdAndDeletedAtIsNull(companyId) ?: throw CompanyNotFoundException(companyId)

    /**
     * 사전 중복 검사와 저장 사이의 경쟁 조건으로 Unique Index(`uk_companies_name_type_active`,
     * V6 Migration)에 걸린 경우에만 [DuplicateCompanyException]으로 변환한다. 다른 제약 위반까지
     * 중복으로 오인하면 원인을 감추게 되므로, 해당 Index가 아니면 원래 예외를 그대로 전달한다.
     */
    private fun throwDuplicateOrRethrow(ex: DataIntegrityViolationException): Nothing {
        val constraintName = (ex.cause as? ConstraintViolationException)?.constraintName
        val isNameTypeDuplicate =
            constraintName?.equals(NAME_TYPE_UNIQUE_INDEX, ignoreCase = true) == true ||
                ex.message?.contains(NAME_TYPE_UNIQUE_INDEX, ignoreCase = true) == true
        throw if (isNameTypeDuplicate) DuplicateCompanyException() else ex
    }

    // 전달된 Field만 반영한다. null Field는 여기서 아무 일도 하지 않으므로 기존 값이 유지된다.
    private fun applyChanges(
        company: Company,
        request: CompanyUpdateRequest,
        newName: String?,
    ) {
        company.apply {
            newName?.let { name = it }
            request.companyType?.let { type = it }
            request.mouStatus?.let { mouStatus = it }
            request.sourceName?.let { source = it }
            request.homepageUrl?.let { websiteUrl = it }
            request.description?.let { description = it }
            request.industry?.let { industry = it }
            request.address?.let { address = it }
            request.mouStartDate?.let { mouStartedOn = it }
            request.mouEndDate?.let { mouEndedOn = it }
        }
    }

    private fun validateMouPeriod(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw MouPeriodInvalidException()
        }
    }

    private companion object {
        // V6__add_companies_name_type_unique_index.sql에서 생성하는 Index 이름
        const val NAME_TYPE_UNIQUE_INDEX = "uk_companies_name_type_active"
    }
}
