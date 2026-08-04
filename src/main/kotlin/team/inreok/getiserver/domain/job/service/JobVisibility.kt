package team.inreok.getiserver.domain.job.service

import team.inreok.getiserver.domain.job.entity.type.JobStatus

/**
 * 공개 목록/상세에서 노출할 수 있는 공고 상태다. `search.dto.PublicJobStatus`(Query Parameter
 * 계약)와 값 집합은 같지만, 이건 `job` 내부 판정용이라 별도 Enum 없이 상수로 둔다(Issue #69 —
 * `GET /api/v1/jobs` 목록의 소유권이 `search`로 옮겨지며 `PublicJobStatus` Enum도 함께
 * 옮겼다. `job`에 남은 유일한 공개 판정은 상세 조회뿐이다).
 */
internal val PUBLIC_VISIBLE_STATUSES = setOf(JobStatus.PUBLISHED, JobStatus.CLOSED)
