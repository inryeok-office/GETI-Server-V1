package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.DepartmentType

@Schema(description = "학생 프로필 조회 응답. isPublic=false인 대상은 majors/techStacks/desiredJob/bio가 빈 값으로 내려온다.")
data class MemberProfileResponse(
    @param:Schema(description = "회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "이름", example = "홍길동")
    val name: String,
    @param:Schema(description = "프로필 이미지 URL. File 업로드 연동 전이라 항상 null.", nullable = true)
    val profileImageUrl: String?,
    @param:Schema(description = "기수", example = "3", nullable = true)
    val cohort: Int?,
    @param:Schema(description = "학과", nullable = true)
    val department: DepartmentType?,
    @param:Schema(description = "전공 이름 목록. 비공개 프로필이면 빈 배열.", example = "[\"소프트웨어\"]")
    val majors: List<String>,
    @param:Schema(description = "기술 스택 이름 목록. 비공개 프로필이면 빈 배열.", example = "[\"Kotlin\", \"Spring Boot\"]")
    val techStacks: List<String>,
    @param:Schema(description = "희망 직무. 비공개 프로필이면 null.", example = "Backend Developer", nullable = true)
    val desiredJob: String?,
    @param:Schema(description = "자기소개. 비공개 프로필이면 null.", nullable = true)
    val bio: String?,
    @param:Schema(description = "프로필 공개 여부", example = "true")
    val isPublic: Boolean,
    @param:Schema(
        description = "true면 비공개 프로필이라 majors/techStacks/desiredJob/bio가 빈 값으로 제한됨을 의미",
        example = "false",
    )
    val profileRestricted: Boolean,
)
