package team.inreok.getiserver.domain.portfolio.entity

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
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import java.time.LocalDateTime

/**
 * 교사·개발자가 생성하는 포트폴리오 수합 요청 Aggregate다(Portfolio 도메인 요구사항 §4).
 *
 * 제출 대상 학생은 이 Entity가 아니라 [PortfolioRequestTarget](구체적 studentId 집합)에 저장한다
 * (§5/§19). 제출 기간은 `dueAt` 하나만 사용한다(§25/§38). File 첨부(안내/양식 파일)와 제출 필수
 * 자료 정책(allowFile/allowUrl)은 각각 File Integration(Phase 3)과 Product Decision(§15) 확정
 * 전까지 이 Entity에 넣지 않는다 — 가짜 값을 미리 만들지 않기 위함이다.
 */
@Entity
@Table(name = "portfolio_requests")
class PortfolioRequest(
    @Column(name = "created_by_member_id", nullable = false)
    var createdByMemberId: Long,
    @Column(nullable = false, length = 500)
    var title: String,
    @Column(name = "due_at", nullable = false)
    var dueAt: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PortfolioRequestStatus = PortfolioRequestStatus.DRAFT,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(columnDefinition = "text")
    var description: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
