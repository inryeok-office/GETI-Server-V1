package team.inreok.getiserver.global.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.info.Info

class DeploymentInfoContributorTest {
    @Test
    fun `설정된 배포 메타데이터를 deployment Field로 그대로 노출한다`() {
        val properties =
            DeploymentInfoProperties(
                service = "GETI-Server",
                version = "1.2.3",
                gitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                buildTime = "2026-08-03T12:00:00Z",
                environment = "develop",
            )
        val builder = Info.Builder()

        DeploymentInfoContributor(properties).contribute(builder)

        @Suppress("UNCHECKED_CAST")
        val deployment = builder.build().details["deployment"] as Map<String, String>
        assertThat(deployment)
            .containsEntry("service", "GETI-Server")
            .containsEntry("version", "1.2.3")
            .containsEntry("gitSha", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
            .containsEntry("buildTime", "2026-08-03T12:00:00Z")
            .containsEntry("environment", "develop")
    }
}
