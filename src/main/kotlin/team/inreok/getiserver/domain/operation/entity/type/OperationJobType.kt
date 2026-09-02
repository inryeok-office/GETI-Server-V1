package team.inreok.getiserver.domain.operation.entity.type

import org.springframework.modulith.NamedInterface

/** Stable business identifiers for the six schedulers exposed by the admin operations view. */
@NamedInterface
enum class OperationJobType {
    JOB_COLLECTION,
    JOB_NOTIFICATION_RETRY,
    DISCORD_DELIVERY_RETRY,
    PROGRAM_CLOSE,
    RECOMMENDATION_GENERATION,
    SEARCH_INDEX_RETRY,
}
