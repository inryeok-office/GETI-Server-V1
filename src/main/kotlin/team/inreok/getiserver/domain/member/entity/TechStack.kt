package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

@Entity
@Table(
    name = "tech_stacks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tech_stacks_name", columnNames = ["name"]),
    ],
)
class TechStack(
    @Column(nullable = false, length = 100)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: TechStackCategory,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
