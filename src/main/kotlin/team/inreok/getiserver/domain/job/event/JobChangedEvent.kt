package team.inreok.getiserver.domain.job.event

import org.springframework.modulith.NamedInterface

/**
 * Job이 다른 Module(현재는 search)이 재조회해야 할 정도로 바뀌었음을 알리는 최소 계약이다.
 * `jobId`만 담고 변경 종류(생성/수정/상태 변경)는 담지 않는다 — 구독 측이 이 Id로 현재 Job
 * 상태를 다시 조회해서 반영하면, Event가 중복되거나 순서가 뒤바뀌어 도착해도 항상 같은 최종
 * 결과로 수렴하기 때문이다(Issue #69).
 *
 * `domain.job.upsert`/`domain.company.query`와 같은 방식으로 이 Package 전체를 Named Interface로
 * 공개한다. Job의 나머지 구현(`entity`, `repository`, `service`)은 여전히 비공개다.
 */
@NamedInterface
data class JobChangedEvent(
    val jobId: Long,
)
