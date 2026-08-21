package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import java.time.LocalDateTime

/**
 * 회원 상세(관리자 관점) 조회, Role 변경, 계정 상태 변경 응답으로 공용 사용한다(Issue #183).
 * 관리자 전용 화면에서 실제 연락·식별이 필요해 email/GitHub URL/전화번호를 포함한다(DECISION_REQUIRED
 * 확정 -- 공개 프로필 응답과 달리 이 Endpoint 전체가 DEVELOPER 전용이라 노출 범위가 다르다).
 */
@Schema(description = "회원 상세(관리자 관점)")
data class AdminMemberDetailResponse(
    @param:Schema(description = "회원 ID", example = "42")
    val memberId: Long,
    @param:Schema(description = "이메일", example = "member@example.com")
    val email: String,
    @param:Schema(description = "회원 이름", nullable = true, example = "김학생")
    val name: String?,
    @param:Schema(description = "계정 상태", example = "ACTIVE")
    val status: MemberStatus,
    @param:Schema(description = "보유 Role 목록")
    val roles: Set<RoleType>,
    @param:Schema(description = "OAuth Provider", example = "DG")
    val oauthProvider: OAuthProvider,
    @param:Schema(description = "재학 상태(학생만 값이 있음)", nullable = true, example = "ENROLLED")
    val academicStatus: AcademicStatus?,
    @param:Schema(description = "기수(학생만 값이 있음)", nullable = true, example = "5")
    val cohort: Int?,
    @param:Schema(description = "학년(학생만 값이 있음)", nullable = true, example = "3")
    val grade: Int?,
    @param:Schema(description = "학과(학생만 값이 있음)", nullable = true, example = "SW_DEVELOPMENT")
    val department: DepartmentType?,
    @param:Schema(description = "전화번호", nullable = true, example = "010-1234-5678")
    val phoneNumber: String?,
    @param:Schema(description = "GitHub URL", nullable = true, example = "https://github.com/example")
    val githubUrl: String?,
    @param:Schema(description = "거절 사유(REJECTED 상태일 때만 값이 있음)", nullable = true)
    val rejectionReason: String?,
    @param:Schema(description = "승인 일시(교직원 승인 완료 시점)", nullable = true)
    val approvedAt: LocalDateTime?,
    @param:Schema(description = "가입일시")
    val createdAt: LocalDateTime,
    @param:Schema(description = "최근 수정일시")
    val updatedAt: LocalDateTime,
    @param:Schema(description = "탈퇴일시(WITHDRAWN 상태일 때만 값이 있음)", nullable = true)
    val withdrawnAt: LocalDateTime?,
)
