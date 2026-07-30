package team.inreok.getiserver.domain.recommendation.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import java.time.LocalDateTime

@Entity
@Table(name = "member_job_preferences")
class MemberJobPreference(
    @EmbeddedId
    val id: MemberJobPreferenceId,
    @Column(nullable = false)
    var bookmarked: Boolean = false,
) {
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var exclusion: ExclusionType? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
