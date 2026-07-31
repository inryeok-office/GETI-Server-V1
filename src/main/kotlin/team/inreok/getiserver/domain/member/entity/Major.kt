package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "majors",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_majors_name", columnNames = ["name"]),
    ],
)
class Major(
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(nullable = false)
    var active: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
