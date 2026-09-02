package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `ai` Module이 OpenAI가 추출한 기술 이름을 GETI Tech Stack Master와 매칭할 때 쓰는 공개
 * 계약이다(AI Analysis Phase 1, Issue #132). `ai`는 이 Interface를 통해서만 Tech Stack을 읽고,
 * `TechStack` Entity나 `TechStackRepository`를 직접 참조하지 않는다.
 *
 * 정확(공백/대소문자 무시) 매칭만 수행한다 -- "Spring"과 "Spring Boot"처럼 근거 없이 서로 다른
 * 기술을 하나로 합치는 공격적 매칭은 하지 않는다(GETI Notion AI Analysis Phase 1 "Tech Stack
 * 정규화" 절).
 */
@NamedInterface
interface TechStackMatchQueryPort {
    /**
     * 입력한 이름 원문을 Key로, 매칭된 Tech Stack ID를 Value로 돌려준다. 매칭되지 않은 이름은
     * 결과 Map에 없다 -- 호출 측이 없는 Key를 `techStackId = null`로 다룬다.
     */
    fun matchByNames(names: Collection<String>): Map<String, Long>
}
