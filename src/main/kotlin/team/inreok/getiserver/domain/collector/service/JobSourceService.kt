package team.inreok.getiserver.domain.collector.service

import team.inreok.getiserver.domain.collector.dto.JobSourceListResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateRequest
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateResponse
import team.inreok.getiserver.domain.collector.dto.PublicJobSourceListResponse

interface JobSourceService {
    /** 등록된 모든 수집원의 설정과 상태를 sourceCode 오름차순으로 반환한다. */
    fun list(): JobSourceListResponse

    /** `GET /api/v1/job-sources`(전체 인증 사용자 공개)용 최소 정보 목록이다. */
    fun listPublic(activeOnly: Boolean): PublicJobSourceListResponse

    /**
     * 수집원 활성 상태를 변경한다. 활성화(`enabled=true`)는 승인 완료(READY), 설정 완료
     * (configured), 진행 중인 실행이 없어야 하는 세 조건을 모두 만족해야 한다. 비활성화는
     * 제한 없이 항상 허용한다.
     */
    fun updateEnabled(
        sourceId: Long,
        request: JobSourceUpdateRequest,
    ): JobSourceUpdateResponse
}
