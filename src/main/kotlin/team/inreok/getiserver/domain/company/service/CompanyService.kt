package team.inreok.getiserver.domain.company.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanySearchResponse
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus

interface CompanyService {
    /**
     * [requesterId]는 로고 파일의 업로더 확인(`FileLinkPort.validateAndLink`)과 응답 URL 발급에
     * 쓴다. 본인이 업로드하지 않은 파일은 로고로 연결할 수 없다.
     */
    fun create(
        request: CompanyCreateRequest,
        requesterId: Long,
    ): CompanyResponse

    fun get(
        companyId: Long,
        requesterId: Long,
    ): CompanyResponse

    fun search(
        requesterId: Long,
        query: String?,
        companyType: CompanyType?,
        mouStatus: MouStatus?,
        sourceName: String?,
        pageable: Pageable,
    ): CompanySearchResponse

    fun update(
        companyId: Long,
        request: CompanyUpdateRequest,
        requesterId: Long,
    ): CompanyResponse

    /** 기업을 Soft Delete한다(`deleted_at` 기록). 이미 삭제된 기업은 찾을 수 없는 것으로 처리한다. */
    fun delete(companyId: Long)
}
