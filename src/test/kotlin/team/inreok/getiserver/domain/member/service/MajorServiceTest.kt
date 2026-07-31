package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Major
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.service.impl.MajorServiceImpl

@ExtendWith(MockitoExtension::class)
class MajorServiceTest {
    @Mock
    private lateinit var majorRepository: MajorRepository

    private val service: MajorService by lazy { MajorServiceImpl(majorRepository) }

    @Test
    fun `activeOnly가 true이면 활성 전공만 조회한다`() {
        val major = Major(name = "소프트웨어", active = true).apply { id = 1L }
        given(majorRepository.findAllByActiveTrueOrderByNameAsc()).willReturn(listOf(major))

        val result = service.search(true)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].majorId).isEqualTo(1L)
        assertThat(result.items[0].name).isEqualTo("소프트웨어")
        assertThat(result.items[0].active).isTrue()
    }

    @Test
    fun `activeOnly가 없으면 전체 전공을 조회한다`() {
        val active = Major(name = "소프트웨어", active = true).apply { id = 1L }
        val inactive = Major(name = "폐지전공", active = false).apply { id = 2L }
        given(majorRepository.findAllByOrderByNameAsc()).willReturn(listOf(active, inactive))

        val result = service.search(null)

        assertThat(result.items).hasSize(2)
    }
}
