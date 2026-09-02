package team.inreok.getiserver.domain.portfolio.service

import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionUpsertRequest

interface PortfolioSubmissionService {
    /**
     * 학생이 자신의 포트폴리오를 임시저장하거나 제출한다(요구사항 §14, Issue #204).
     *
     * 요청·학생 조합의 제출물은 하나뿐이므로 없으면 만들고 있으면 덮어쓴다(Upsert). 요청자가 대상이
     * 아니면 403(NOT_REQUEST_TARGET), 아직 공개되지 않았거나 없으면 404(존재 비노출),
     * 이미 마감(CLOSED)됐거나 마감 시각을 지났으면 409(SUBMISSION_CLOSED)로 거부한다.
     */
    fun submit(
        requestId: Long,
        requesterId: Long,
        request: PortfolioSubmissionUpsertRequest,
    ): PortfolioSubmissionResponse
}
