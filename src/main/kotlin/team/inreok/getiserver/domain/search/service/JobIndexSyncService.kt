package team.inreok.getiserver.domain.search.service

/**
 * 공고 하나의 색인을 현재 Postgres 상태에 맞춰 동기화한다(생성/갱신이면 UPSERT, 삭제·비공개면
 * DELETE, Issue #69). Event 발행 시점의 값이 아니라 호출 시점의 최신 상태를 다시 읽으므로,
 * 중복되거나 순서가 뒤바뀐 호출도 항상 같은 최종 Document로 수렴한다.
 */
interface JobIndexSyncService {
    fun syncOne(jobId: Long)
}
