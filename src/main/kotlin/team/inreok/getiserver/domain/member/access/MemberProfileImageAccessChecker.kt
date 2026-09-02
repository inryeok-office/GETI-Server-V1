package team.inreok.getiserver.domain.member.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.member.repository.MemberRepository

/**
 * 프로필 이미지의 다운로드 권한을 판정한다. `ownerId`는 이미지가 연결된 회원의 ID다.
 *
 * 본인과 교사·개발자는 항상 볼 수 있고, 그 외에는 대상 회원이 프로필을 공개
 * (`members.profile_public`)한 경우에만 허용한다 -- `MemberServiceImpl.toProfileResponse()`가
 * 비공개 프로필의 전공·기술 스택·자기소개를 가리는 것과 같은 기준이다. 이미지만 다른 기준을
 * 쓰면 프로필 공개 설정의 의미가 갈라진다.
 *
 * 교사·개발자 예외는 학생 관리·상담 목적이다(Issue #114, #89 결정). 판정은
 * [PrivilegedProfileViewer]가 하며, 상세 프로필 마스킹도 같은 판정을 쓴다.
 *
 * **이 Checker는 [FileOwnerType.MEMBER] 전체를 판정한다.** 현재 회원에 연결되는 파일은
 * `FilePurpose.PROFILE_IMAGE`뿐이라 실질적인 차이가 없지만, 회원에게 다른 목적의 파일(예: 개인
 * 문서)을 붙이는 기능이 생기면 그 파일도 교사·개발자에게 자동으로 열린다. 그 시점에
 * [FileAccessChecker] 계약에 `purpose`를 전달해 예외를 프로필 이미지로 한정해야 한다.
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(해당 Interface의 Class 주석 참고). 의존 방향은 `domain.member -> domain.file`이다.
 */
@Component
class MemberProfileImageAccessChecker(
    private val memberRepository: MemberRepository,
    private val privilegedProfileViewer: PrivilegedProfileViewer,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.MEMBER

    /**
     * 목록 조회에서 이 Method는 파일마다 호출된다. 그래도 회원 수만큼 SELECT가 나가지는 않는데,
     * `MemberSearchServiceImpl.search`가 `@Transactional(readOnly = true)` 안에서 JPQL로 managed
     * `Member`를 이미 읽어 두었고 [MemberRepository.findById]가 같은 영속성 Context의 1차 캐시에서
     * 해결되기 때문이다. `MemberSearchImageUrlQueryCountIntegrationTest`가 실제 Statement 수를
     * 세어 이 사실을 고정한다(회원 2명/6명 모두 2건).
     *
     * **`MemberRepository.search`를 DTO Projection으로 바꾸면 이 전제가 깨진다.** 1차 캐시가 비어
     * 회원 수만큼 SELECT가 늘고 위 Test가 깨지며, 그때가 `FileAccessChecker`에 배치 판정을 도입할
     * 시점이다.
     *
     * 교사·개발자 판정이 [MemberRepository.findById]보다 앞에 있는 것도 Query 수 때문이다. 이
     * 분기를 타면 아래 조회가 아예 나가지 않아 교사·개발자 경로에서 Statement가 늘지 않는다.
     */
    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 본인과 교사·개발자는 Member 조회 없이 통과한다. ||가 왼쪽부터 짧게 끊으므로 여기서
        // 판정이 끝나면 아래 SELECT가 아예 나가지 않는다.
        if (requesterId == ownerId || privilegedProfileViewer.canViewPrivateProfile()) return true
        // 없는 회원이면 거부한다. 탈퇴 등으로 회원이 사라졌는데 이미지가 남아 있는 경우다.
        return memberRepository.findById(ownerId).map { it.profilePublic }.orElse(false)
    }
}
