package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.repository.TechStackRepository
import team.inreok.getiserver.domain.member.service.impl.TechStackServiceImpl

@ExtendWith(MockitoExtension::class)
class TechStackServiceTest {
    @Mock
    private lateinit var techStackRepository: TechStackRepository

    @Test
    fun `Repository가 반환한 Entity를 응답 DTO로 변환한다`() {
        val kotlin = TechStack(name = "Kotlin", category = TechStackCategory.BACKEND).apply { id = 1L }
        given(techStackRepository.search("kot", TechStackCategory.BACKEND)).willReturn(listOf(kotlin))
        val service: TechStackService = TechStackServiceImpl(techStackRepository)

        val result = service.search("kot", TechStackCategory.BACKEND)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].techStackId).isEqualTo(1L)
        assertThat(result.items[0].name).isEqualTo("Kotlin")
        assertThat(result.items[0].category).isEqualTo(TechStackCategory.BACKEND)
    }

    @Test
    fun `일치하는 항목이 없으면 빈 목록을 반환한다`() {
        given(techStackRepository.search(null, null)).willReturn(emptyList())
        val service: TechStackService = TechStackServiceImpl(techStackRepository)

        val result = service.search(null, null)

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `검색어에 LIKE Wildcard가 있으면 이스케이프해서 Repository에 전달한다`() {
        given(techStackRepository.search("C\\_\\_", null)).willReturn(emptyList())
        val service: TechStackService = TechStackServiceImpl(techStackRepository)

        val result = service.search("C__", null)

        assertThat(result.items).isEmpty()
    }
}
