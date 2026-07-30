package team.inreok.getiserver

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

// PostgreSQL 전용 Migration(jsonb, Partial Unique Index 등)은 H2로 검증하지 않는다
// (docs/development/persistence.md). 이 Test는 Spring Context가 정상적으로 뜨는지만
// 확인하는 Smoke Test라 Flyway/Schema Validation은 이 Test Class에서만 비활성화한다.
// 실제 Schema/Migration 검증은 Testcontainers 기반 integrationTest가 담당한다.
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
    ],
)
class GetiServerApplicationTests {
    @Test
    fun contextLoads() {
    }
}
