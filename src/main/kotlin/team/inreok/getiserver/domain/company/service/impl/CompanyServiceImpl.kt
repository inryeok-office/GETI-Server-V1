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
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileUrlPort
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CompanyServiceImpl(
    private val companyRepository: CompanyRepository,
    private val fileLinkPort: FileLinkPort,
    private val fileUrlPort: FileUrlPort,
) : CompanyService {
    @Transactional
    override fun create(
        request: CompanyCreateRequest,
        requesterId: Long,
    ): CompanyResponse {
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
        val saved =
            try {
                companyRepository.saveAndFlush(company)
            } catch (ex: DataIntegrityViolationException) {
                throwDuplicateOrRethrow(ex)
            }

        // 로고 연결은 저장 이후다 -- 연결 대상(ownerId)으로 쓸 companyId가 저장 전에는 없다.
        // 연결이 거부되면 예외가 Transaction을 되돌려 기업도 만들어지지 않는다.
        linkLogo(saved, request.logoFileId, requesterId)
        return CompanyResponse.from(saved, logoUrl(saved, requesterId))
    }

    @Transactional(readOnly = true)
    override fun get(
        companyId: Long,
        requesterId: Long,
    ): CompanyResponse {
        val company = findActive(companyId)
        return CompanyResponse.from(company, logoUrl(company, requesterId))
    }

    @Transactional(readOnly = true)
    override fun search(
        requesterId: Long,
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
        // 목록의 로고를 한 번에 URL로 바꾼다. 항목마다 단건 발급하면 기업 수만큼 반복된다(N+1).
        val logoUrls = fileUrlPort.presignedImageUrls(requesterId, page.content.mapNotNull { it.logoFileId })
        return CompanySearchResponse(
            content =
                page.content.map { company ->
                    CompanySummaryResponse.from(company, company.logoFileId?.let { logoUrls[it] })
                },
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
        requesterId: Long,
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
        linkLogo(company, request.logoFileId, requesterId)
        // @UpdateTimestamp가 Flush 시점에 갱신되므로, 응답에 낡은 updatedAt이 담기지 않도록
        // 응답을 만들기 전에 Flush한다.
        try {
            companyRepository.flush()
        } catch (ex: DataIntegrityViolationException) {
            throwDuplicateOrRethrow(ex)
        }
        return CompanyResponse.from(company, logoUrl(company, requesterId))
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
     * 로고를 등록하거나 교체한다. 전달하지 않았거나(`null`) 현재와 같은 파일이면 아무 일도
     * 하지 않는다 -- 로고 제거는 [CompanyUpdateRequest]가 지원하지 않는 동작이다.
     *
     * 소유권·목적·상태 검증은 [FileLinkPort.validateAndLink]가 수행한다. 남이 올린 파일이면
     * `FILE_NOT_OWNED`, `COMPANY_LOGO` 용도로 올리지 않은 파일이면 `FILE_PURPOSE_MISMATCH`로
     * 거부되어 이 Transaction 전체가 되돌아간다.
     */
    private fun linkLogo(
        company: Company,
        newFileId: Long?,
        requesterId: Long,
    ) {
        if (newFileId == null || newFileId == company.logoFileId) return
        val companyId = requireNotNull(company.id) { "저장된 Company는 id를 가져야 합니다." }

        // 교체 시 기존 로고는 연결만 해제한다. Storage Binary 삭제는 Cleanup(Phase 5) 범위다.
        if (company.logoFileId != null) fileLinkPort.unlinkAllOf(FileOwnerType.COMPANY, companyId)
        fileLinkPort.validateAndLink(
            requesterId = requesterId,
            fileIds = listOf(newFileId),
            purpose = FilePurpose.COMPANY_LOGO,
            ownerId = companyId,
        )
        company.logoFileId = newFileId
    }

    private fun logoUrl(
        company: Company,
        requesterId: Long,
    ): String? {
        val fileId = company.logoFileId ?: return null
        return fileUrlPort.presignedImageUrls(requesterId, listOf(fileId))[fileId]
    }

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
