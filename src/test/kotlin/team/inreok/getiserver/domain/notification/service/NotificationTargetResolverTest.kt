package team.inreok.getiserver.domain.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.application.query.JobApplicationNotificationTargetQueryPort
import team.inreok.getiserver.domain.application.query.JobApplicationNotificationTargetSnapshot
import team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetQueryPort
import team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetSnapshot
import team.inreok.getiserver.domain.job.query.JobNotificationTargetQueryPort
import team.inreok.getiserver.domain.job.query.JobNotificationTargetSnapshot
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetQueryPort
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetSnapshot

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationTargetResolverTest {
    @Mock
    private lateinit var jobPort: JobNotificationTargetQueryPort

    @Mock
    private lateinit var programPort: ProgramNotificationTargetQueryPort

    @Mock
    private lateinit var inquiryPort: InquiryNotificationTargetQueryPort

    @Mock
    private lateinit var applicationPort: JobApplicationNotificationTargetQueryPort

    private val viewerMemberId = 1L

    private fun resolver() = NotificationTargetResolver(jobPort, programPort, inquiryPort, applicationPort)

    private fun jobSnapshot(
        id: Long,
        status: String,
        deleted: Boolean = false,
    ) = JobNotificationTargetSnapshot(jobId = id, status = status, deleted = deleted)

    private fun programSnapshot(
        id: Long,
        status: String,
        deleted: Boolean = false,
    ) = ProgramNotificationTargetSnapshot(programId = id, status = status, deleted = deleted)

    @Test
    fun `게시된 공고는 이동할 수 있고 deepLink를 내려준다`() {
        val ref = NotificationTargetRef(NotificationTargetType.JOB, 10L)
        given(jobPort.findAllByIds(setOf(10L))).willReturn(mapOf(10L to jobSnapshot(10L, "PUBLISHED")))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isTrue
        assertThat(result[ref]?.reason).isNull()
        assertThat(result[ref]?.deepLink).isEqualTo("/jobs/10")
    }

    @Test
    fun `마감된 프로그램도 여전히 열어볼 수 있다`() {
        val ref = NotificationTargetRef(NotificationTargetType.PROGRAM, 20L)
        given(programPort.findAllByIds(setOf(20L))).willReturn(mapOf(20L to programSnapshot(20L, "CLOSED")))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isTrue
        assertThat(result[ref]?.deepLink).isEqualTo("/programs/20")
    }

    @Test
    fun `deletedAt이 채워진 대상은 DELETED로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.PROGRAM, 21L)
        given(programPort.findAllByIds(setOf(21L)))
            .willReturn(mapOf(21L to programSnapshot(21L, "PUBLISHED", deleted = true)))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isFalse
        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.DELETED)
        assertThat(result[ref]?.deepLink).isNull()
    }

    @Test
    fun `상태가 DELETED인 대상도 DELETED로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.JOB, 11L)
        given(jobPort.findAllByIds(setOf(11L))).willReturn(mapOf(11L to jobSnapshot(11L, "DELETED")))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.DELETED)
    }

    @Test
    fun `원본 Row 자체가 사라졌으면 DELETED로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.JOB, 12L)
        given(jobPort.findAllByIds(setOf(12L))).willReturn(emptyMap())

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isFalse
        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.DELETED)
    }

    @Test
    fun `DRAFT 상태는 NOT_VISIBLE로 판정하고 deepLink를 내려주지 않는다`() {
        val ref = NotificationTargetRef(NotificationTargetType.PROGRAM, 22L)
        given(programPort.findAllByIds(setOf(22L))).willReturn(mapOf(22L to programSnapshot(22L, "DRAFT")))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isFalse
        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.NOT_VISIBLE)
        assertThat(result[ref]?.deepLink).isNull()
    }

    @Test
    fun `아직 해석하지 않는 대상 유형은 이동 불가로 두되 이유를 지어내지 않는다`() {
        val ref = NotificationTargetRef(NotificationTargetType.MEMBER_APPROVAL, 30L)

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isFalse
        assertThat(result[ref]?.reason).isNull()
        assertThat(result[ref]?.deepLink).isNull()
        verifyNoInteractions(jobPort, programPort, inquiryPort, applicationPort)
    }

    @Test
    fun `같은 유형의 대상이 여러 건이어도 Domain당 조회를 한 번만 한다`() {
        val refs =
            setOf(
                NotificationTargetRef(NotificationTargetType.JOB, 1L),
                NotificationTargetRef(NotificationTargetType.JOB, 2L),
                NotificationTargetRef(NotificationTargetType.JOB, 3L),
                NotificationTargetRef(NotificationTargetType.PROGRAM, 4L),
                NotificationTargetRef(NotificationTargetType.PROGRAM, 5L),
            )
        given(jobPort.findAllByIds(setOf(1L, 2L, 3L)))
            .willReturn((1L..3L).associateWith { jobSnapshot(it, "PUBLISHED") })
        given(programPort.findAllByIds(setOf(4L, 5L)))
            .willReturn((4L..5L).associateWith { programSnapshot(it, "PUBLISHED") })

        val result = resolver().resolveAll(refs, viewerMemberId)

        assertThat(result).hasSize(5)
        assertThat(result.values).allMatch { it.available }
        verify(jobPort, times(1)).findAllByIds(setOf(1L, 2L, 3L))
        verify(programPort, times(1)).findAllByIds(setOf(4L, 5L))
    }

    @Test
    fun `해석할 대상이 없으면 Port를 호출하지 않는다`() {
        val result = resolver().resolveAll(emptySet(), viewerMemberId)

        assertThat(result).isEmpty()
        verifyNoInteractions(jobPort, programPort, inquiryPort, applicationPort)
    }

    @Test
    fun `내가 작성한 문의는 이동할 수 있고 deepLink를 내려준다`() {
        val ref = NotificationTargetRef(NotificationTargetType.INQUIRY, 30L)
        given(inquiryPort.findAllByIds(setOf(30L)))
            .willReturn(mapOf(30L to InquiryNotificationTargetSnapshot(30L, authorMemberId = viewerMemberId)))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isTrue
        assertThat(result[ref]?.reason).isNull()
        assertThat(result[ref]?.deepLink).isEqualTo("/inquiries/30")
    }

    @Test
    fun `다른 사용자가 작성한 문의는 FORBIDDEN으로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.INQUIRY, 31L)
        given(inquiryPort.findAllByIds(setOf(31L)))
            .willReturn(mapOf(31L to InquiryNotificationTargetSnapshot(31L, authorMemberId = 999L)))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isFalse
        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.FORBIDDEN)
        assertThat(result[ref]?.deepLink).isNull()
    }

    @Test
    fun `사라진 문의는 DELETED로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.INQUIRY, 32L)
        given(inquiryPort.findAllByIds(setOf(32L))).willReturn(emptyMap())

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.DELETED)
    }

    @Test
    fun `내 지원서는 상태와 무관하게 이동할 수 있다`() {
        val ref = NotificationTargetRef(NotificationTargetType.JOB_APPLICATION, 40L)
        given(applicationPort.findAllByIds(setOf(40L)))
            .willReturn(
                mapOf(40L to JobApplicationNotificationTargetSnapshot(40L, applicantMemberId = viewerMemberId)),
            )

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isTrue
        assertThat(result[ref]?.deepLink).isEqualTo("/job-applications/40")
    }

    @Test
    fun `다른 사용자의 지원서는 FORBIDDEN으로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.JOB_APPLICATION, 41L)
        given(applicationPort.findAllByIds(setOf(41L)))
            .willReturn(mapOf(41L to JobApplicationNotificationTargetSnapshot(41L, applicantMemberId = 999L)))

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.available).isFalse
        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.FORBIDDEN)
        assertThat(result[ref]?.deepLink).isNull()
    }

    @Test
    fun `사라진 지원서는 DELETED로 판정한다`() {
        val ref = NotificationTargetRef(NotificationTargetType.JOB_APPLICATION, 42L)
        given(applicationPort.findAllByIds(setOf(42L))).willReturn(emptyMap())

        val result = resolver().resolveAll(setOf(ref), viewerMemberId)

        assertThat(result[ref]?.reason).isEqualTo(NotificationTargetUnavailableReason.DELETED)
    }

    @Test
    fun `소유 리소스도 Domain당 조회를 한 번만 한다`() {
        val refs =
            setOf(
                NotificationTargetRef(NotificationTargetType.INQUIRY, 1L),
                NotificationTargetRef(NotificationTargetType.INQUIRY, 2L),
                NotificationTargetRef(NotificationTargetType.JOB_APPLICATION, 3L),
                NotificationTargetRef(NotificationTargetType.JOB_APPLICATION, 4L),
            )
        given(inquiryPort.findAllByIds(setOf(1L, 2L)))
            .willReturn((1L..2L).associateWith { InquiryNotificationTargetSnapshot(it, viewerMemberId) })
        given(applicationPort.findAllByIds(setOf(3L, 4L)))
            .willReturn((3L..4L).associateWith { JobApplicationNotificationTargetSnapshot(it, viewerMemberId) })

        val result = resolver().resolveAll(refs, viewerMemberId)

        assertThat(result).hasSize(4)
        assertThat(result.values).allMatch { it.available }
        verify(inquiryPort, times(1)).findAllByIds(setOf(1L, 2L))
        verify(applicationPort, times(1)).findAllByIds(setOf(3L, 4L))
    }
}
