package team.inreok.getiserver.domain.company.entity

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
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "companies")
class Company(
    @Column(nullable = false, length = 255)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: CompanyType,
    @Enumerated(EnumType.STRING)
    @Column(name = "mou_status", nullable = false, length = 30)
    var mouStatus: MouStatus = MouStatus.NONE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "logo_file_id")
    var logoFileId: Long? = null

    @Column(columnDefinition = "text")
    var description: String? = null

    @Column(length = 255)
    var industry: String? = null

    @Column(length = 1000)
    var address: String? = null

    @Column(name = "website_url", length = 1000)
    var websiteUrl: String? = null

    @Column(length = 255)
    var source: String? = null

    @Column(name = "mou_started_on")
    var mouStartedOn: LocalDate? = null

    @Column(name = "mou_ended_on")
    var mouEndedOn: LocalDate? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    @Column(name = "representative_email", length = 320)
    var representativeEmail: String? = null

    @Column(name = "representative_phone", length = 100)
    var representativePhone: String? = null

    @Column(columnDefinition = "text")
    var memo: String? = null
}
