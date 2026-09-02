package team.inreok.getiserver.domain.inquiry.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.inquiry.dto.InquiryAdminListResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryDetailResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryListResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateResponse
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType

interface InquiryService {
    /** 초기 status는 항상 RECEIVED로 서버가 고정한다(요구사항 4절). */
    fun create(
        request: InquiryCreateRequest,
        authorMemberId: Long,
    ): InquiryCreateResponse

    /** 항상 `requesterMemberId` 본인의 문의만 반환한다(요구사항 13절). */
    fun listMine(
        status: InquiryStatus?,
        requesterMemberId: Long,
        pageable: Pageable,
    ): InquiryListResponse

    /**
     * 소유권을 검증한다 -- `isDeveloper`가 아니면 본인이 작성한 문의만 조회할 수 있고, 아니면
     * [team.inreok.getiserver.domain.inquiry.exception.InquiryAccessDeniedException](403). 없는
     * 문의는 [team.inreok.getiserver.domain.inquiry.exception.InquiryNotFoundException](404).
     * `assignee`는 `isDeveloper`일 때만 채워진다(요구사항 17절).
     */
    fun getDetail(
        inquiryId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ): InquiryDetailResponse

    /**
     * 개발자 전용 전체 문의 목록이다(요구사항 26절~29절). `mineOnly=true`면 `requesterMemberId`를
     * 담당자 Filter로 함께 적용한다(`assigneeId`와는 단순 AND, 요구사항 29절).
     */
    fun listAdmin(
        inquiryType: InquiryType?,
        status: InquiryStatus?,
        query: String?,
        assigneeId: Long?,
        mineOnly: Boolean,
        requesterMemberId: Long,
        pageable: Pageable,
        answered: Boolean? = null,
    ): InquiryAdminListResponse

    /**
     * 담당 개발자를 지정하거나(`assigneeId != null`) 해제한다(`assigneeId == null`, 요구사항
     * 31절). 지정 대상은 role=DEVELOPER AND status=ACTIVE를 모두 만족해야 하고, 아니면
     * [team.inreok.getiserver.domain.inquiry.exception.InvalidInquiryAssigneeException](400).
     * 회원 자체가 없으면
     * [team.inreok.getiserver.domain.inquiry.exception.InquiryAssigneeMemberNotFoundException](404).
     * 담당자 지정은 문의 상태를 바꾸지 않는다(요구사항 34절, 사용자 확인 완료).
     */
    fun updateAssignee(
        inquiryId: Long,
        request: InquiryAssigneeUpdateRequest,
    ): InquiryAssigneeUpdateResponse

    /**
     * 문의 상태를 변경한다. 확정된 전이 Matrix를 벗어나거나(요구사항 35절/36절), 답변이 없는데
     * `status=ANSWERED`를 직접 지정하면(요구사항 37절) 둘 다
     * [team.inreok.getiserver.domain.inquiry.exception.InquiryStatusInvalidException](400).
     */
    fun changeStatus(
        inquiryId: Long,
        request: InquiryStatusUpdateRequest,
    ): InquiryStatusUpdateResponse

    /**
     * 문의에 답변을 등록한다(요구사항 §38). 답변 등록은 문의 상태를 항상
     * [team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus.ANSWERED]로 자동
     * 전이한다(사용자 확인 완료). CLOSED 상태의 문의에 답변을 등록하면
     * [team.inreok.getiserver.domain.inquiry.exception.InquiryAlreadyClosedException](409).
     * 문의가 없으면
     * [team.inreok.getiserver.domain.inquiry.exception.InquiryNotFoundException](404).
     *
     * 답변 등록 성공 시 `domain.notification`에 인앱 알림 생성을 요청하는 Event를 발행한다
     * (Commit 이후에만 실제로 처리됨).
     */
    fun createAnswer(
        inquiryId: Long,
        request: InquiryAnswerCreateRequest,
        authorMemberId: Long,
    ): InquiryAnswerCreateResponse
}
