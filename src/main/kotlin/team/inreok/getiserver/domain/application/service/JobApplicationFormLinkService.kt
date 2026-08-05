package team.inreok.getiserver.domain.application.service

import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkResponse

interface JobApplicationFormLinkService {
    /**
     * 공고에 활성 양식을 연결(또는 재연결)한다(요구사항 6절). [isDeveloper]가 false면 요청자가
     * 공고 등록자 또는 담당 교사여야 한다.
     */
    fun link(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: JobApplicationFormLinkRequest,
    ): JobApplicationFormLinkResponse
}
