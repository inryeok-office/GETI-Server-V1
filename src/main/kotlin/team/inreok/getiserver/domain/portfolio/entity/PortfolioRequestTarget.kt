package team.inreok.getiserver.domain.portfolio.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/**
 * 수합 요청([PortfolioRequest])의 실제 제출 대상 학생 한 명을 저장한다(요구사항 §5).
 *
 * `UNIQUE(request_id, student_member_id)`로 같은 학생이 한 요청에 중복 등록되는 것을 막는다.
 * 이렇게 구체적 studentId 집합을 Backend가 소유해야 제출/미제출 현황(§19)을 정확히 계산할 수
 * 있다. 기수·학과 같은 대상 조건 자체는 저장하지 않는다 — Frontend가 Member 검색으로 studentId를
 * 확정해 전달한다(§6).
 */
@Entity
@Table(
    name = "portfolio_request_targets",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_portfolio_request_targets_request_student",
            columnNames = ["request_id", "student_member_id"],
        ),
    ],
)
class PortfolioRequestTarget(
    @Column(name = "request_id", nullable = false)
    var requestId: Long,
    @Column(name = "student_member_id", nullable = false)
    var studentMemberId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
