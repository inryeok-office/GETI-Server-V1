package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.TechStackMatchQueryPort
import team.inreok.getiserver.domain.member.repository.TechStackRepository

/**
 * 다른 Domain Module(ai)에 공개된 조회 계약([TechStackMatchQueryPort])의 구현이다.
 * `TechStackServiceImpl`에 합치지 않고 분리한다(`JobDiscordPayloadQueryPortImpl`이
 * `JobServiceImpl`과 분리된 것과 같은 이유).
 *
 * Tech Stack Master는 Application이 관리하는 소규모 고정 목록이라(수백 건 이하) 매칭 시점마다
 * 전체를 한 번에 읽어 메모리에서 비교한다 -- 기술 이름 하나마다 별도 조회를 하는 N+1을 피한다.
 */
@Service
class TechStackMatchQueryPortImpl(
    private val techStackRepository: TechStackRepository,
) : TechStackMatchQueryPort {
    @Transactional(readOnly = true)
    override fun matchByNames(names: Collection<String>): Map<String, Long> {
        val candidates = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (candidates.isEmpty()) return emptyMap()

        val byNormalizedName = techStackRepository.findAll().associateBy { normalize(it.name) }
        return candidates
            .mapNotNull { name -> byNormalizedName[normalize(name)]?.let { name to requireNotNull(it.id) } }
            .toMap()
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}
