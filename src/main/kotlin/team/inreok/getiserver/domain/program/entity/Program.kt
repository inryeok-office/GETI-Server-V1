package team.inreok.getiserver.domain.program.entity

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
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

@Entity
@Table(name = "programs")
class Program(
    @Column(name = "created_by_member_id", nullable = false)
    var createdByMemberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: ProgramType,
    @Column(nullable = false, length = 500)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ProgramStatus = ProgramStatus.DRAFT,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "body_markdown", columnDefinition = "text")
    var bodyMarkdown: String? = null

    @Column(length = 500)
    var location: String? = null

    @Column(name = "event_started_at")
    var eventStartedAt: LocalDateTime? = null

    @Column(name = "event_ended_at")
    var eventEndedAt: LocalDateTime? = null

    @Column(name = "application_started_at")
    var applicationStartedAt: LocalDateTime? = null

    @Column(name = "application_ended_at")
    var applicationEndedAt: LocalDateTime? = null

    var capacity: Int? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "application_form_schema", columnDefinition = "jsonb")
    var applicationFormSchema: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
