package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.member.access.PrivilegedProfileViewer
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberProfileLink
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.repository.MemberProfileLinkRepository
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.impl.MemberProfileImageServiceImpl
import team.inreok.getiserver.domain.member.service.impl.MemberServiceImpl
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

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    // 기본값 false(권한 없는 요청자)라 기존 Test는 그대로 통과한다. 교사·개발자 경로만 명시적으로
    // 스텁한다.
    @Mock
    private lateinit var privilegedProfileViewer: PrivilegedProfileViewer

    @Mock
    private lateinit var memberProfileLinkRepository: MemberProfileLinkRepository

    @Captor
    private lateinit var linksCaptor: ArgumentCaptor<List<MemberProfileLink>>

    private val service: MemberService by lazy {
        // 프로필 이미지 Service는 Mock이 아니라 실제 구현을 쓴다. 이 Class의 Test가 검증하려는
        // 것은 연결/해제가 실제로 어떤 순서로 일어나는가이고, Mock으로 대체하면 그 동작이 사라진다.
        MemberServiceImpl(
            memberRepository,
            memberRoleRepository,
            memberSelectionQueryService,
            MemberProfileImageServiceImpl(fileLinkPort, fileUrlPort),
            privilegedProfileViewer,
            memberProfileLinkRepository,
            JsonMapper(),
        )
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

        val result = service.getProfile(1L, REQUESTER_ID)

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

        val result = service.getProfile(2L, REQUESTER_ID)

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

        val result = service.getProfile(8L, REQUESTER_ID)

        assertThat(result.majors).isEmpty()
        assertThat(result.techStacks).isEmpty()
        assertThat(result.desiredJob).isNull()
        assertThat(result.bio).isNull()
        assertThat(result.profileRestricted).isTrue()
        verify(memberSelectionQueryService, never()).getMajorNames(8L)
        verify(memberSelectionQueryService, never()).getTechStackNames(8L)
    }

    @Test
    fun `교사와 개발자는 비공개 프로필의 전공-기술스택-희망직무-자기소개를 조회한다`() {
        // 학생 관리·상담 목적으로 비공개 프로필도 전체를 본다(Issue #114, #89 결정).
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-9",
                email = "student9@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = false,
            ).apply {
                id = 9L
                name = "홍길동"
                desiredPositions = """["Backend Developer"]"""
                introduction = "비공개 자기소개"
            }
        given(memberRepository.findById(9L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(9L)).willReturn(studentRole(9L))
        given(memberSelectionQueryService.getMajorNames(9L)).willReturn(listOf("소프트웨어"))
        given(memberSelectionQueryService.getTechStackNames(9L)).willReturn(listOf("Kotlin"))
        given(privilegedProfileViewer.canViewPrivateProfile()).willReturn(true)

        val result = service.getProfile(9L, REQUESTER_ID)

        assertThat(result.majors).containsExactly("소프트웨어")
        assertThat(result.techStacks).containsExactly("Kotlin")
        assertThat(result.desiredJob).isEqualTo("Backend Developer")
        assertThat(result.bio).isEqualTo("비공개 자기소개")
        // isPublic은 대상의 설정값이라 false 그대로다. 가려지지 않았다는 사실은 profileRestricted가
        // 나타낸다.
        assertThat(result.isPublic).isFalse()
        assertThat(result.profileRestricted).isFalse()
    }

    @Test
    fun `교사가 조회해도 대상이 학생이 아니면 MemberNotFoundException을 던진다`() {
        // 이 API는 학생 프로필 조회 용도라는 기존 규칙은 Role 개방과 무관하게 유지된다.
        val teacher =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-10",
                email = "teacher10@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = true,
            ).apply { id = 10L }
        given(memberRepository.findById(10L)).willReturn(Optional.of(teacher))
        given(memberRoleRepository.findAllByIdMemberId(10L)).willReturn(
            listOf(MemberRole(MemberRoleId(memberId = 10L, role = RoleType.TEACHER))),
        )

        assertThatThrownBy { service.getProfile(10L, REQUESTER_ID) }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `존재하지 않는 회원을 조회하면 MemberNotFoundException을 던진다`() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getProfile(999L, REQUESTER_ID) }
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

        assertThatThrownBy { service.getProfile(9L, REQUESTER_ID) }
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
    fun `cohort를 저장하고 응답에 반환한다`() {
        val member = newMember(5L).apply { cohort = 9 }
        given(memberRepository.findById(5L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"cohort":10}""")

        val result = service.updateProfile(5L, body)

        assertThat(member.cohort).isEqualTo(10)
        assertThat(result.cohort).isEqualTo(10)
    }

    @Test
    fun `cohort에 null을 보내면 값을 지운다`() {
        val member = newMember(6L).apply { cohort = 9 }
        given(memberRepository.findById(6L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"cohort":null}""")

        val result = service.updateProfile(6L, body)

        assertThat(member.cohort).isNull()
        assertThat(result.cohort).isNull()
    }

    @Test
    fun `cohort가 정수가 아니면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(7L)
        given(memberRepository.findById(7L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"cohort":"10"}""")

        assertThatThrownBy { service.updateProfile(7L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
            .hasMessage("cohort는 정수여야 합니다.")
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

    @Test
    fun `프로필 이미지 File ID를 보내면 본인 프로필에 연결한다`() {
        val member = newMember(20L)
        given(memberRepository.findById(20L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"profileImageFileId":$FILE_ID}""")

        service.updateProfile(20L, body)

        // 요청자와 소유자가 모두 본인이다. 남의 파일을 붙이지 못하게 막는 것은
        // FileLinkPort.validateAndLink의 책임이라 여기서 다시 검사하지 않는다.
        verify(fileLinkPort).validateAndLink(20L, listOf(FILE_ID), FilePurpose.PROFILE_IMAGE, 20L)
        assertThat(member.profileImageFileId).isEqualTo(FILE_ID)
    }

    @Test
    fun `이미지를 교체하면 기존 연결을 먼저 해제한다`() {
        val member = newMember(21L).apply { profileImageFileId = OLD_FILE_ID }
        given(memberRepository.findById(21L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"profileImageFileId":$FILE_ID}""")

        service.updateProfile(21L, body)

        // 해제가 먼저여야 한다. unlinkAllOf는 이 회원에게 연결된 파일을 모두 풀기 때문에,
        // 순서가 뒤바뀌면 방금 붙인 새 이미지까지 함께 풀려 프로필 이미지가 사라진다.
        val ordered = inOrder(fileLinkPort)
        ordered.verify(fileLinkPort).unlinkAllOf(FileOwnerType.MEMBER, 21L)
        ordered.verify(fileLinkPort).validateAndLink(21L, listOf(FILE_ID), FilePurpose.PROFILE_IMAGE, 21L)
        assertThat(member.profileImageFileId).isEqualTo(FILE_ID)
    }

    @Test
    fun `null을 보내면 이미지를 제거하고 연결만 해제한다`() {
        val member = newMember(22L).apply { profileImageFileId = OLD_FILE_ID }
        given(memberRepository.findById(22L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"profileImageFileId":null}""")

        service.updateProfile(22L, body)

        verify(fileLinkPort).unlinkAllOf(FileOwnerType.MEMBER, 22L)
        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
        assertThat(member.profileImageFileId).isNull()
    }

    @Test
    fun `같은 File ID를 다시 보내면 연결을 건드리지 않는다`() {
        // 다른 Field만 바꾸면서 현재 이미지를 그대로 다시 보내는 것이 흔한 요청이다. 이때
        // 해제 후 재연결하면 멀쩡한 연결이 잠시 풀리고 불필요한 Update가 발생한다.
        val member = newMember(23L).apply { profileImageFileId = FILE_ID }
        given(memberRepository.findById(23L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"profileImageFileId":$FILE_ID}""")

        service.updateProfile(23L, body)

        verifyNoInteractions(fileLinkPort)
        assertThat(member.profileImageFileId).isEqualTo(FILE_ID)
    }

    @Test
    fun `profileImageFileId가 정수가 아니면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        given(memberRepository.findById(24L)).willReturn(Optional.of(newMember(24L)))
        val body = JsonMapper().readTree("""{"profileImageFileId":"42"}""")

        assertThatThrownBy { service.updateProfile(24L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `프로필 이미지가 있으면 조회 응답에 Presigned URL이 담긴다`() {
        val member = newMember(25L).apply { profileImageFileId = FILE_ID }
        given(memberRepository.findById(25L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(25L)).willReturn(studentRole(25L))
        given(memberSelectionQueryService.getMajorNames(25L)).willReturn(emptyList())
        given(memberSelectionQueryService.getTechStackNames(25L)).willReturn(emptyList())
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(FILE_ID)))
            .willReturn(mapOf(FILE_ID to IMAGE_URL))

        val result = service.getProfile(25L, REQUESTER_ID)

        assertThat(result.profileImageUrl).isEqualTo(IMAGE_URL)
    }

    @Test
    fun `볼 권한이 없으면 프로필 이미지 URL은 null이다`() {
        // 비공개 프로필 판정은 MemberProfileImageAccessChecker가 하고, FileUrlPort는 접근할 수
        // 없는 파일을 결과에서 빼고 예외를 던지지 않는다. 여기서는 그 결과를 그대로 전달하는지만 본다.
        val member = newMember(26L).apply { profileImageFileId = FILE_ID }
        given(memberRepository.findById(26L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(26L)).willReturn(studentRole(26L))
        given(memberSelectionQueryService.getMajorNames(26L)).willReturn(emptyList())
        given(memberSelectionQueryService.getTechStackNames(26L)).willReturn(emptyList())
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(FILE_ID))).willReturn(emptyMap())

        assertThat(service.getProfile(26L, REQUESTER_ID).profileImageUrl).isNull()
    }

    @Test
    fun `links를 배열 순서대로 전체 교체하고 표시 순서로 응답한다`() {
        val member = newMember(30L)
        given(memberRepository.findById(30L)).willReturn(Optional.of(member))
        given(memberProfileLinkRepository.findAllByMemberIdOrderByDisplayOrderAsc(30L)).willReturn(
            listOf(
                MemberProfileLink(memberId = 30L, label = "블로그", url = "https://blog.example.com", displayOrder = 0),
                MemberProfileLink(
                    memberId = 30L,
                    label = "포트폴리오",
                    url = "https://portfolio.example.com",
                    displayOrder = 1,
                ),
            ),
        )
        val body =
            JsonMapper().readTree(
                """{"links":[{"label":"블로그","url":"https://blog.example.com"},
                    |{"label":"포트폴리오","url":"https://portfolio.example.com"}]}
                """.trimMargin().replace("\n", ""),
            )

        val result = service.updateProfile(30L, body)

        verify(memberProfileLinkRepository).deleteAllByMemberId(30L)
        verify(memberProfileLinkRepository).saveAll(linksCaptor.capture())
        assertThat(linksCaptor.value.map { it.label }).containsExactly("블로그", "포트폴리오")
        assertThat(linksCaptor.value.map { it.displayOrder }).containsExactly(0, 1)
        assertThat(result.links.map { it.label }).containsExactly("블로그", "포트폴리오")
    }

    @Test
    fun `links 필드를 보내지 않으면 기존 링크를 그대로 유지한다`() {
        val member = newMember(31L)
        given(memberRepository.findById(31L)).willReturn(Optional.of(member))
        given(memberProfileLinkRepository.findAllByMemberIdOrderByDisplayOrderAsc(31L)).willReturn(
            listOf(
                MemberProfileLink(memberId = 31L, label = "블로그", url = "https://blog.example.com", displayOrder = 0),
            ),
        )
        val body = JsonMapper().readTree("""{"bio":"안녕"}""")

        val result = service.updateProfile(31L, body)

        verify(memberProfileLinkRepository, never()).deleteAllByMemberId(anyLong())
        verify(memberProfileLinkRepository, never()).saveAll(anyList<MemberProfileLink>())
        assertThat(result.links.map { it.label }).containsExactly("블로그")
    }

    @Test
    fun `links에 빈 배열을 보내면 전체 삭제한다`() {
        val member = newMember(32L)
        given(memberRepository.findById(32L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":[]}""")

        service.updateProfile(32L, body)

        verify(memberProfileLinkRepository).deleteAllByMemberId(32L)
        verify(memberProfileLinkRepository).saveAll(linksCaptor.capture())
        assertThat(linksCaptor.value).isEmpty()
    }

    @Test
    fun `links를 수정해도 githubUrl은 그대로 유지된다`() {
        val member = newMember(33L).apply { githubUrl = "https://github.com/example" }
        given(memberRepository.findById(33L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":[]}""")

        val result = service.updateProfile(33L, body)

        assertThat(result.githubUrl).isEqualTo("https://github.com/example")
    }

    @Test
    fun `links를 null로 보내면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(34L)
        given(memberRepository.findById(34L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":null}""")

        assertThatThrownBy { service.updateProfile(34L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links가 배열이 아니면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(35L)
        given(memberRepository.findById(35L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":"https://example.com"}""")

        assertThatThrownBy { service.updateProfile(35L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links 개수가 상한을 넘으면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(36L)
        given(memberRepository.findById(36L)).willReturn(Optional.of(member))
        val tooMany =
            (1..21).joinToString(",") { """{"label":"링크$it","url":"https://example.com/$it"}""" }
        val body = JsonMapper().readTree("""{"links":[$tooMany]}""")

        assertThatThrownBy { service.updateProfile(36L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links의 url이 http-https가 아니면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(37L)
        given(memberRepository.findById(37L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":[{"label":"위험","url":"javascript:alert(1)"}]}""")

        assertThatThrownBy { service.updateProfile(37L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links의 url에 Scheme이 없으면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(38L)
        given(memberRepository.findById(38L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":[{"label":"상대경로","url":"/relative/path"}]}""")

        assertThatThrownBy { service.updateProfile(38L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links의 label이 빈 값이면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(39L)
        given(memberRepository.findById(39L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":[{"label":"","url":"https://example.com"}]}""")

        assertThatThrownBy { service.updateProfile(39L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links의 label이 최대 길이를 넘으면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(40L)
        given(memberRepository.findById(40L)).willReturn(Optional.of(member))
        val longLabel = "a".repeat(101)
        val body = JsonMapper().readTree("""{"links":[{"label":"$longLabel","url":"https://example.com"}]}""")

        assertThatThrownBy { service.updateProfile(40L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `links 원소가 객체가 아니면 PROFILE_VALIDATION_FAILED 예외를 던진다`() {
        val member = newMember(41L)
        given(memberRepository.findById(41L)).willReturn(Optional.of(member))
        val body = JsonMapper().readTree("""{"links":["https://example.com"]}""")

        assertThatThrownBy { service.updateProfile(41L, body) }
            .isInstanceOf(MemberProfileValidationException::class.java)
    }

    @Test
    fun `내 프로필 조회는 links를 항상 포함한다`() {
        val member = newMember(42L)
        given(memberRepository.findById(42L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(42L)).willReturn(studentRole(42L))
        given(memberProfileLinkRepository.findAllByMemberIdOrderByDisplayOrderAsc(42L)).willReturn(
            listOf(
                MemberProfileLink(memberId = 42L, label = "블로그", url = "https://blog.example.com", displayOrder = 0),
            ),
        )

        val result = service.getMyProfile(42L)

        assertThat(result.links.map { it.label }).containsExactly("블로그")
    }

    @Test
    fun `비공개 프로필은 links도 노출하지 않는다`() {
        val member =
            Member(
                oauthProvider = OAuthProvider.GOOGLE,
                oauthSubject = "subject-43",
                email = "student43@example.com",
                status = MemberStatus.ACTIVE,
                profilePublic = false,
            ).apply { id = 43L }
        given(memberRepository.findById(43L)).willReturn(Optional.of(member))
        given(memberRoleRepository.findAllByIdMemberId(43L)).willReturn(studentRole(43L))

        val result = service.getProfile(43L, REQUESTER_ID)

        assertThat(result.links).isEmpty()
        verify(memberProfileLinkRepository, never()).findAllByMemberIdOrderByDisplayOrderAsc(43L)
    }

    // Mockito Matcher는 null을 돌려주는데 Kotlin non-null Parameter라 NPE가 난다. 실제로는
    // never() 검증에만 쓰여 반환값이 사용되지 않지만, Elvis로 기본값을 채워 둔다.
    private fun anyPurpose(): FilePurpose = any(FilePurpose::class.java) ?: FilePurpose.PROFILE_IMAGE

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

    private companion object {
        /** 프로필을 조회하는 다른 회원. 대상 회원(1L, 2L 등)과 겹치지 않는 값을 쓴다. */
        private const val REQUESTER_ID = 100L
        private const val FILE_ID = 42L
        private const val OLD_FILE_ID = 41L
        private const val IMAGE_URL = "https://storage.example/profile?signature=test"
    }
}
