package team.inreok.getiserver.domain.portfolio.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestCreateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestListResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestStatusUpdateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestUpdateRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus

interface PortfolioRequestService {
    /** 수합 요청을 DRAFT 상태로 등록한다. 대상 학생이 없거나 실제 STUDENT가 아니면 거부한다. */
    fun create(
        request: PortfolioRequestCreateRequest,
        createdByMemberId: Long,
    ): PortfolioRequestResponse

    /** 전달된 Field만 수정한다. CLOSED/DELETED는 수정할 수 없고, 대상 교체는 DRAFT에서만 허용한다. */
    fun update(
        requestId: Long,
        request: PortfolioRequestUpdateRequest,
    ): PortfolioRequestResponse

    /** 수합 요청 상태를 변경한다. 공개(PUBLISHED)는 대상 최소 1명이 필요하고, 삭제는 Soft Delete다. */
    fun changeStatus(
        requestId: Long,
        request: PortfolioRequestStatusUpdateRequest,
    ): PortfolioRequestResponse

    /**
     * 수합 요청 상세를 조회한다. [isAdmin]이 false(학생)면 자신이 대상인 공개 이후 요청만 볼 수
     * 있다 -- 대상이 아니면 403(NOT_REQUEST_TARGET), 아직 공개 전(DRAFT)이거나 없으면 404.
     */
    fun getDetail(
        requestId: Long,
        requesterId: Long,
        isAdmin: Boolean,
    ): PortfolioRequestResponse

    /**
     * 수합 요청 목록을 조회한다. [isAdmin]이면 관리 가능한 전체 요청을, 아니면 요청자가 대상인
     * 공개 이후 요청만 돌려준다. [status]가 있으면 그 상태만 좁힌다.
     */
    fun getList(
        status: PortfolioRequestStatus?,
        requesterId: Long,
        isAdmin: Boolean,
        pageable: Pageable,
    ): PortfolioRequestListResponse
}
