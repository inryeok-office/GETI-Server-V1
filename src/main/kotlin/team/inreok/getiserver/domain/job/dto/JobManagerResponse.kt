package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.query.JobManagerSnapshot

@Schema(description = "공고 담당자 요약")
data class JobManagerResponse(
    @param:Schema(description = "회원 ID", example = "7")
    val memberId: Long,
    @param:Schema(description = "회원 이름", example = "홍길동")
    val name: String,
) {
    companion object {
        fun from(snapshot: JobManagerSnapshot): JobManagerResponse? =
            snapshot.name?.let { JobManagerResponse(snapshot.memberId, it) }
    }
}
