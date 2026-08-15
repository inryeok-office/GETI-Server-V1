package team.inreok.getiserver.domain.application.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidate
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobApplicationFileAccessCheckerTest {
    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock
    private lateinit var inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort

    private val checker: JobApplicationFileAccessChecker by lazy {
        JobApplicationFileAccessChecker(jobApplicationRepository, inquiryAssigneeCandidateQueryPort)
    }

    private fun applicationOf(applicantMemberId: Long = 10L) =
        JobApplication(
            jobId = 1L,
            applicantMemberId = applicantMemberId,
            attemptNumber = 1,
            contactEmail = "student@example.com",
            answers = "[]",
        ).apply { id = 1L }

    @Test
    fun `지원서 본인은 다운로드할 수 있다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf(applicantMemberId = 10L)))

        assertThat(checker.canDownload(requesterId = 10L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `교사는 다른 학생의 지원서 첨부파일도 다운로드할 수 있다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf(applicantMemberId = 10L)))
        given(inquiryAssigneeCandidateQueryPort.findById(20L))
            .willReturn(
                InquiryAssigneeCandidate(memberId = 20L, name = "선생님", roles = setOf("TEACHER"), status = "ACTIVE"),
            )

        assertThat(checker.canDownload(requesterId = 20L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `개발자는 다운로드할 수 있다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf(applicantMemberId = 10L)))
        given(inquiryAssigneeCandidateQueryPort.findById(30L))
            .willReturn(
                InquiryAssigneeCandidate(memberId = 30L, name = "개발자", roles = setOf("DEVELOPER"), status = "ACTIVE"),
            )

        assertThat(checker.canDownload(requesterId = 30L, ownerId = 1L)).isTrue()
    }

    @Test
    fun `본인도 교사·개발자도 아니면 거부한다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf(applicantMemberId = 10L)))
        given(inquiryAssigneeCandidateQueryPort.findById(40L))
            .willReturn(
                InquiryAssigneeCandidate(memberId = 40L, name = "다른 학생", roles = setOf("STUDENT"), status = "ACTIVE"),
            )

        assertThat(checker.canDownload(requesterId = 40L, ownerId = 1L)).isFalse()
    }

    @Test
    fun `요청자 회원 정보를 찾을 수 없으면 거부한다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf(applicantMemberId = 10L)))
        given(inquiryAssigneeCandidateQueryPort.findById(50L)).willReturn(null)

        assertThat(checker.canDownload(requesterId = 50L, ownerId = 1L)).isFalse()
    }

    @Test
    fun `지원서가 없으면 거부한다`() {
        given(jobApplicationRepository.findById(999L)).willReturn(Optional.empty())

        assertThat(checker.canDownload(requesterId = 1L, ownerId = 999L)).isFalse()
    }
}
