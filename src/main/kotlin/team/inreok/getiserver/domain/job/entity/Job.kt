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

    // 근무지역과 고용형태는 Enum이 아니라 자유 문자열이다(Issue #169). 값 집합이 명세로 확정되지
    // 않았고, 외부 수집 공고가 실제로 받아오는 값도 Provider마다 축이 달라(근무형태, 채용유형
    // 등) 지금 Enum으로 좁히면 표시할 값을 잃는다. 표시 전용이라 검색어 매칭 대상도 아니다.
    @Column(length = 255)
    var location: String? = null

    @Column(name = "employment_type", length = 255)
    var employmentType: String? = null

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
