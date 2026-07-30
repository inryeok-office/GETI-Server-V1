package team.inreok.getiserver.domain.inquiry.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import java.time.LocalDateTime

@Entity
@Table(name = "inquiries")
class Inquiry(
    @Column(name = "author_member_id", nullable = false)
    var authorMemberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: InquiryType,
    @Column(nullable = false, length = 500)
    var title: String,
    @Column(nullable = false, columnDefinition = "text")
    var content: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: InquiryStatus = InquiryStatus.RECEIVED,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "answered_by_member_id")
    var answeredByMemberId: Long? = null

    @Column(columnDefinition = "text")
    var answer: String? = null

    @Column(name = "discord_message_id", length = 255)
    var discordMessageId: String? = null

    @Column(name = "discord_error_message", columnDefinition = "text")
    var discordErrorMessage: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "answered_at")
    var answeredAt: LocalDateTime? = null

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null
}
