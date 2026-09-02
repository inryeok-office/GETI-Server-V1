package team.inreok.getiserver.domain.ai.entity.type

import org.springframework.modulith.NamedInterface

/** 고졸/신입 지원 적합성 판정. 공고 진입 요구 수준에 대한 판정이며 지원자 개인 역량 평가가 아니다. */
@NamedInterface
enum class AiFitLevel {
    SUITABLE,
    CONDITIONAL,
    UNSUITABLE,
}
