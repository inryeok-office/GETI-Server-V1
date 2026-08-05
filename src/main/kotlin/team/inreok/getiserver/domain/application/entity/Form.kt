package team.inreok.getiserver.domain.application.entity

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
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import java.time.LocalDateTime

/**
 * 교사·개발자 개인 소유의 재사용 가능한 신청 양식 템플릿이다(요구사항 5.1절). 공고나 프로그램에
 * 종속되어 생성되지 않는다 — 공고와의 연결은 별도 Phase에서 다룬다(`docs/application/application-domain-plan.md`
 * §3.8). `ownerMemberId`는 다른 Domain의 FK와 동일하게 평범한 Column으로 보관하고 JPA
 * 연관관계를 만들지 않는다(Modulith 경계 유지, `docs/architecture/erd.md` 원칙).
 */
@Entity
@Table(name = "forms")
class Form(
    @Column(name = "owner_member_id", nullable = false)
    var ownerMemberId: Long,
    @Column(nullable = false, length = 255)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false, length = 20)
    var formType: FormType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FormStatus = FormStatus.DRAFT,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(columnDefinition = "text")
    var description: String? = null

    // 최초 생성 시 1이고, Field 구조가 바뀔 때마다 FormVersion을 새로 만들며 함께 증가한다.
    @Column(name = "current_version", nullable = false)
    var currentVersion: Int = 1

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
