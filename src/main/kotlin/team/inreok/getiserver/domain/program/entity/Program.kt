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

    // Phase 1 이후로는 사용하지 않는다(Form 도메인 실연동 결정, 사용자 확인 완료). Program은
    // formId로 forms(V11)의 Form을 참조하고, 신청 답변 구조는 그 Form의 FormVersion Schema를
    // 따른다. 기존 Column·데이터는 유지하되(Migration 없이 보존) 새 코드는 더 이상 쓰지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "application_form_schema", columnDefinition = "jsonb")
    var applicationFormSchema: String? = null

    // 등록자가 단일 담당 교사가 된다(요구사항 6절/8절) — DRAFT 상태에서는 null이고, PUBLISHED로
    // 전이(등록 시 즉시 게시 포함)하는 시점에 createdByMemberId로 채운다.
    @Column(name = "manager_member_id")
    var managerMemberId: Long? = null

    // 모든 Program은 선착순으로만 운영되어 클라이언트 입력을 받지 않는다(요구사항 1절/3.3절).
    @Column(name = "first_come_served", nullable = false)
    var firstComeServed: Boolean = true

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0

    // Application 도메인의 forms(V11)를 가리키는 느슨한 FK다(Modulith 경계 유지, JPA 연관관계
    // 없음). FormType=PROGRAM/ACTIVE/소유자 검증은 Application이 공개한 Named Interface로 한다.
    @Column(name = "form_id")
    var formId: Long? = null

    @Column(name = "discord_channel_id", length = 255)
    var discordChannelId: String? = null

    @Column(name = "discord_message_id", length = 255)
    var discordMessageId: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
