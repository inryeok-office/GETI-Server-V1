package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.repository.TechStackRepository

@ExtendWith(MockitoExtension::class)
class TechStackMatchQueryPortImplTest {
    @Mock
    private lateinit var techStackRepository: TechStackRepository

    private val port by lazy { TechStackMatchQueryPortImpl(techStackRepository) }

    @Test
    fun `대소문자와 공백만 다르면 매칭한다`() {
        given(techStackRepository.findAll()).willReturn(listOf(techStack(1L, "Spring Boot")))

        val result = port.matchByNames(listOf("  spring boot  "))

        assertThat(result).containsEntry("spring boot", 1L)
    }

    @Test
    fun `근거 없이 유사 이름을 병합하지 않는다`() {
        // "Spring"과 "Spring Boot"는 서로 다른 기술로 남아야 한다(GETI Notion "Tech Stack 정규화" 절).
        given(techStackRepository.findAll()).willReturn(listOf(techStack(1L, "Spring Boot")))

        val result = port.matchByNames(listOf("Spring"))

        assertThat(result).isEmpty()
    }

    @Test
    fun `매칭되지 않은 이름은 결과 Map에 없다`() {
        given(techStackRepository.findAll()).willReturn(listOf(techStack(1L, "Spring Boot")))

        val result = port.matchByNames(listOf("Roboflow"))

        assertThat(result).doesNotContainKey("Roboflow")
    }

    @Test
    fun `빈 입력은 조회 없이 빈 결과를 반환한다`() {
        val result = port.matchByNames(emptyList())

        assertThat(result).isEmpty()
    }

    private fun techStack(
        id: Long,
        name: String,
    ) = TechStack(name = name, category = TechStackCategory.BACKEND).apply { this.id = id }
}
