package team.inreok.getiserver.domain.member.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}
