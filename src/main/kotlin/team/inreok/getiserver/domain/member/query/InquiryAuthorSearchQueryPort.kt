package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `inquiry` Module의 관리자용 문의 검색(GETI Inquiry 도메인 개발 요구사항 26절/28절, 검색 대상
 * title/content/author name)이 작성자 이름으로도 검색할 수 있게 하는 공개 계약이다.
 *
 * Inquiry는 `authorMemberId`(ID)만 가지고 있어 이름 검색을 직접 수행할 수 없다. 물리 FK가
 * `members` Table을 가리킨다고 해서 Native Query로 직접 Join하지 않는다 -- 그러면 Module 경계가
 * DB Schema 수준으로 새어 나간다(이 저장소의 일관된 원칙). 대신 이 Port로 이름이 일치하는
 * Member id 집합만 받아 `authorMemberId IN (...)` 조건으로 사용한다.
 */
@NamedInterface
interface InquiryAuthorSearchQueryPort {
    /**
     * 이름에 [nameQuery]가 포함된(대소문자 무시) Member의 id 집합을 반환한다. 매치가 없으면
     * 빈 집합이다. [nameQuery]는 호출 측이 LIKE Wildcard(%, _)를 이미 이스케이프해 전달한다.
     */
    fun findIdsByNameContaining(nameQuery: String): Set<Long>
}
