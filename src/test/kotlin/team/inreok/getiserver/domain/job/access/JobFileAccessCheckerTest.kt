package team.inreok.getiserver.domain.job.access

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
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobFileAccessCheckerTest {
    @Mock
    private lateinit var jobRepository: JobRepository

    @Mock
    private lateinit var memberRoleQueryPort: MemberRoleQueryPort

    private val checker: JobFileAccessChecker by lazy {
        JobFileAccessChecker(jobRepository, memberRoleQueryPort)
    }

    private fun jobOf(
        status: JobStatus,
        createdByMemberId: Long? = 7L,
        managerMemberId: Long? = null,
    ) = Job(
        companyId = 1L,
        type = PostingType.MOU,
        applicationMethod = ApplicationMethod.EXTERNAL,
        title = "2026 상반기 백엔드 채용",
        status = status,
    ).apply {
        id = 1L
        this.createdByMemberId = createdByMemberId
        this.managerMemberId = managerMemberId
    }

    @Test
    fun `PUBLISHED 공고는 등록자가 아닌 인증된 사용자도 다운로드할 수 있다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(jobOf(status = JobStatus.PUBLISHED))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `CLOSED 공고는 등록자가 아닌 인증된 사용자도 다운로드할 수 있다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(jobOf(status = JobStatus.CLOSED))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `PUBLISHED 공고는 조기 반환으로 findRoles를 호출하지 않는다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(jobOf(status = JobStatus.PUBLISHED))

        checker.canDownload(requesterId = 99L, ownerId = 1L)

        verify(memberRoleQueryPort, never()).findRoles(99L)
    }

    @Test
    fun `DRAFT 공고는 등록자·담당 교사·개발자가 아니면 다운로드할 수 없다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(jobOf(status = JobStatus.DRAFT, createdByMemberId = 7L, managerMemberId = 8L))
        given(memberRoleQueryPort.findRoles(99L)).willReturn(setOf(RoleType.STUDENT))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isFalse()
    }

    @Test
    fun `DRAFT 공고는 등록자가 다운로드할 수 있다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(jobOf(status = JobStatus.DRAFT, createdByMemberId = 7L))

        assertThat(checker.canDownload(requesterId = 7L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `DRAFT 공고는 담당 교사가 다운로드할 수 있다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(jobOf(status = JobStatus.DRAFT, createdByMemberId = 7L, managerMemberId = 8L))

        assertThat(checker.canDownload(requesterId = 8L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `DRAFT 공고는 개발자가 다운로드할 수 있다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L))
            .willReturn(jobOf(status = JobStatus.DRAFT, createdByMemberId = 7L))
        given(memberRoleQueryPort.findRoles(99L)).willReturn(setOf(RoleType.DEVELOPER))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `존재하지 않거나 삭제된 공고는 다운로드를 거부한다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThat(checker.canDownload(requesterId = 7L, ownerId = 1L)).isFalse()
    }
}
