package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

// 관계 자체에 별도 속성이 없어 @ManyToMany + @JoinTable로 단순화할 수도 있다(코드 리뷰 Minor
// 확인 완료, 유지로 결정). 같은 Domain의 MemberRole/MemberRoleId가 이미 동일한 EmbeddedId 관례를
// 쓰고 있어 일관성을 우선했고, member_majors Table/FK/삭제 정책(deleteAllByIdMemberId + saveAll로
// 전체 교체)을 그대로 유지할 수 있다. 향후 선택 시각·우선순위 등 관계 속성이 필요해지면 이 구조가
// 자연스럽게 확장 가능하다는 점도 고려했다.
@Entity
@Table(name = "member_majors")
class MemberMajor(
    @EmbeddedId
    val id: MemberMajorId,
)
