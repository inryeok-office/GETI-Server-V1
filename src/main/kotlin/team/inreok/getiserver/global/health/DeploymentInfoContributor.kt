package team.inreok.getiserver.global.health

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * `GET /actuator/info`에 `deployment` 필드를 추가한다. Secret, DB/Redis 연결 정보, 환경변수
 * 전체 목록 등 민감한 값은 이 Contributor가 다루는 5개 Field(service/version/gitSha/buildTime/
 * environment) 밖에 있으므로 노출되지 않는다.
 */
@Component
@EnableConfigurationProperties(DeploymentInfoProperties::class)
class DeploymentInfoContributor(
    private val deploymentInfoProperties: DeploymentInfoProperties,
) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "deployment",
            mapOf(
                "service" to deploymentInfoProperties.service,
                "version" to deploymentInfoProperties.version,
                "gitSha" to deploymentInfoProperties.gitSha,
                "buildTime" to deploymentInfoProperties.buildTime,
                "environment" to deploymentInfoProperties.environment,
            ),
        )
    }
}
