package team.inreok.getiserver.domain.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.program.entity.ProgramTargetGrade
import team.inreok.getiserver.domain.program.entity.ProgramTargetGradeId

// 게시 후에는 targetGrades를 변경할 수 없고(원본 요구사항 문서 7절) 이번 범위(Phase 1~3)는
// 게시 전 수정 API도 targetGrades를 다루지 않아 삭제·재저장 Method는 필요하지 않다.
interface ProgramTargetGradeRepository : JpaRepository<ProgramTargetGrade, ProgramTargetGradeId> {
    fun findAllByIdProgramId(programId: Long): List<ProgramTargetGrade>
}
