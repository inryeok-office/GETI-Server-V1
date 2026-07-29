package team.inreok.getiserver.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Persistence 통합 테스트 전용 Entity다. 실제 GETI Domain을 나타내지 않으며
 * PostgreSQL Testcontainers에서 Flyway로 생성한 Schema와 JPA Mapping을 검증하는 데만 쓰인다.
 */
@Entity
@Table(name = "persistence_probe")
class PersistenceProbeEntity(
    @Column(nullable = false)
    val label: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
