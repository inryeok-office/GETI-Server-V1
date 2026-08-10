package team.inreok.getiserver.domain.program.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 Program 관리 권한(등록자 또는 담당 교사)을 판정할 때 쓰는 공개 계약이다
 * (Notification 요구사항 §37, Issue #97). Discord 상태 조회·수동 재시도 Admin API가 이 Port로
 * 소유권을 확인한 뒤 `ProgramServiceImpl.requireManager`와 같은 규칙
 * (`requesterId == createdByMemberId || requesterId == managerMemberId`, 개발자는 우회)을
 * 스스로 적용한다.
 *
 * [ProgramDiscordPayloadQueryPort]와 합치지 않는다 -- 그 Port는 "Bot에 보낼 Program 내용"만
 * 담기로 문서화돼 있고(KDoc 참고), 접근 제어용 소유자 정보는 성격이 다른 별개 관심사다.
 */
@NamedInterface
interface ProgramManagerQueryPort {
    /** 존재하지 않으면 null을 반환한다. */
    fun findById(programId: Long): ProgramManagerSnapshot?
}

@NamedInterface
data class ProgramManagerSnapshot(
    val programId: Long,
    val createdByMemberId: Long,
    /** 등록자와 별도로 지정된 담당 교사. 미지정(DRAFT 등)이면 null이다. */
    val managerMemberId: Long?,
)
