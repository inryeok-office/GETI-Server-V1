package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MemberServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var memberRoleRepository: MemberRoleRepository

    @Mock
    private lateinit var memberSelectionQueryService: MemberSelectionQueryService

    private val service by lazy {
        MemberService(memberRepository, memberRoleRepository, memberSelectionQueryService, JsonMapper())
    }

    @Test
    fun `Member Entity를 프로필 응답으로 변환한다`() {
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-1",
                email = "student@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = true,
            ).apply {
                id = 1L
                name = "홍길동"
                cohort = 3
                department = DepartmentType.SW_DEVELOPMENT
                desiredPositions = """["Backend Developer","Platform Engineer"]"""
                introduction = "안녕하세요"
            }
        given(memberRepository.findById(1L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(1L)).willReturn(studentRole(1L))
        given(memberSelectionQueryService.getMajorNames(1L)).willReturn(listOf("소프트웨어"))
        given(memberSelectionQueryService.getTechStackNames(1L)).willReturn(listOf("Kotlin", "Spring Boot"))

        val result = service.getProfile(1L)

        assertThat(result.memberId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("홍길동")
        assertThat(result.cohort).isEqualTo(3)
        assertThat(result.department).isEqualTo(DepartmentType.SW_DEVELOPMENT)
        assertThat(result.majors).containsExactly("소프트웨어")
        assertThat(result.techStacks).containsExactly("Kotlin", "Spring Boot")
        assertThat(result.desiredJob).isEqualTo("Backend Developer")
        assertThat(result.bio).isEqualTo("안녕하세요")
        assertThat(result.isPublic).isTrue()
        assertThat(result.profileRestricted).isFalse()
    }

    @Test
    fun `선택한 전공과 기술 스택이 없으면 빈 목록과 null로 채운다`() {
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-2",
                email = "student2@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = true,
            ).apply { id = 2L }
        given(memberRepository.findById(2L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(2L)).willReturn(studentRole(2L))
        given(memberSelectionQueryService.getMajorNames(2L)).willReturn(emptyList())
        given(memberSelectionQueryService.getTechStackNames(2L)).willReturn(emptyList())

        val result = service.getProfile(2L)

        assertThat(result.majors).isEmpty()
        assertThat(result.techStacks).isEmpty()
        assertThat(result.desiredJob).isNull()
        assertThat(result.profileImageUrl).isNull()
        assertThat(result.isPublic).isTrue()
        assertThat(result.profileRestricted).isFalse()
    }

    @Test
    fun `비공개 프로필은 실제 값이 있어도 전공-기술스택-희망직무-자기소개를 노출하지 않는다`() {
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-8",
                email = "student8@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = false,
            ).apply {
                id = 8L
                name = "홍길동"
                desiredPositions = """["Backend Developer"]"""
                introduction = "비공개 자기소개"
            }
        given(memberRepository.findById(8L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(8L)).willReturn(studentRole(8L))

        val result = service.getProfile(8L)

        assertThat(result.majors).isEmpty()
        assertThat(result.techStacks).isEmpty()
        assertThat(result.desiredJob).isNull()
        assertThat(result.bio).isNull()
        assertThat(result.profileRestricted).isTrue()
        verify(memberSelectionQueryService, never()).getMajorNames(8L)
        verify(memberSelectionQueryService, never()).getTechStackNames(8L)
    }

    @Test
    fun `존재하지 않는 회원을 조회하면 MemberNotFoundException을 던진다`() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getProfile(999L) }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `조회 대상이 STUDENT Role이 아니면 MemberNotFoundException을 던진다`() {
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-9",
                email = "teacher9@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = true,
            ).apply { id = 9L }
        given(memberRepository.findById(9L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(9L))
            .willReturn(listOf(MemberRole(MemberRoleId(9L, RoleType.TEACHER))))

        assertThatThrownBy { service.getProfile(9L) }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `요청에 포함된 Field만 수정하고 나머지는 유지한다`() {
        val member = newMember(3L).apply { phoneNumber = "010-0000-0000" }
        given(memberRepository.findById(3L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"bio":"새 소개"}""")

        val result = service.updateProfile(3L, body)

        assertThat(result.bio).isEqualTo("새 소개")
        assertThat(result.phone).isEqualTo("010-0000-0000")
        verify(memberRepository).flush()
    }

    @Test
    fun `PATCH Body에 알 수 없는 Field가 있으면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val body = JsonMapper().readTree("""{"nickname":"별명"}""")

        assertThatThrownBy { service.updateProfile(10L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
        verify(memberRepository, never()).findById(10L)
    }

    @Test
    fun `profileImageUrl을 요청에 포함하면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val body = JsonMapper().readTree("""{"profileImageUrl":"https://example.com/a.png"}""")

        assertThatThrownBy { service.updateProfile(11L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
        verify(memberRepository, never()).findById(11L)
    }

    @Test
    fun `학생은 isPublic을 false로 설정할 수 있다`() {
        val member = newMember(12L)
        given(memberRepository.findById(12L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(12L)).willReturn(studentRole(12L))
        val body = JsonMapper().readTree("""{"isPublic":false}""")

        val result = service.updateProfile(12L, body)

        assertThat(result.isPublic).isFalse()
    }

    @Test
    fun `교사-개발자는 isPublic을 false로 설정할 수 없다`() {
        val member = newMember(13L)
        given(memberRepository.findById(13L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(13L))
            .willReturn(listOf(MemberRole(MemberRoleId(13L, RoleType.TEACHER))))
        val body = JsonMapper().readTree("""{"isPublic":false}""")

        assertThatThrownBy { service.updateProfile(13L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `Field를 명시적으로 null로 보내면 값을 지운다`() {
        val member = newMember(4L).apply { phoneNumber = "010-0000-0000" }
        given(memberRepository.findById(4L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"phone":null}""")

        val result = service.updateProfile(4L, body)

        assertThat(result.phone).isNull()
    }

    @Test
    fun `desiredJob을 수정하면 desiredPositions의 첫 값으로 저장된다`() {
        val member = newMember(5L)
        given(memberRepository.findById(5L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"desiredJob":"Backend Developer"}""")

        val result = service.updateProfile(5L, body)

        assertThat(result.desiredJob).isEqualTo("Backend Developer")
    }

    @Test
    fun `department에 잘못된 값을 보내면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(6L)
        given(memberRepository.findById(6L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"department":"NOT_A_DEPARTMENT"}""")

        assertThatThrownBy { service.updateProfile(6L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `isPublic을 null로 보내면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(7L)
        given(memberRepository.findById(7L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"isPublic":null}""")

        assertThatThrownBy { service.updateProfile(7L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `존재하지 않는 회원을 수정하면 MemberProfileNotFoundException을 던진다`() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty())
        val body = JsonMapper().readTree("""{"bio":"x"}""")

        assertThatThrownBy { service.updateProfile(999L, body) }
            .isInstanceOf(MemberProfileNotFoundException::class.java)
    }

    private fun studentRole(memberId: Long): List<MemberRole> =
        listOf(MemberRole(MemberRoleId(memberId, RoleType.STUDENT)))

    private fun newMember(memberId: Long): Member =
        Member(
            oauthProvider = OAuthProvider.GOOGLE,
            oauthSubject = "subject-$memberId",
            email = "student$memberId@example.com",
            status = MemberStatus.ACTIVE,
            profilePublic = true,
        ).apply {
            id = memberId
            name = "홍길동"
            updatedAt = LocalDateTime.now()
        }
}
