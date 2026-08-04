package team.inreok.getiserver.domain.collector.scheduler

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import java.time.LocalDateTime

/**
 * 서버가 실행 도중 재시작되면 PENDING/RUNNING으로 남아 있던 CollectionRun이 영구적으로 진행
 * 중인 것처럼 보여 해당 Source의 이후 실행을 계속 막는다(`existsBySourceIdAndStatusIn`).
 * 애플리케이션 기동 시 한 번, 그런 Row를 FAILED로 정리해 다음 실행이 막히지 않게 한다.
 * 분산 Lock이 없는 단일 Instance 배포를 전제로 하며(최종 보고 참고), 여러 Instance가 동시에
 * 기동하면 서로의 진행 중이던 실행까지 정리할 수 있다는 한계가 있다.
 *
 * DB 조회 실패는 애플리케이션 기동 자체를 막지 않는다 — 이 정리 작업은 부가 기능이고, Flyway를
 * 비활성화한 채 Context 기동만 검증하는 일부 Test(`GetiServerApplicationTests` 등)처럼 Schema가
 * 없는 환경도 있기 때문이다.
 */
@Component
class CollectorStaleRunRecoveryRunner(
    private val collectionRunRepository: CollectionRunRepository,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val stale =
            try {
                collectionRunRepository.findAllByStatusIn(ACTIVE_STATUSES)
            } catch (ex: DataAccessException) {
                log.warn("서버 재시작 정리용 CollectionRun 조회에 실패해 이번 기동에서는 건너뜁니다.", ex)
                return
            }
        if (stale.isEmpty()) return

        val now = LocalDateTime.now()
        stale.forEach { run ->
            run.status = CollectionRunStatus.FAILED
            run.finishedAt = now
        }
        collectionRunRepository.saveAll(stale)
        log.warn("서버 재시작으로 중단된 수집 실행 {}건을 FAILED로 정리했습니다: runIds={}", stale.size, stale.map { it.id })
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING)
        val log = LoggerFactory.getLogger(CollectorStaleRunRecoveryRunner::class.java)
    }
}
