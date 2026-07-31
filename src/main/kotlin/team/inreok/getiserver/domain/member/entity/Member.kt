package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import java.time.LocalDateTime

@Entity
@Table(
    name = "members",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_members_oauth_provider_subject", columnNames = ["oauth_provider", "oauth_subject"]),
        UniqueConstraint(name = "uk_members_email", columnNames = ["email"]),
    ],
)
class Member(
    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 30)
    var oauthProvider: OAuthProvider,
    @Column(name = "oauth_subject", nullable = false, length = 255)
    var oauthSubject: String,
    @Column(nullable = false, length = 255)
    var email: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: MemberStatus = MemberStatus.PENDING,
    @Column(name = "profile_public", nullable = false)
    var profilePublic: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(length = 100)
    var name: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_status", length = 30)
    var academicStatus: AcademicStatus? = null

    var cohort: Int? = null

    var grade: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var department: DepartmentType? = null

    @Column(name = "profile_image_file_id")
    var profileImageFileId: Long? = null

    @Column(name = "phone_number", length = 30)
    var phoneNumber: String? = null

    @Column(length = 1000)
    var introduction: String? = null

    @Column(name = "github_url", length = 500)
    var githubUrl: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_positions", columnDefinition = "jsonb")
    var desiredPositions: String? = null

    @Column(name = "rejection_reason", length = 1000)
    var rejectionReason: String? = null

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "withdrawn_at")
    var withdrawnAt: LocalDateTime? = null
}
