package team.inreok.getiserver.domain.member.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?

    fun findByOauthProviderAndOauthSubject(
        oauthProvider: OAuthProvider,
        oauthSubject: String,
    ): Member?

    @Query(
        """
        SELECT m FROM Member m
        WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%'))
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
        @Param("academicStatus") academicStatus: AcademicStatus?,
        @Param("cohort") cohort: Int?,
        @Param("department") department: DepartmentType?,
        @Param("majorId") majorId: Long?,
        @Param("techStackId") techStackId: Long?,
        pageable: Pageable,
    ): Page<Member>
}
