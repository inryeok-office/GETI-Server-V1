package team.inreok.getiserver.domain.inquiry.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/**
 * 문의에 달린 답변 하나다. 하나의 [Inquiry]는 여러 [InquiryAnswer]를 가질 수 있다(요구사항 20절).
 *
 * 답변 수정·삭제 API는 명세에 없으므로(요구사항 48절) 한 번 등록된 답변은 이력으로 보존하고
 * 바뀌지 않는다 -- `content`를 포함한 모든 필드가 `val`인 이유다. `inquiryId`는 같은 Domain
 * 안의 참조지만 이 저장소 전체가 JPA 연관관계 대신 ID 참조만 쓰는 관례(예:
 * [team.inreok.getiserver.domain.program.entity.ProgramApplication.programId])를 그대로 따른다.
 */
@Entity
@Table(name = "inquiry_answers")
class InquiryAnswer(
    @Column(name = "inquiry_id", nullable = false)
    val inquiryId: Long,
    @Column(name = "author_member_id", nullable = false)
    val authorMemberId: Long,
    @Column(nullable = false, columnDefinition = "text")
    val content: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
