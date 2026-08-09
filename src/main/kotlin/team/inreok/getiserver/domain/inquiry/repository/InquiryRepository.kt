package team.inreok.getiserver.domain.inquiry.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType

interface InquiryRepository : JpaRepository<Inquiry, Long> {
    /**
     * 답변 등록(POST .../answers)과 상태 변경(PATCH .../status)이 같은 Inquiry Row를 동시에
     * CLOSED 전환/답변 등록하려는 경합을 막기 위해 Pessimistic Write Lock으로 조회한다(§CLOSED-답변
     * 동시성, 사용자 확인 완료 -- Row Lock 방식 채택). 두 작업 모두 반드시 이 Method로 조회한 뒤
     * CLOSED 여부를 검사해야 한다 -- 그래야 먼저 Commit되는 쪽이 이기고, 나중 요청은 갱신된 상태를
     * 읽어 정합성 있게 거부되거나 성공한다.
     *
     * `ProgramRepository.findByIdForUpdate`/`StoredFileRepository.findAllByIdInForUpdate`와 같은
     * 이유와 방식이다(이 저장소 첫 Optimistic/Pessimistic Lock 도입이 아니라 기존 관례를 그대로
     * 따른 것). Lock 보유 시간을 최소화하기 위해 파일 첨부 검증처럼 느릴 수 있는 작업은 이 Method를
     * 호출하기 전에 모두 끝낸다(InquiryServiceImpl.createAnswer 참고).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inquiry i WHERE i.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Inquiry?

    /** 내 문의 목록(status 필터 없음). 최신순(요구사항 14절)으로 고정한다. */
    fun findAllByAuthorMemberIdOrderByCreatedAtDescIdDesc(
        authorMemberId: Long,
        pageable: Pageable,
    ): Page<Inquiry>

    /** 내 문의 목록(status 필터 있음). */
    fun findAllByAuthorMemberIdAndStatusOrderByCreatedAtDescIdDesc(
        authorMemberId: Long,
        status: InquiryStatus,
        pageable: Pageable,
    ): Page<Inquiry>

    /**
     * 관리자용 전체 문의 목록이다(요구사항 26절/27절). 모든 Filter는 null이면 조건을 적용하지
     * 않고(ProgramRepository.search와 동일한 관례), 서로 AND로 결합된다.
     *
     * `:assigneeId`(요청한 assigneeId Filter)와 `:mineOnlyMemberId`(mineOnly=true일 때 현재
     * 로그인한 개발자 id)는 같은 Column(`assigneeMemberId`)에 대한 별개의 조건이며 단순 AND로
     * 결합한다(요구사항 29절, "임의로 복잡한 충돌 규칙을 만들지 않는다", 사용자 확인 완료) --
     * 두 값이 다르면 결과가 항상 빈 목록이 되지만 그것이 두 조건을 각각 존중하는 정직한 동작이다.
     *
     * `:query`는 title/content에 대해 대소문자 무시 LIKE로, `:authorIds`(작성자 이름이 일치하는
     * Member id 집합, `InquiryAuthorSearchQueryPort`로 미리 조회)에 대해서는 `IN`으로 검색한다
     * (요구사항 28절, 검색 대상 title/content/author name -- studentNumber는 제외).
     * `:hasQuery`가 false면 이 OR 그룹 전체가 참이 되어 검색어 없음과 동일하게 동작한다 --
     * `:query` 자체를 null로 바인딩하지 않는 이유는, `LOWER(CONCAT('%', ?, '%'))`처럼 문자열
     * 연산 안에서 값이 NULL인 Parameter는 PostgreSQL이 Type을 추론하지 못해 `bytea`로 잘못
     * 확정해 `function lower(bytea) does not exist` 오류를 내기 때문이다(Testcontainers
     * 실측으로 발견). `:query`는 검색어가 없을 때도 항상 빈 문자열 등 실제 String 값으로
     * 바인딩한다(단, `:hasQuery=false`라 그 값은 조건 평가에 쓰이지 않는다). `:query`는 Service
     * 계층에서 LIKE Wildcard(%, _)를 미리 이스케이프해 전달한다
     * (domain.inquiry.service.escapeLikePattern 참고). SQL Injection 방어는 이 Parameter
     * Binding(`:query`, `:authorIds`)이 전부 담당하며 문자열을 직접 이어 붙이지 않는다.
     */
    @Query(
        """
        SELECT i FROM Inquiry i
        WHERE (:type IS NULL OR i.type = :type)
          AND (:status IS NULL OR i.status = :status)
          AND (:assigneeId IS NULL OR i.assigneeMemberId = :assigneeId)
          AND (:mineOnlyMemberId IS NULL OR i.assigneeMemberId = :mineOnlyMemberId)
          AND (
            :hasQuery = FALSE
            OR LOWER(i.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\'
            OR LOWER(i.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\'
            OR i.authorMemberId IN :authorIds
          )
        ORDER BY i.createdAt DESC, i.id DESC
        """,
    )
    fun searchForAdmin(
        @Param("type") type: InquiryType?,
        @Param("status") status: InquiryStatus?,
        @Param("assigneeId") assigneeId: Long?,
        @Param("mineOnlyMemberId") mineOnlyMemberId: Long?,
        @Param("hasQuery") hasQuery: Boolean,
        @Param("query") query: String,
        @Param("authorIds") authorIds: Collection<Long>,
        pageable: Pageable,
    ): Page<Inquiry>
}
