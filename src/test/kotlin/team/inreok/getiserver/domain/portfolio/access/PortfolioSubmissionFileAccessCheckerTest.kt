package team.inreok.getiserver.domain.portfolio.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidate
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PortfolioSubmissionFileAccessCheckerTest {
    @Mock
    private lateinit var submissionRepository: PortfolioSubmissionRepository

    @Mock
    private lateinit var inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort

    private val checker by lazy {
        PortfolioSubmissionFileAccessChecker(submissionRepository, inquiryAssigneeCandidateQueryPort)
    }

    @Test
    fun `제출 학생 본인은 다운로드할 수 있다`() {
        given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.of(submission(memberId = 7L)))

        assertThat(checker.canDownload(requesterId = 7L, ownerId = SUBMISSION_ID)).isTrue()
    }

    @Test
    fun `교사는 다른 학생의 제출물도 다운로드할 수 있다`() {
        given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.of(submission(memberId = 7L)))
        given(inquiryAssigneeCandidateQueryPort.findById(99L)).willReturn(candidate(99L, "TEACHER"))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = SUBMISSION_ID)).isTrue()
    }

    @Test
    fun `개발자도 다운로드할 수 있다`() {
        given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.of(submission(memberId = 7L)))
        given(inquiryAssigneeCandidateQueryPort.findById(99L)).willReturn(candidate(99L, "DEVELOPER"))

        assertThat(checker.canDownload(requesterId = 99L, ownerId = SUBMISSION_ID)).isTrue()
    }

    @Test
    fun `다른 학생은 다운로드할 수 없다`() {
        given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.of(submission(memberId = 7L)))
        given(inquiryAssigneeCandidateQueryPort.findById(8L)).willReturn(candidate(8L, "STUDENT"))

        assertThat(checker.canDownload(requesterId = 8L, ownerId = SUBMISSION_ID)).isFalse()
    }

    @Test
    fun `없는 제출물은 거부한다`() {
        given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.empty())

        assertThat(checker.canDownload(requesterId = 7L, ownerId = SUBMISSION_ID)).isFalse()
    }

    private fun submission(memberId: Long) =
        PortfolioSubmission(requestId = 1L, memberId = memberId, status = PortfolioSubmissionStatus.SUBMITTED)
            .apply { id = SUBMISSION_ID }

    private fun candidate(
        memberId: Long,
        vararg roles: String,
    ) = InquiryAssigneeCandidate(memberId = memberId, name = "회원$memberId", roles = roles.toSet(), status = "ACTIVE")

    private companion object {
        const val SUBMISSION_ID = 100L
    }
}
