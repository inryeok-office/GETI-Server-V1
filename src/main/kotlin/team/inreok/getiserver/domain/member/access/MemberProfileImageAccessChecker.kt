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

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        if (requesterId == ownerId) return true
        // 없는 회원이면 거부한다. 탈퇴 등으로 회원이 사라졌는데 이미지가 남아 있는 경우다.
        return memberRepository.findById(ownerId).map { it.profilePublic }.orElse(false)
    }
}
