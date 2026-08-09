package team.inreok.getiserver.domain.program.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class ProgramTargetGradeId(
    @Column(name = "program_id")
    val programId: Long,
    @Column(name = "grade")
    val grade: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
