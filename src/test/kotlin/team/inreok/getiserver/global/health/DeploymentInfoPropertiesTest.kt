package team.inreok.getiserver.global.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class DeploymentInfoPropertiesTest {
    private val contextRunner = ApplicationContextRunner().withUserConfiguration(TestConfig::class.java)

    @Test
    fun `환경 변수가 없으면 Local 기본값이 적용된다`() {
        contextRunner.run { context ->
            val properties = context.getBean(DeploymentInfoProperties::class.java)

            assertThat(properties.service).isEqualTo("GETI-Server")
            assertThat(properties.version).isEqualTo("0.0.1-SNAPSHOT")
            assertThat(properties.gitSha).isEqualTo("unknown")
            assertThat(properties.buildTime).isEqualTo("unknown")
            assertThat(properties.environment).isEqualTo("local")
        }
    }

    @Test
    fun `Develop 배포 형태의 값이 그대로 Binding된다`() {
        contextRunner
            .withPropertyValues(
                "app.deployment.version=0.0.1-SNAPSHOT",
                "app.deployment.git-sha=a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                "app.deployment.build-time=2026-08-03T12:00:00Z",
                "app.deployment.environment=develop",
            ).run { context ->
                val properties = context.getBean(DeploymentInfoProperties::class.java)

                assertThat(properties.gitSha).isEqualTo("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
                assertThat(properties.buildTime).isEqualTo("2026-08-03T12:00:00Z")
                assertThat(properties.environment).isEqualTo("develop")
            }
    }

    @Configuration
    @EnableConfigurationProperties(DeploymentInfoProperties::class)
    class TestConfig
}
