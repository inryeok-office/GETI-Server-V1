package team.inreok.getiserver.domain.member.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileUploaderAccessor
import team.inreok.getiserver.domain.file.access.FileUploaderSnapshot
import team.inreok.getiserver.domain.member.repository.MemberRepository

/**
 * 관리자 공통 파일 목록(Issue #225)이 쓰는 업로더 이름 배치 조회 구현이다.
 *
 * `FileUploaderAccessor`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(해당 Interface의 Class 주석 참고). 의존 방향은 `domain.member -> domain.file`이다
 * (기존 `MemberProfileImageAccessChecker`/`MemberProfileImageServiceImpl`이 이미 갖고 있던 방향과
 * 같다).
 *
 * `MemberRepository.findAllById`(Spring Data가 기본 제공)로 한 번에 배치 조회해 목록 Row 수만큼
 * Query가 늘어나지 않게 한다(N+1 방지).
 */
@Component
class MemberFileUploaderAccessor(
    private val memberRepository: MemberRepository,
) : FileUploaderAccessor {
    override fun findAllByIds(memberIds: Set<Long>): Map<Long, FileUploaderSnapshot> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository
            .findAllById(memberIds)
            .associate { member ->
                val id = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
                id to FileUploaderSnapshot(memberId = id, name = member.name)
            }
    }
}
