package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.DepartmentType

@Schema(
    description =
        "학생 프로필 조회 응답. isPublic=false인 대상을 권한 없는 요청자(학생)가 조회하면 " +
            "majors/techStacks/desiredJob/bio가 빈 값으로 내려온다. 교사·개발자는 비공개 프로필도 " +
            "전체를 받는다.",
)
data class MemberProfileResponse(
    @param:Schema(description = "회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "이름", example = "홍길동")
    val name: String,
    @param:Schema(
        description = "프로필 이미지 Presigned URL. 이미지가 없거나 요청자에게 볼 권한이 없으면 null.",
        nullable = true,
    )
    val profileImageUrl: String?,
    @param:Schema(description = "기수", example = "3", nullable = true)
    val cohort: Int?,
    @param:Schema(description = "학과", nullable = true)
    val department: DepartmentType?,
    @param:Schema(description = "전공 이름 목록. profileRestricted=true면 빈 배열.", example = "[\"소프트웨어\"]")
    val majors: List<String>,
    @param:Schema(
        description = "기술 스택 이름 목록. profileRestricted=true면 빈 배열.",
        example = "[\"Kotlin\", \"Spring Boot\"]",
    )
    val techStacks: List<String>,
    @param:Schema(
        description = "희망 직무. profileRestricted=true면 null.",
        example = "Backend Developer",
        nullable = true,
    )
    val desiredJob: String?,
    @param:Schema(description = "자기소개. profileRestricted=true면 null.", nullable = true)
    val bio: String?,
    @param:Schema(
        description = "블로그/포트폴리오 등 추가 링크 목록. profileRestricted=true면 빈 배열. 배열 순서가 표시 순서다.",
    )
    val links: List<MemberProfileLinkResponse>,
    @param:Schema(description = "대상 회원이 설정한 프로필 공개 여부", example = "true")
    val isPublic: Boolean,
    @param:Schema(
        description =
            "true면 이번 응답에서 majors/techStacks/desiredJob/bio가 가려졌음을 의미. " +
                "교사·개발자는 비공개 프로필도 전체를 받으므로 isPublic=false여도 false다.",
        example = "false",
    )
    val profileRestricted: Boolean,
)
