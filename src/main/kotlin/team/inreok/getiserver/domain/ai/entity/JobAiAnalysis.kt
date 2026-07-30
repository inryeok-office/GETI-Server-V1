package team.inreok.getiserver.domain.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import java.time.LocalDateTime

@Entity
@Table(name = "job_ai_analyses")
class JobAiAnalysis(
    @Id
    @Column(name = "job_id")
    val jobId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: AiStatus = AiStatus.PENDING,
    @Column(name = "reanalysis_count", nullable = false)
    var reanalysisCount: Int = 0,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime,
) {
    @Column(columnDefinition = "text")
    var summary: String? = null

    @Column(name = "eligibility_summary", columnDefinition = "text")
    var eligibilitySummary: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_skills", columnDefinition = "jsonb")
    var extractedSkills: String? = null

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
}
