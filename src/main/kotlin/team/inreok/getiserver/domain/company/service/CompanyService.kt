package team.inreok.getiserver.domain.company.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanySearchResponse
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus

interface CompanyService {
    fun create(request: CompanyCreateRequest): CompanyResponse

    fun get(companyId: Long): CompanyResponse

    fun search(
        query: String?,
        companyType: CompanyType?,
        mouStatus: MouStatus?,
        sourceName: String?,
        pageable: Pageable,
    ): CompanySearchResponse

    fun update(
        companyId: Long,
        request: CompanyUpdateRequest,
    ): CompanyResponse

    /** 기업을 Soft Delete한다(`deleted_at` 기록). 이미 삭제된 기업은 찾을 수 없는 것으로 처리한다. */
    fun delete(companyId: Long)
}
