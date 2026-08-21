package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberSelfModificationForbiddenException
import team.inreok.getiserver.domain.member.exception.MemberStatusTransitionNotAllowedException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import java.time.LocalDateTime

/**
 * [MemberAdminManagementServiceImpl]의 Unit Test다(Issue #183). 동시성(Row Lock)과 실제 JPQL
 * Filter 동작은 Mock으로 재현할 수 없어
 * [team.inreok.getiserver.domain.member.MemberAdminManagementIntegrationTest]가 실제
 * PostgreSQL로 검증한다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberAdminManagementServiceImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var memberRoleRepository: MemberRoleRepository

    private val service by lazy { MemberAdminManagementServiceImpl(memberRepository, memberRoleRepository) }

    // Kotlin non-null 파라미터(pageable)에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로
    // Fallback을 둔다(JobApplicationAdminControllerTest.anyPageable과 동일한 관례).
    private fun anyPageable(): Pageable = org.mockito.ArgumentMatchers.any(Pageable::class.java) ?: Pageable.unpaged()

    // status/role/cohort/department는 실제 호출에서 null이 그대로 전달될 수 있는 Nullable
    // Parameter다. `any(Class)`는 null을 매칭에서 제외하므로(Javadoc "excluding nulls") 여기서는
    // null도 매칭하는 무인자 any()를 쓴다.
    private fun anyStatus(): MemberStatus? = org.mockito.ArgumentMatchers.any()

    private fun anyRole(): RoleType? = org.mockito.ArgumentMatchers.any()

    private fun anyDepartment(): DepartmentType? = org.mockito.ArgumentMatchers.any()

    private fun anyCohort(): Int? = org.mockito.ArgumentMatchers.any()

    // ---------- search ----------

    @Test
    fun `이름 검색어는 공백을 제거하고 LIKE Wildcard를 이스케이프해 전달한다`() {
        val page = PageImpl(listOf(memberOf()), PageRequest.of(0, 20), 1)
        given(
            memberRepository.searchForAdmin(true, "50\\%", null, null, null, null, PageRequest.of(0, 20)),
        ).willReturn(page)

        val result = service.search("  50%  ", null, null, null, null, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun `이름 검색어가 공백뿐이면 Filter를 적용하지 않는다`() {
        val page = PageImpl(listOf(memberOf()), PageRequest.of(0, 20), 1)
        given(memberRepository.searchForAdmin(false, "", null, null, null, null, PageRequest.of(0, 20)))
            .willReturn(page)

        val result = service.search("   ", null, null, null, null, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun `목록 항목마다 보유 Role이 채워지고 배치 조회는 한 번만 호출된다`() {
        val members = listOf(memberOf(id = 1L), memberOf(id = 2L))
        val page = PageImpl(members, PageRequest.of(0, 20), 2)
        given(
            memberRepository.searchForAdmin(
                anyBoolean(),
                anyString(),
                anyStatus(),
                anyRole(),
                anyCohort(),
                anyDepartment(),
                anyPageable(),
            ),
        ).willReturn(page)
        given(memberRoleRepository.findAllByIdMemberIdIn(setOf(1L, 2L))).willReturn(
            listOf(
                MemberRole(MemberRoleId(1L, RoleType.STUDENT)),
                MemberRole(MemberRoleId(2L, RoleType.TEACHER)),
                MemberRole(MemberRoleId(2L, RoleType.DEVELOPER)),
            ),
        )

        val result = service.search(null, null, null, null, null, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].roles).containsExactly(RoleType.STUDENT)
        assertThat(result.content[1].roles).containsExactlyInAnyOrder(RoleType.TEACHER, RoleType.DEVELOPER)
        verify(memberRoleRepository).findAllByIdMemberIdIn(setOf(1L, 2L))
    }

    @Test
    fun `조회 결과가 없으면 Role 배치 조회를 호출하지 않는다`() {
        given(
            memberRepository.searchForAdmin(
                anyBoolean(),
                anyString(),
                anyStatus(),
                anyRole(),
                anyCohort(),
                anyDepartment(),
                anyPageable(),
            ),
        ).willReturn(PageImpl(emptyList(), PageRequest.of(0, 20), 0))

        val result = service.search(null, null, null, null, null, PageRequest.of(0, 20))

        assertThat(result.content).isEmpty()
        verify(memberRoleRepository, never()).findAllByIdMemberIdIn(anyCollection())
    }

    // ---------- getDetail ----------

    @Test
    fun `상세 조회 시 회원이 없으면 MemberNotFoundException을 던진다`() {
        given(memberRepository.findById(999L)).willReturn(java.util.Optional.empty())

        assertThatThrownBy { service.getDetail(999L) }.isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `상세 조회는 email GitHub URL 전화번호를 포함한다`() {
        val member =
            memberOf(id = 1L).apply {
                phoneNumber = "010-1234-5678"
                githubUrl = "https://github.com/example"
            }
        given(memberRepository.findById(1L)).willReturn(java.util.Optional.of(member))
        given(
            memberRoleRepository.findAllByIdMemberId(1L),
        ).willReturn(listOf(MemberRole(MemberRoleId(1L, RoleType.STUDENT))))

        val result = service.getDetail(1L)

        assertThat(result.email).isEqualTo(member.email)
        assertThat(result.phoneNumber).isEqualTo("010-1234-5678")
        assertThat(result.githubUrl).isEqualTo("https://github.com/example")
        assertThat(result.roles).containsExactly(RoleType.STUDENT)
    }

    // ---------- updateRoles ----------

    @Test
    fun `자기 자신의 Role을 바꾸려 하면 MemberSelfModificationForbiddenException을 던지고 Repository를 호출하지 않는다`() {
        assertThatThrownBy {
            service.updateRoles(memberId = 1L, requesterMemberId = 1L, roles = setOf(RoleType.TEACHER))
        }.isInstanceOf(MemberSelfModificationForbiddenException::class.java)

        verify(memberRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `대상 회원이 없으면 MemberNotFoundException을 던진다`() {
        given(memberRepository.findByIdForUpdate(999L)).willReturn(null)

        assertThatThrownBy {
            service.updateRoles(memberId = 999L, requesterMemberId = 1L, roles = setOf(RoleType.TEACHER))
        }.isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `Role Set을 교체하면 기존 Role을 모두 지우고 요청받은 집합을 저장한다`() {
        val member = memberOf(id = 2L)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        val result = service.updateRoles(memberId = 2L, requesterMemberId = 1L, roles = setOf(RoleType.TEACHER))

        verify(memberRoleRepository).deleteAllByIdMemberId(2L)
        // MemberRole은 data class가 아니라(JPA @EmbeddedId 관례) 기본 equals가 참조 동일성이라
        // 리스트를 직접 비교할 수 없다. ArgumentCaptor로 실제 저장된 값을 잡아 id(data class)만
        // 비교한다.
        @Suppress("UNCHECKED_CAST")
        val captor =
            org.mockito.ArgumentCaptor.forClass(
                Iterable::class.java,
            ) as org.mockito.ArgumentCaptor<Iterable<MemberRole>>
        verify(memberRoleRepository).saveAll(captor.capture() ?: emptyList())
        assertThat(captor.value.map { it.id }).containsExactly(MemberRoleId(2L, RoleType.TEACHER))
        assertThat(result.roles).containsExactly(RoleType.TEACHER)
    }

    @Test
    fun `빈 Role 집합을 요청하면 삭제만 하고 저장은 호출하지 않는다`() {
        val member = memberOf(id = 2L)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        val result = service.updateRoles(memberId = 2L, requesterMemberId = 1L, roles = emptySet())

        verify(memberRoleRepository).deleteAllByIdMemberId(2L)
        verify(memberRoleRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList<MemberRole>())
        assertThat(result.roles).isEmpty()
    }

    // ---------- updateStatus ----------

    @Test
    fun `자기 자신의 상태를 바꾸려 하면 MemberSelfModificationForbiddenException을 던지고 Repository를 호출하지 않는다`() {
        assertThatThrownBy {
            service.updateStatus(memberId = 1L, requesterMemberId = 1L, status = MemberStatus.SUSPENDED)
        }.isInstanceOf(MemberSelfModificationForbiddenException::class.java)

        verify(memberRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `ACTIVE에서 SUSPENDED로 전이할 수 있다`() {
        val member = memberOf(id = 2L, status = MemberStatus.ACTIVE)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        val result = service.updateStatus(memberId = 2L, requesterMemberId = 1L, status = MemberStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(MemberStatus.SUSPENDED)
        assertThat(member.status).isEqualTo(MemberStatus.SUSPENDED)
    }

    @Test
    fun `SUSPENDED에서 ACTIVE로 전이할 수 있다`() {
        val member = memberOf(id = 2L, status = MemberStatus.SUSPENDED)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        val result = service.updateStatus(memberId = 2L, requesterMemberId = 1L, status = MemberStatus.ACTIVE)

        assertThat(result.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `PENDING에서 ACTIVE로는 전이할 수 없다`() {
        val member = memberOf(id = 2L, status = MemberStatus.PENDING)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        assertThatThrownBy {
            service.updateStatus(memberId = 2L, requesterMemberId = 1L, status = MemberStatus.ACTIVE)
        }.isInstanceOf(MemberStatusTransitionNotAllowedException::class.java)
    }

    @Test
    fun `ACTIVE에서 ACTIVE로는(동일 상태) 전이할 수 없다`() {
        val member = memberOf(id = 2L, status = MemberStatus.ACTIVE)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        assertThatThrownBy {
            service.updateStatus(memberId = 2L, requesterMemberId = 1L, status = MemberStatus.ACTIVE)
        }.isInstanceOf(MemberStatusTransitionNotAllowedException::class.java)
    }

    @Test
    fun `WITHDRAWN에서 SUSPENDED로는 전이할 수 없다`() {
        val member = memberOf(id = 2L, status = MemberStatus.WITHDRAWN)
        given(memberRepository.findByIdForUpdate(2L)).willReturn(member)

        assertThatThrownBy {
            service.updateStatus(memberId = 2L, requesterMemberId = 1L, status = MemberStatus.SUSPENDED)
        }.isInstanceOf(MemberStatusTransitionNotAllowedException::class.java)
    }

    private fun memberOf(
        id: Long = 1L,
        status: MemberStatus = MemberStatus.ACTIVE,
    ) = Member(
        oauthProvider = OAuthProvider.DG,
        oauthSubject = "subject-$id",
        email = "member-$id@example.com",
        status = status,
    ).apply {
        this.id = id
        createdAt = LocalDateTime.of(2026, 8, 1, 0, 0)
        updatedAt = LocalDateTime.of(2026, 8, 1, 0, 0)
    }
}
