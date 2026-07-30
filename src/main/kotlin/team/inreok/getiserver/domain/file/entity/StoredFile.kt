package team.inreok.getiserver.domain.file.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "files")
class StoredFile(
    @Column(name = "owner_type", nullable = false, length = 100)
    var ownerType: String,
    @Column(name = "owner_id", nullable = false)
    var ownerId: Long,
    @Column(name = "object_key", nullable = false, length = 1000)
    var objectKey: String,
    @Column(name = "original_name", nullable = false, length = 500)
    var originalName: String,
    @Column(name = "content_type", nullable = false, length = 255)
    var contentType: String,
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,
    @Column(name = "contains_personal_information", nullable = false)
    var containsPersonalInformation: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "uploader_member_id")
    var uploaderMemberId: Long? = null

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
