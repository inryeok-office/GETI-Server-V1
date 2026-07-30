package team.inreok.getiserver.domain.recommendation.entity

import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class MemberJobPreferenceId(
    val memberId: Long,
    val jobId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
