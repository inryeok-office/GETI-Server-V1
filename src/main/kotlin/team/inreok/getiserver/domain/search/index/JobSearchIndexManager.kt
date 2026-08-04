package team.inreok.getiserver.domain.search.index

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.elasticsearch.core.index.AliasAction
import org.springframework.data.elasticsearch.core.index.AliasActionParameters
import org.springframework.data.elasticsearch.core.index.AliasActions
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.Query
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.search.config.SearchProperties
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import java.time.format.DateTimeFormatter

/**
 * Elasticsearch 색인·조회를 담당하는 저수준 Component다(Issue #69). `JobSearchDocument`의
 * `@Document(indexName=...)`는 Spring Data Elasticsearch가 요구하는 기본값일 뿐이고, 실제
 * Index 이름은 항상 여기서 명시적 [IndexCoordinates]로 지정한다 — 재색인이 Timestamp Index를
 * 새로 만들고 Alias를 전환해야 하므로 고정 이름 하나로는 표현할 수 없기 때문이다.
 *
 * `search`의 나머지 구현(`event`, `service`, `reindex`)은 Elasticsearch Client/Operations를
 * 직접 참조하지 않고 이 Class를 통해서만 색인에 접근한다.
 */
@Component
@EnableConfigurationProperties(SearchProperties::class)
class JobSearchIndexManager(
    private val elasticsearchOperations: ElasticsearchOperations,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(JobSearchIndexManager::class.java)

    /** 검색 Query가 항상 바라보는 고정 Alias의 [IndexCoordinates]. */
    fun aliasCoordinates(): IndexCoordinates = IndexCoordinates.of(properties.indexAlias)

    fun upsert(document: JobSearchDocument) {
        elasticsearchOperations.save(document, aliasCoordinates())
    }

    fun delete(jobId: Long) {
        // 이미 없는 Document를 지우려는 요청도 성공으로 취급한다(멱등 삭제, Issue #69 요구사항).
        elasticsearchOperations.delete(jobId.toString(), aliasCoordinates())
    }

    fun search(query: Query): SearchHits<JobSearchDocument> =
        elasticsearchOperations.search(query, JobSearchDocument::class.java, aliasCoordinates())

    /** 새 물리 Index를 만들고(Mapping/Setting 포함) 이름을 반환한다. 아직 Alias는 연결하지 않는다. */
    fun createNewPhysicalIndex(): String {
        val indexName = "${properties.indexPrefix}-${TIMESTAMP_FORMATTER.format(java.time.LocalDateTime.now())}"
        val indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName))
        indexOps.create(indexOps.createSettings(JobSearchDocument::class.java))
        indexOps.putMapping(indexOps.createMapping(JobSearchDocument::class.java))
        return indexName
    }

    fun bulkIndex(
        indexName: String,
        documents: List<JobSearchDocument>,
    ) {
        if (documents.isEmpty()) return
        val queries =
            documents.map {
                org.springframework.data.elasticsearch.core.query
                    .IndexQueryBuilder()
                    .withId(it.id)
                    .withObject(it)
                    .build()
            }
        elasticsearchOperations.bulkIndex(queries, IndexCoordinates.of(indexName))
    }

    /** 현재 Alias가 가리키는 물리 Index 이름 목록이다(정상 상태라면 0개 또는 1개). */
    fun resolveIndicesBehindAlias(): Set<String> {
        val aliasOps = elasticsearchOperations.indexOps(aliasCoordinates())
        if (!aliasOps.exists()) return emptySet()
        return elasticsearchOperations
            .indexOps(IndexCoordinates.of(properties.indexAlias))
            .getAliases(properties.indexAlias)
            .keys
    }

    /**
     * `newIndexName`에 Alias를 연결하고, 그 전에 연결되어 있던 다른 물리 Index에서는 제거한다.
     * 두 Action을 하나의 요청으로 보내 검색 공백이 생기지 않는다.
     */
    fun switchAlias(
        newIndexName: String,
        previousIndexNames: Set<String>,
    ) {
        val actions = mutableListOf<AliasAction>()
        actions +=
            AliasAction.Add(
                AliasActionParameters
                    .builder()
                    .withIndices(newIndexName)
                    .withAliases(properties.indexAlias)
                    .build(),
            )
        previousIndexNames.filter { it != newIndexName }.forEach { previous ->
            actions +=
                AliasAction.Remove(
                    AliasActionParameters
                        .builder()
                        .withIndices(previous)
                        .withAliases(properties.indexAlias)
                        .build(),
                )
        }
        elasticsearchOperations.indexOps(IndexCoordinates.of(newIndexName)).alias(AliasActions(*actions.toTypedArray()))
    }

    fun deleteIndex(indexName: String) {
        runCatching { elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).delete() }
            .onFailure { ex -> log.warn("재색인 후 이전 Index 삭제 실패(무시하고 계속 진행): {}", indexName, ex) }
    }

    companion object {
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
    }
}
