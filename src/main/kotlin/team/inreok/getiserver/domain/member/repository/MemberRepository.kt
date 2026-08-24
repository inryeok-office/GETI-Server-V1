package team.inreok.getiserver.domain.member.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?

    fun findByOauthProviderAndOauthSubject(
        oauthProvider: OAuthProvider,
        oauthSubject: String,
    ): Member?

    /**
     * 교직원 가입 승인·거절 처리를 위해 회원 Row에 Pessimistic Write Lock을 건다(Issue #99 동시성).
     * 같은 PENDING 회원을 두 개발자가 동시에 승인/거절할 때, 이 Method로 조회한 뒤 상태를 확인·전이해야
     * 두 요청이 순차적으로 실행되어 최종적으로 하나의 상태 전이만 성공한다
     * (ProgramRepository.findByIdForUpdate와 동일한 관례). Member에 @Version이 없어 Optimistic Lock
     * 대신 Pessimistic Lock을 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Member?

    // :name은 Service 계층에서 LIKE Wildcard(%, _)와 Escape 문자(\)를 미리 이스케이프해 전달한다
    // (검색어에 %/_가 포함되어도 문자 그대로 매칭하기 위함, ESCAPE '\' 참고). :status/:role은
    // "학생 이름 검색" API 목적에 맞게 Service 계층에서 항상 ACTIVE/STUDENT로 고정해 전달한다
    // (교사/개발자, 승인 대기·탈퇴 등 상태의 회원은 검색 결과에서 제외, 코드 리뷰 Major 반영).
    @Query(
        """
        SELECT m FROM Member m
        WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')) ESCAPE '\'
          AND m.status = :status
          AND EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role)
          AND (:academicStatus IS NULL OR m.academicStatus = :academicStatus)
          AND (:cohort IS NULL OR m.cohort = :cohort)
          AND (:department IS NULL OR m.department = :department)
          AND (
            :majorId IS NULL
            OR EXISTS (SELECT 1 FROM MemberMajor mm WHERE mm.id.memberId = m.id AND mm.id.majorId = :majorId)
          )
          AND (
            :techStackId IS NULL
            OR EXISTS (
              SELECT 1 FROM MemberTechStack mts WHERE mts.id.memberId = m.id AND mts.id.techStackId = :techStackId
            )
          )
        """,
    )
    fun search(
        @Param("name") name: String,
        @Param("status") status: MemberStatus,
        @Param("role") role: RoleType,
        @Param("academicStatus") academicStatus: AcademicStatus?,
        @Param("cohort") cohort: Int?,
        @Param("department") department: DepartmentType?,
        @Param("majorId") majorId: Long?,
        @Param("techStackId") techStackId: Long?,
        pageable: Pageable,
    ): Page<Member>

    // Role/상태를 가리지 않고 이름만으로 찾는다 -- 문의 작성자는 학생/교사/개발자 모두일 수
    // 있다(InquiryAuthorSearchQueryPort 참고). :name은 호출 측이 LIKE Wildcard(%, _)를 이미
    // 이스케이프해 전달한다.
    @Query(
        """
        SELECT m.id FROM Member m
        WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')) ESCAPE '\'
        """,
    )
    fun findIdsByNameContaining(
        @Param("name") name: String,
    ): List<Long>

    // Recommendation Daily Scheduler(R4, Issue #160, RecommendationAudienceQueryPortImpl)가 이미
    // 추천을 활성화한 memberId 집합 중 지금도 실제 대상일 수 있는 재학생만 추려낼 때 쓴다.
    // :status/:academicStatus/:role은 `search`와 같은 관례로 호출 측(Service)이 항상
    // ACTIVE/ENROLLED/STUDENT로 고정해 전달한다.
    @Query(
        """
        SELECT m.id FROM Member m
        WHERE m.id IN :memberIds
          AND m.status = :status
          AND m.academicStatus = :academicStatus
          AND EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role)
        """,
    )
    fun findIdsByIdInAndStatusAndAcademicStatusAndRole(
        @Param("memberIds") memberIds: Collection<Long>,
        @Param("status") status: MemberStatus,
        @Param("academicStatus") academicStatus: AcademicStatus,
        @Param("role") role: RoleType,
    ): List<Long>

    // Portfolio 수합 요청의 제출 대상 검증(PortfolioTargetMemberQueryPortImpl, §26 "Target이 실제
    // STUDENT인지 검증")에 쓴다. :status/:role은 `search`와 같은 관례로 호출 측(Service)이 항상
    // ACTIVE/STUDENT로 고정해 전달한다 -- 탈퇴·정지·승인 대기(로그인 불가) 회원은 제출할 수 없으므로
    // 대상에서 제외해 제출/미제출 현황(§19)이 어긋나지 않게 한다. 재학/졸업 상태(academicStatus)는
    // 걸러내지 않는다 -- 졸업생 Target 허용 여부는 아직 확정되지 않은 Product Decision이라(§39-8)
    // 임의로 배제하지 않는다.
    @Query(
        """
        SELECT m.id FROM Member m
        WHERE m.id IN :memberIds
          AND m.status = :status
          AND EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role)
        """,
    )
    fun findIdsByIdInAndStatusAndRole(
        @Param("memberIds") memberIds: Collection<Long>,
        @Param("status") status: MemberStatus,
        @Param("role") role: RoleType,
    ): List<Long>

    // Notification Producer(JOB_PUBLISHED/PROGRAM_PUBLISHED, Issue #191)가 게시 알림 대상 학생을
    // 찾을 때 쓴다. RecommendationAudienceQueryPortImpl과 달리 후보 memberId 집합을 미리 좁혀오지
    // 않고 전체 재학생 중에서 바로 찾는다(공고·프로그램 게시는 특정 학생 집합이 아니라 "그 조건을
    // 만족하는 모든 재학생"이 대상이기 때문). 학년 조건이 없는 경우(전 학년 대상)는
    // [findIdsByStatusAndAcademicStatusAndRole]을 따로 둬 호출한다 -- 빈 Collection을 IN 절에
    // 바인딩하면 Hibernate가 예외를 던지므로, 학년 필터가 없을 때는 이 Query 자체를 호출하지 않는
    // 방식으로 피한다(NotificationAudienceQueryPortImpl 참고).
    @Query(
        """
        SELECT m.id FROM Member m
        WHERE m.status = :status
          AND m.academicStatus = :academicStatus
          AND m.grade IN :targetGrades
          AND EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role)
        """,
    )
    fun findIdsByStatusAndAcademicStatusAndRoleAndGradeIn(
        @Param("status") status: MemberStatus,
        @Param("academicStatus") academicStatus: AcademicStatus,
        @Param("role") role: RoleType,
        @Param("targetGrades") targetGrades: Set<Int>,
    ): List<Long>

    // [findIdsByStatusAndAcademicStatusAndRoleAndGradeIn]과 조건은 같지만 학년 필터가 없다(전 학년
    // 대상 공고·프로그램).
    @Query(
        """
        SELECT m.id FROM Member m
        WHERE m.status = :status
          AND m.academicStatus = :academicStatus
          AND EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role)
        """,
    )
    fun findIdsByStatusAndAcademicStatusAndRole(
        @Param("status") status: MemberStatus,
        @Param("academicStatus") academicStatus: AcademicStatus,
        @Param("role") role: RoleType,
    ): List<Long>

    // 관리자 담당자 선택 Dropdown용 회원 목록 조회(AdminMemberQueryServiceImpl, Issue #182)에 쓴다.
    // :status/:role은 호출 측(Service)이 ACTIVE/조회 대상 Role로 고정해 전달한다. 이름 오름차순으로
    // 정렬하고(동명이인은 id로 안정 정렬), Dropdown 용도라 Pagination 없이 전체를 돌려준다.
    @Query(
        """
        SELECT m FROM Member m
        WHERE m.status = :status
          AND EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role)
        ORDER BY m.name ASC, m.id ASC
        """,
    )
    fun findAllByStatusAndRoleOrderByName(
        @Param("status") status: MemberStatus,
        @Param("role") role: RoleType,
    ): List<Member>

    // 관리자용 회원 목록 조회(Issue #183)다. `search`(학생 이름 검색 전용, status/role을 Service가
    // 항상 ACTIVE/STUDENT로 고정)와 달리 이 Query는 Role/Status를 가리지 않고, 모든 Filter가
    // Nullable이며 지정하지 않으면 적용하지 않는다(FormRepository.search와 동일한 관례). :hasName이
    // false면 이름 조건은 항상 참으로 취급한다(MemberRepository.search의 :hasApplicantName과 동일한
    // 이유 -- :name을 null로 바인딩하면 LOWER(CONCAT(...)) Type 추론 문제가 난다). :name은 Service
    // 계층에서 LIKE Wildcard를 미리 이스케이프해 전달한다.
    @Query(
        """
        SELECT m FROM Member m
        WHERE (
          :hasName = FALSE
          OR LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')) ESCAPE '\'
        )
          AND (:status IS NULL OR m.status = :status)
          AND (:role IS NULL OR EXISTS (SELECT 1 FROM MemberRole mr WHERE mr.id.memberId = m.id AND mr.id.role = :role))
          AND (:cohort IS NULL OR m.cohort = :cohort)
          AND (:department IS NULL OR m.department = :department)
        ORDER BY m.id DESC
        """,
    )
    fun searchForAdmin(
        @Param("hasName") hasName: Boolean,
        @Param("name") name: String,
        @Param("status") status: MemberStatus?,
        @Param("role") role: RoleType?,
        @Param("cohort") cohort: Int?,
        @Param("department") department: DepartmentType?,
        pageable: Pageable,
    ): Page<Member>
}
