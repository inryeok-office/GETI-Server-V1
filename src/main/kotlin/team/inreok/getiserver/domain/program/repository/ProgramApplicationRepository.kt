package team.inreok.getiserver.domain.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus

// 내 신청 목록 조회(GET /api/v1/me/program-applications)는 Phase 4 범위라 이번 Phase 1~3에서는
// 목록 조회용 Method(search 등)를 미리 추가하지 않았다(아직 필요하지 않은 조회 Method를 미리
// 만들지 않는다는 원칙, docs/ai/coding-conventions.md).
interface ProgramApplicationRepository : JpaRepository<ProgramApplication, Long> {
    fun findByProgramIdAndApplicantMemberIdAndStatus(
        programId: Long,
        applicantMemberId: Long,
        status: ProgramApplicationStatus,
    ): ProgramApplication?

    // 정원 판정에 쓰는 현재 활성 신청자 수다. ProgramRepository.findByIdForUpdate로 Program
    // Row를 먼저 잠근 뒤 호출해야 동시 요청에서도 일관된 값을 본다(요구사항 22절 동시성).
    fun countByProgramIdAndStatus(
        programId: Long,
        status: ProgramApplicationStatus,
    ): Long
}
