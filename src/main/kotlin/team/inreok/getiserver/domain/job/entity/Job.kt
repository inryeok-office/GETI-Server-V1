package team.inreok.getiserver.domain.job.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

@Entity
@Table(name = "jobs")
class Job(
    @Column(name = "company_id", nullable = false)
    var companyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: PostingType,
    @Enumerated(EnumType.STRING)
    @Column(name = "application_method", nullable = false, length = 30)
    var applicationMethod: ApplicationMethod,
    @Column(nullable = false, length = 500)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: JobStatus = JobStatus.DRAFT,
    @Column(name = "first_come_served", nullable = false)
    var firstComeServed: Boolean = false,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_by_member_id")
    var createdByMemberId: Long? = null

    @Column(name = "manager_member_id")
    var managerMemberId: Long? = null

    @Column(name = "body_markdown", columnDefinition = "text")
    var bodyMarkdown: String? = null

    @Column(name = "source_name", length = 255)
    var sourceName: String? = null

    @Column(name = "external_job_id", length = 255)
    var externalJobId: String? = null

    @Column(name = "external_url", length = 2000)
    var externalUrl: String? = null

    @Column(name = "discord_channel_key", length = 255)
    var discordChannelKey: String? = null

    @Column(name = "recruitment_started_at")
    var recruitmentStartedAt: LocalDateTime? = null

    @Column(name = "recruitment_ended_at")
    var recruitmentEndedAt: LocalDateTime? = null

    @Column(name = "target_grade")
    var targetGrade: Int? = null

    var capacity: Int? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", columnDefinition = "jsonb")
    var requiredSkills: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "application_form_schema", columnDefinition = "jsonb")
    var applicationFormSchema: String? = null

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
