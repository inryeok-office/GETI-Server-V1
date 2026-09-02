package team.inreok.getiserver.domain.program.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.repository.ProgramRepository

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramFileAccessCheckerTest {
    @Mock
    private lateinit var programRepository: ProgramRepository

    @Mock
    private lateinit var memberRoleQueryPort: MemberRoleQueryPort

    private val checker: ProgramFileAccessChecker by lazy {
        ProgramFileAccessChecker(programRepository, memberRoleQueryPort)
    }

    private fun programOf(
        status: ProgramStatus,
        createdByMemberId: Long = 7L,
        managerMemberId: Long? = null,
    ) = Program(
        createdByMemberId = createdByMemberId,
        type = ProgramType.SPECIAL_LECTURE,
        title = "특강",
        status = status,
    ).apply {
        id = 1L
        this.managerMemberId = managerMemberId
    }

    @Test
    fun `PUBLISHED 프로그램은 등록자가 아닌 인증된 사용자도 다운로드할 수 있다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.PUBLISHED))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `CLOSED 프로그램은 등록자가 아닌 인증된 사용자도 다운로드할 수 있다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.CLOSED))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `PUBLISHED 프로그램은 조기 반환으로 findRoles를 호출하지 않는다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.PUBLISHED))

        checker.canDownload(requesterId = 99L, ownerId = 1L)

        verify(memberRoleQueryPort, never()).findRoles(99L)
    }

    @Test
    fun `DRAFT 프로그램은 등록자·담당교사·개발자가 아니면 다운로드할 수 없다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L, managerMemberId = 8L))
        given(memberRoleQueryPort.findRoles(99L)).willReturn(setOf(RoleType.STUDENT))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isFalse()
    }

    @Test
    fun `DRAFT 프로그램은 등록자가 다운로드할 수 있다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L))

        assertThat(checker.canDownload(requesterId = 7L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `DRAFT 프로그램은 담당 교사가 다운로드할 수 있다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L, managerMemberId = 8L))

        assertThat(checker.canDownload(requesterId = 8L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `DRAFT 프로그램은 개발자가 다운로드할 수 있다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L))
        given(memberRoleQueryPort.findRoles(99L)).willReturn(setOf(RoleType.DEVELOPER))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `존재하지 않거나 삭제된 프로그램은 다운로드를 거부한다`() {
        given(programRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThat(checker.canDownload(requesterId = 7L, ownerId = 1L)).isFalse()
    }
}
