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
        // src/test/resources/application.yaml이 src/main/resources/application.yaml을 대체하고
        // (같은 파일 이름이라 Test Classpath가 우선), app.jwt.secret은 application-local.yaml/
        // application-prod.yaml에만 있어(Profile별 실제 값이 필요) 이 Test는 Profile을 활성화하지
        // 않으므로, JwtProperties Binding이 실패하지 않도록 세 값을 모두 채운다(실제 서명 검증/
        // 만료 시간 자체가 이 Test의 관심사가 아니다).
        "app.jwt.secret=smoke-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
    ],
)
class GetiServerApplicationTests {
    @Test
    fun contextLoads() {
    }
}
