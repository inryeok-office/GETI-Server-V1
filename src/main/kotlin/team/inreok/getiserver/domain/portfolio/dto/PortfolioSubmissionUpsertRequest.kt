package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus

/**
 * 학생 포트폴리오 제출(임시저장/제출) 요청이다(요구사항 §14, Issue #204).
 *
 * 같은 요청·학생 조합의 제출물은 하나뿐이므로(UNIQUE(request_id, member_id)) 이 요청은 매번 그
 * 단일 제출물을 통째로 덮어쓰는 Upsert다 -- 부분 수정이 아니라 전달한 값이 최종 상태가 된다.
 *
 * [status]로 임시저장([PortfolioSubmissionStatus.DRAFT])과 제출 확정([PortfolioSubmissionStatus.SUBMITTED])을
 * 구분한다. [fileIds]는 이 제출물에 연결할 파일의 최종 집합이며(넘긴 목록이 곧 연결 상태가 된다),
 * 실제 소유권·목적 검증은 File 도메인의 연결 계약이 수행한다.
 */
@Schema(description = "포트폴리오 제출(임시저장/제출) 요청")
data class PortfolioSubmissionUpsertRequest(
    @param:Schema(
        description = "제출 상태. DRAFT는 임시저장, SUBMITTED는 제출 확정이다.",
        example = "SUBMITTED",
    )
    val status: PortfolioSubmissionStatus,
    @field:Size(max = 2000, message = "포트폴리오 URL은 2000자를 넘을 수 없습니다.")
    @param:Schema(
        description = "포트폴리오 URL(선택)",
        example = "https://portfolio.example.com/me",
        nullable = true,
        maxLength = 2000,
    )
    val portfolioUrl: String? = null,
    @param:Schema(description = "제출 메모(선택)", example = "일부 자료는 링크로 대체했습니다.", nullable = true)
    val note: String? = null,
    // 잘못된 요청 하나가 대량 IN 조회·연결로 이어지지 않도록 상한을 둔다(File 연결 정책의 목적별
    // 최대 개수와 별개로, 요청 크기 자체를 막는 방어선).
    @field:Size(max = 100, message = "첨부 파일은 100개를 넘을 수 없습니다.")
    @param:Schema(
        description = "이 제출물에 연결할 파일 ID의 최종 목록. 넘긴 목록이 곧 연결 상태가 된다(기본 빈 목록).",
        example = "[1, 2]",
    )
    val fileIds: List<Long> = emptyList(),
)
