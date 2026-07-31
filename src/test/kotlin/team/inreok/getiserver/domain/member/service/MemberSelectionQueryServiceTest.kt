package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Major
import team.inreok.getiserver.domain.member.entity.MemberMajor
import team.inreok.getiserver.domain.member.entity.MemberMajorId
import team.inreok.getiserver.domain.member.entity.MemberTechStack
import team.inreok.getiserver.domain.member.entity.MemberTechStackId
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.MemberMajorRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository
import team.inreok.getiserver.domain.member.service.impl.MemberSelectionQueryServiceImpl

@ExtendWith(MockitoExtension::class)
class MemberSelectionQueryServiceTest {
    @Mock
    private lateinit var memberMajorRepository: MemberMajorRepository

    @Mock
    private lateinit var majorRepository: MajorRepository

    @Mock
    private lateinit var memberTechStackRepository: MemberTechStackRepository

    @Mock
    private lateinit var techStackRepository: TechStackRepository

    private val service: MemberSelectionQueryService by lazy {
        MemberSelectionQueryServiceImpl(
            memberMajorRepository,
            majorRepository,
            memberTechStackRepository,
            techStackRepository,
        )
    }

    @Test
    fun `선택한 전공 이름 목록을 이름순으로 반환한다`() {
        given(memberMajorRepository.findAllByIdMemberId(1L)).willReturn(
            listOf(MemberMajor(MemberMajorId(1L, 20L)), MemberMajor(MemberMajorId(1L, 10L))),
        )
        given(majorRepository.findAllById(listOf(20L, 10L))).willReturn(
            listOf(
                Major(name = "인공지능", active = true).apply { id = 20L },
                Major(name = "소프트웨어", active = true).apply { id = 10L },
            ),
        )

        val result = service.getMajorNames(1L)

        assertThat(result).containsExactly("소프트웨어", "인공지능")
    }

    @Test
    fun `선택한 전공이 없으면 빈 목록을 반환한다`() {
        given(memberMajorRepository.findAllByIdMemberId(1L)).willReturn(emptyList())

        assertThat(service.getMajorNames(1L)).isEmpty()
    }

    @Test
    fun `선택한 기술 스택 이름 목록을 이름순으로 반환한다`() {
        given(memberTechStackRepository.findAllByIdMemberId(1L)).willReturn(
            listOf(MemberTechStack(MemberTechStackId(1L, 10L))),
        )
        given(techStackRepository.findAllById(listOf(10L))).willReturn(
            listOf(TechStack(name = "Kotlin", category = TechStackCategory.BACKEND).apply { id = 10L }),
        )

        val result = service.getTechStackNames(1L)

        assertThat(result).containsExactly("Kotlin")
    }

    @Test
    fun `선택한 기술 스택이 없으면 빈 목록을 반환한다`() {
        given(memberTechStackRepository.findAllByIdMemberId(1L)).willReturn(emptyList())

        assertThat(service.getTechStackNames(1L)).isEmpty()
    }
}
