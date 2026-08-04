package team.inreok.getiserver.domain.search.reindex

import team.inreok.getiserver.domain.search.entity.SearchReindexRun

/**
 * 전체 재색인을 접수하고 실행한다(Issue #69). [triggerReindex]는 실행 이력을 PENDING으로
 * 즉시 만들고 반환하며, 실제 재색인은 별도 Thread에서 비동기로 진행된다.
 */
interface SearchReindexService {
    /** 이미 진행 중인 재색인이 있으면 [team.inreok.getiserver.domain.search.exception.ReindexAlreadyRunningException]을 던진다. */
    fun triggerReindex(): SearchReindexRun
}
