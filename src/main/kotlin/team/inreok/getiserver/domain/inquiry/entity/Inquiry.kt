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

/**
 * 문의 하나를 표현한다. 하나의 Inquiry는 [team.inreok.getiserver.domain.inquiry.entity.InquiryAnswer]
 * 여러 건을 가질 수 있다(GETI Inquiry 도메인 개발 요구사항 20절, V18에서 1:N 구조로 재구성,
 * 사용자 확인 완료). 이 Entity 자체는 개별 답변 내용을 담지 않는다.
 *
 * `authorMemberId`/`assigneeMemberId`는 Member를 가리키는 ID 참조일 뿐 JPA 연관관계가 아니다
 * (Module 경계 유지, 이 저장소 전체의 일관된 관례).
 */
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

    /** 담당 개발자. 지정 전이거나 해제되면 null이다(요구사항 31절/34절). */
    @Column(name = "assignee_member_id")
    var assigneeMemberId: Long? = null

    // Discord 접수 알림 관련 필드다. 공통 Discord Delivery 계약(discord_deliveries 테이블,
    // PENDING/PROCESSING/DELIVERED/FAILED)이 아직 저장소에 없어(V18 시점 실측, Phase 5 재확인) 당장
    // 대체할 곳이 없다. Program 도메인이 Phase 7 교체 예정으로 남긴 경고와 동일한 방식으로 유지만
    // 하고, 신규 Inquiry 로직(Discord 상태 조회는 InquiryDiscordDeliveryQueryPort 사용, Phase 5)에서는
    // 읽거나 쓰지 않는다.
    @Deprecated("공통 Discord Delivery 계약으로 교체 예정. 신규 로직에서 사용하지 않는다.")
    @Column(name = "discord_message_id", length = 255)
    var discordMessageId: String? = null

    @Deprecated("공통 Discord Delivery 계약으로 교체 예정. 신규 로직에서 사용하지 않는다.")
    @Column(name = "discord_error_message", columnDefinition = "text")
    var discordErrorMessage: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    /** 최초 답변 시각. 이후 추가 답변이 등록돼도 갱신하지 않는다(요구사항 83절 5번, 사용자 확인 완료). */
    @Column(name = "answered_at")
    var answeredAt: LocalDateTime? = null
}
