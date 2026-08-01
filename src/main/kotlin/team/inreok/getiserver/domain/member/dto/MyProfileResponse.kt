package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType

@Schema(description = "내 프로필 전체 조회 응답. 학생/교사/개발자 공통으로 사용하며, 해당하지 않는 Field는 null 또는 빈 값이다.")
data class MyProfileResponse(
    @param:Schema(description = "회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "이름", example = "홍길동")
    val name: String,
    @param:Schema(description = "이메일", example = "student@example.com")
    val email: String,
    @param:Schema(description = "내부 역할 목록", example = "[\"STUDENT\"]")
    val roles: List<RoleType>,
    @param:Schema(description = "회원 상태")
    val status: MemberStatus,
    @param:Schema(description = "학적 상태(학생이 아니면 null)", nullable = true)
    val academicStatus: AcademicStatus?,
    @param:Schema(description = "기수(학생이 아니면 null일 수 있음)", example = "3", nullable = true)
    val cohort: Int?,
    @param:Schema(description = "학과(학생이 아니면 null일 수 있음)", nullable = true)
    val department: DepartmentType?,
    @param:Schema(description = "전화번호", example = "010-1234-5678", nullable = true)
    val phone: String?,
    @param:Schema(description = "프로필 이미지 URL. File 업로드 연동 전이라 항상 null.", nullable = true)
    val profileImageUrl: String?,
    @param:Schema(description = "희망 직무", example = "Backend Developer", nullable = true)
    val desiredJob: String?,
    @param:Schema(description = "자기소개", nullable = true)
    val bio: String?,
    @param:Schema(description = "GitHub URL", example = "https://github.com/example", nullable = true)
    val githubUrl: String?,
    @param:Schema(description = "프로필 공개 여부", example = "true")
    val isPublic: Boolean,
    @param:Schema(description = "선택한 전공 이름 목록", example = "[\"소프트웨어\"]")
    val majors: List<String>,
    @param:Schema(description = "선택한 기술 스택 이름 목록", example = "[\"Kotlin\"]")
    val techStacks: List<String>,
)
