package team.inreok.getiserver.domain.member.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.util.Optional

/**
 * Issue #86의 완료 조건 중 "타인의 비공개 프로필 이미지 다운로드가 거부된다"를 검증한다.
 *
 * 이 판정이 다운로드 API(`GET /api/v1/files/{id}/download`)와 목록의 이미지 URL 발급 양쪽에
 * 함께 적용된다(`FileAccessResolver.canAccess`).
 */
@ExtendWith(MockitoExtension::class)
class MemberProfileImageAccessCheckerTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    // 기본값 false(권한 없는 요청자)라 기존 Test는 그대로 통과한다.
    @Mock
    private lateinit var privilegedProfileViewer: PrivilegedProfileViewer

    private val checker: MemberProfileImageAccessChecker by lazy {
        MemberProfileImageAccessChecker(memberRepository, privilegedProfileViewer)
    }

    @Test
    fun `MEMBER 종류의 파일을 판정한다`() {
        assertThat(checker.ownerType).isEqualTo(FileOwnerType.MEMBER)
    }

    @Test
    fun `본인은 비공개 프로필이어도 자기 이미지를 볼 수 있다`() {
        assertThat(checker.canDownload(requesterId = OWNER_ID, ownerId = OWNER_ID)).isTrue()
    }

    @Test
    fun `공개 프로필의 이미지는 다른 회원도 볼 수 있다`() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(memberOf(profilePublic = true)))

        assertThat(checker.canDownload(requesterId = OTHER_ID, ownerId = OWNER_ID)).isTrue()
    }

    @Test
    fun `비공개 프로필의 이미지는 다른 회원이 볼 수 없다`() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(memberOf(profilePublic = false)))

        assertThat(checker.canDownload(requesterId = OTHER_ID, ownerId = OWNER_ID)).isFalse()
    }

    @Test
    fun `교사와 개발자는 비공개 프로필의 이미지를 볼 수 있다`() {
        // 학생 관리·상담 목적의 예외다(Issue #114, #89 결정).
        given(privilegedProfileViewer.canViewPrivateProfile()).willReturn(true)

        assertThat(checker.canDownload(requesterId = OTHER_ID, ownerId = OWNER_ID)).isTrue()
        // 이 판정이 Member 조회보다 앞에 있어야 목록 조회에서 Statement가 늘지 않는다.
        verifyNoInteractions(memberRepository)
    }

    @Test
    fun `회원이 없으면 거부한다`() {
        // 탈퇴 등으로 회원 행이 사라졌는데 파일이 남아 있는 경우다. 판정 근거가 없으면 거부한다.
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.empty())

        assertThat(checker.canDownload(requesterId = OTHER_ID, ownerId = OWNER_ID)).isFalse()
    }

    private fun memberOf(profilePublic: Boolean) =
        Member(
            oauthProvider = OAuthProvider.GOOGLE,
            oauthSubject = "subject-$OWNER_ID",
            email = "student@example.com",
            status = MemberStatus.ACTIVE,
            profilePublic = profilePublic,
        ).apply { id = OWNER_ID }

    private companion object {
        private const val OWNER_ID = 1L
        private const val OTHER_ID = 2L
    }
}
