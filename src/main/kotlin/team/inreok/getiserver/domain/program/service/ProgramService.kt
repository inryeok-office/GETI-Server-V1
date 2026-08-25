package team.inreok.getiserver.domain.program.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionResponse
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateResponse
import team.inreok.getiserver.domain.program.dto.ProgramDetailResponse
import team.inreok.getiserver.domain.program.dto.ProgramListResponse
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateResponse
import team.inreok.getiserver.domain.program.dto.ProgramUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramUpdateResponse
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType

interface ProgramService {
    fun create(
        request: ProgramCreateRequest,
        createdByMemberId: Long,
    ): ProgramCreateResponse

    /** 등록자·담당 교사가 아니면 [team.inreok.getiserver.domain.program.exception.ProgramManageForbiddenException]
     * (`isDeveloper=true`면 이 검증만 우회한다). */
    fun update(
        programId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: ProgramUpdateRequest,
    ): ProgramUpdateResponse

    fun changeStatus(
        programId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: ProgramStatusUpdateRequest,
    ): ProgramStatusUpdateResponse

    /** `requesterMemberId` 기준으로 `applied`를 계산한다(학생이 아니면 항상 false). */
    fun list(
        programType: ProgramType?,
        status: ProgramStatus?,
        openOnly: Boolean,
        requesterMemberId: Long,
        pageable: Pageable,
    ): ProgramListResponse

    /** 조회수를 1 증가시킨다. 삭제된 프로그램은 `programDeletedAt`을 포함해 반환한다. 응답의
     * `files` 목록은 DRAFT 상태에서 등록자·담당 교사·개발자에게만 실제 목록을 반환한다(Issue
     * #127) — DEVELOPER 여부는 Controller의 JWT Role이 아니라 Service가
     * `MemberRoleQueryPort`로 직접 조회해 판정한다(`ProgramFileAccessChecker`와 같은 출처를
     * 쓰기 위함, `ProgramServiceImpl.programFilesFor` 참고). */
    fun getDetail(
        programId: Long,
        requesterMemberId: Long,
    ): ProgramDetailResponse

    fun executeApplicationAction(
        programId: Long,
        studentMemberId: Long,
        request: ProgramApplicationActionRequest,
    ): ProgramApplicationActionResponse
}
