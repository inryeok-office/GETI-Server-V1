package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "지원서 초안 생성 요청")
data class CreateJobApplicationRequest(
    @param:Schema(
        description =
            "true면 회원 프로필에서 사용 가능한 필드(이름·기수·학과·전공·전화번호·희망직무·기술스택)를 자동 입력한다. " +
                "연락처 이메일은 이 값과 무관하게 항상 회원 계정 이메일로 채워진다.",
        example = "true",
    )
    val prefillProfileFields: Boolean = true,
)
