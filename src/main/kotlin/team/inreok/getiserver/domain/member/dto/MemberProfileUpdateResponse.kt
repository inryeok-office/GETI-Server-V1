package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import java.time.LocalDateTime

@Schema(description = "내 프로필 수정 응답. PATCH 이후 회원의 전체 필드를 반환한다.")
data class MemberProfileUpdateResponse(
    @param:Schema(description = "회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "이름", example = "홍길동")
    val name: String,
    @param:Schema(description = "학과", nullable = true)
    val department: DepartmentType?,
    @param:Schema(description = "전화번호", example = "010-1234-5678", nullable = true)
    val phone: String?,
    @param:Schema(description = "희망 직무", example = "Backend Developer", nullable = true)
    val desiredJob: String?,
    @param:Schema(description = "자기소개", nullable = true)
    val bio: String?,
    @param:Schema(
        description = "GitHub URL. 하위 호환을 위해 유지하며 links와 별도로 관리된다.",
        example = "https://github.com/example",
        nullable = true,
    )
    val githubUrl: String?,
    @param:Schema(description = "블로그/포트폴리오 등 추가 링크 목록. 배열 순서가 표시 순서다.")
    val links: List<MemberProfileLinkResponse>,
    @param:Schema(description = "프로필 공개 여부", example = "true")
    val isPublic: Boolean,
    @param:Schema(description = "프로필 이미지 URL. File 업로드 연동 전이라 항상 null.", nullable = true)
    val profileImageUrl: String?,
    @param:Schema(description = "수정 시각(UTC)", example = "2026-08-01T12:00:00")
    val updatedAt: LocalDateTime,
)
