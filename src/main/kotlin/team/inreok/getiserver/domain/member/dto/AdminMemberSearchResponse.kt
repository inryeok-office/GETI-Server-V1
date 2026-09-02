package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import java.time.LocalDateTime

/**
 * 관리자용 회원 목록 조회 응답이다(Issue #183). `JobApplicationAdminListResponse`와 동일한 관례로
 * Spring Data Page 규약과 동일하게 page는 0부터 시작한다.
 */
@Schema(description = "관리자용 회원 목록 결과")
data class AdminMemberSearchResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<AdminMemberSearchItemResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "true")
    val last: Boolean,
)

@Schema(description = "관리자용 회원 목록 항목")
data class AdminMemberSearchItemResponse(
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
    @param:Schema(description = "기수(학생만 값이 있음)", nullable = true, example = "5")
    val cohort: Int?,
    @param:Schema(description = "학과(학생만 값이 있음)", nullable = true, example = "SW_DEVELOPMENT")
    val department: DepartmentType?,
    @param:Schema(description = "가입일시")
    val createdAt: LocalDateTime,
)
