package team.inreok.getiserver.domain.member.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.member.repository.MemberRepository

/**
 * 프로필 이미지의 다운로드 권한을 판정한다. `ownerId`는 이미지가 연결된 회원의 ID다.
 *
 * 본인은 항상 볼 수 있고, 그 외에는 대상 회원이 프로필을 공개(`members.profile_public`)한
 * 경우에만 허용한다 -- `MemberServiceImpl.toProfileResponse()`가 비공개 프로필의 전공·기술
 * 스택·자기소개를 가리는 것과 같은 기준이다. 이미지만 예외를 두면 프로필을 비공개로 돌린
 * 의미가 없어진다.
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(해당 Interface의 Class 주석 참고). 의존 방향은 `domain.member -> domain.file`이다.
 */
@Component
class MemberProfileImageAccessChecker(
    private val memberRepository: MemberRepository,
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
     */
    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        if (requesterId == ownerId) return true
        // 없는 회원이면 거부한다. 탈퇴 등으로 회원이 사라졌는데 이미지가 남아 있는 경우다.
        return memberRepository.findById(ownerId).map { it.profilePublic }.orElse(false)
    }
}
