package team.inreok.getiserver.domain.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus

// 내 신청 목록 조회(GET /api/v1/me/program-applications)는 Phase 4 범위라 이번 Phase 1~3에서는
// 목록 조회용 Method(search 등)를 미리 추가하지 않았다(아직 필요하지 않은 조회 Method를 미리
// 만들지 않는다는 원칙, docs/ai/coding-conventions.md).
interface ProgramApplicationRepository : JpaRepository<ProgramApplication, Long> {
    fun findFirstByProgramIdAndApplicantMemberIdOrderByAppliedAtDescIdDesc(
        programId: Long,
        applicantMemberId: Long,
    ): ProgramApplication?

    fun findByProgramIdAndApplicantMemberIdAndStatus(
        programId: Long,
        applicantMemberId: Long,
        status: ProgramApplicationStatus,
    ): ProgramApplication?

    // 정원 판정(신청·취소·수정)에 쓰는 단건 활성 신청자 수다. ProgramRepository.findByIdForUpdate로
    // Program Row를 먼저 잠근 뒤 호출해야 동시 요청에서도 일관된 값을 본다(요구사항 22절 동시성).
    // 목록 조회(list())처럼 여러 Program을 한 번에 다뤄야 하면 이 Method를 항목마다 호출하지 않고
    // countActiveApplicantsByProgramIds를 사용한다(PR #81 리뷰 지적, N+1 방지).
    fun countByProgramIdAndStatus(
        programId: Long,
        status: ProgramApplicationStatus,
    ): Long

    // list()가 한 Page의 Program마다 countByProgramIdAndStatus를 개별 호출하면 N+1 쿼리가
    // 발생한다(PR #81 리뷰 지적). programId 목록을 한 번에 넘겨 Program별 활성 신청자 수를
    // 단일 Query로 가져온다. 신청이 하나도 없는 Program은 결과에 포함되지 않으므로 호출 측이
    // 없는 Key를 0으로 취급해야 한다.
    @Query(
        """
        SELECT pa.programId AS programId, COUNT(pa) AS activeApplicantCount
        FROM ProgramApplication pa
        WHERE pa.programId IN :programIds AND pa.status = :status
        GROUP BY pa.programId
        """,
    )
    fun countActiveApplicantsByProgramIds(
        @Param("programIds") programIds: Collection<Long>,
        @Param("status") status: ProgramApplicationStatus,
    ): List<ProgramApplicantCount>

    // list()에서 요청한 학생의 Program별 신청 여부(applied)를 항목마다 개별 조회하지 않도록
    // 활성 신청이 있는 programId만 한 번에 가져온다(PR #81 리뷰 지적, N+1 방지). 학생이 아닌
    // 요청은 Service가 이 Method 자체를 호출하지 않는다.
    @Query(
        """
        SELECT pa.programId FROM ProgramApplication pa
        WHERE pa.programId IN :programIds
          AND pa.applicantMemberId = :applicantMemberId
          AND pa.status = :status
        """,
    )
    fun findProgramIdsWithActiveApplication(
        @Param("programIds") programIds: Collection<Long>,
        @Param("applicantMemberId") applicantMemberId: Long,
        @Param("status") status: ProgramApplicationStatus,
    ): List<Long>

    // 프로그램 삭제 시 알림을 보낼 신청자를 찾는다(Issue #118, ProgramApplicantQueryPort). Entity를
    // 통째로 읽지 않고 회원 id만 Projection한다 -- 수신자 목록 외에는 쓰지 않기 때문이다.
    @Query(
        """
        SELECT pa.applicantMemberId FROM ProgramApplication pa
        WHERE pa.programId = :programId AND pa.status = :status
        """,
    )
    fun findApplicantMemberIdsByProgramIdAndStatus(
        @Param("programId") programId: Long,
        @Param("status") status: ProgramApplicationStatus,
    ): List<Long>
}

// countActiveApplicantsByProgramIds 전용 Interface Projection이다. Kotlin Property는
// Getter(getProgramId/getActiveApplicantCount)로 컴파일되어 JPQL Alias와 이름으로 매칭된다.
interface ProgramApplicantCount {
    val programId: Long
    val activeApplicantCount: Long
}
