package team.inreok.getiserver.domain.recommendation.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.operation.query.OperationJobState

@NamedInterface
fun interface RecommendationSchedulerStatusQueryPort {
    fun getStatus(): OperationJobState
}
