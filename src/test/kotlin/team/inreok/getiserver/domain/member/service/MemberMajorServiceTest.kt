package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Major
import team.inreok.getiserver.domain.member.exception.DuplicateMajorException
import team.inreok.getiserver.domain.member.exception.MajorNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.MemberMajorRepository
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class MemberMajorServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var majorRepository: MajorRepository

    @Mock
    private lateinit var memberMajorRepository: MemberMajorRepository

    private val service by lazy { MemberMajorService(memberRepository, majorRepository, memberMajorRepository) }

    @Test
    fun `전공 목록을 전체 교체하고 이름순으로 반환한다`() {
        given(memberRepository.existsById(1L)).willReturn(true)
        val backend = Major(name = "소프트웨어", active = true).apply { id = 10L }
        val ai = Major(name = "인공지능", active = true).apply { id = 20L }
        given(majorRepository.findAllById(listOf(10L, 20L))).willReturn(listOf(backend, ai))

        val result = service.replaceAll(1L, listOf(10L, 20L))

        assertThat(result.majors).extracting("name").containsExactly("소프트웨어", "인공지능")
    }

    @Test
    fun `존재하지 않는 회원이면 MemberNotFoundException을 던진다`() {
        given(memberRepository.existsById(999L)).willReturn(false)

        assertThatThrownBy { service.replaceAll(999L, listOf(1L)) }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `요청에 중복된 majorId가 있으면 DuplicateMajorException을 던진다`() {
        given(memberRepository.existsById(1L)).willReturn(true)

        assertThatThrownBy { service.replaceAll(1L, listOf(10L, 10L)) }
            .isInstanceOf(DuplicateMajorException::class.java)
    }

    @Test
    fun `존재하지 않는 majorId가 있으면 MajorNotFoundException을 던진다`() {
        given(memberRepository.existsById(1L)).willReturn(true)
        given(majorRepository.findAllById(listOf(10L, 20L))).willReturn(emptyList())

        assertThatThrownBy { service.replaceAll(1L, listOf(10L, 20L)) }
            .isInstanceOf(MajorNotFoundException::class.java)
    }
}
