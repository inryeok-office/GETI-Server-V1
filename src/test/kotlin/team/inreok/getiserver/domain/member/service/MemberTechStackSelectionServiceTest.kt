package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.TechStackNotFoundException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository

@ExtendWith(MockitoExtension::class)
class MemberTechStackSelectionServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var techStackRepository: TechStackRepository

    @Mock
    private lateinit var memberTechStackRepository: MemberTechStackRepository

    private val service by lazy {
        MemberTechStackSelectionService(memberRepository, techStackRepository, memberTechStackRepository)
    }

    @Test
    fun `기술 스택 목록을 전체 교체하고 이름순으로 반환한다`() {
        given(memberRepository.existsById(1L)).willReturn(true)
        val kotlin = TechStack(name = "Kotlin", category = TechStackCategory.BACKEND).apply { id = 10L }
        val react = TechStack(name = "React", category = TechStackCategory.FRONTEND).apply { id = 20L }
        given(techStackRepository.findAllById(listOf(10L, 20L))).willReturn(listOf(kotlin, react))

        val result = service.replaceAll(1L, listOf(10L, 20L))

        assertThat(result.techStacks).extracting("name").containsExactly("Kotlin", "React")
    }

    @Test
    fun `존재하지 않는 회원이면 MemberNotFoundException을 던진다`() {
        given(memberRepository.existsById(999L)).willReturn(false)

        assertThatThrownBy { service.replaceAll(999L, listOf(1L)) }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `존재하지 않는 techStackId가 있으면 TechStackNotFoundException을 던진다`() {
        given(memberRepository.existsById(1L)).willReturn(true)
        given(techStackRepository.findAllById(listOf(10L))).willReturn(emptyList())

        assertThatThrownBy { service.replaceAll(1L, listOf(10L)) }
            .isInstanceOf(TechStackNotFoundException::class.java)
    }
}
