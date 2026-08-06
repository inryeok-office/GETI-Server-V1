package team.inreok.getiserver.domain.program.entity

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

// member_majors/MemberMajor와 동일한 관례(관계 자체에 속성이 없는 단순 다대다이지만 EmbeddedId
// Join Entity로 통일)를 따른다.
@Entity
@Table(name = "program_target_grades")
class ProgramTargetGrade(
    @EmbeddedId
    val id: ProgramTargetGradeId,
)
