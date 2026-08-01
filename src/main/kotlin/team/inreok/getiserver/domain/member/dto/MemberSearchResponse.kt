package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.DepartmentType

@Schema(description = "학생 이름 검색 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class MemberSearchResponse(
    @param:Schema(description = "검색 결과 목록")
    val content: List<MemberSearchItemResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "false")
    val last: Boolean,
)

@Schema(description = "검색 결과 항목")
data class MemberSearchItemResponse(
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
    @param:Schema(description = "프로필 공개 여부", example = "true")
    val isPublic: Boolean,
)
