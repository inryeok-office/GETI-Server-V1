package team.inreok.getiserver.global.health

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `/actuator/info`의 `deployment` 필드가 노출하는 배포 메타데이터다. CD가 Docker Build/Runtime
 * 시점에 `APP_VERSION`/`APP_GIT_SHA`/`APP_BUILD_TIME`/`APP_ENVIRONMENT`를 주입한다
 * (docs/development/cd.md 참고). `management.info.env.enabled` 같은 무제한 `info.*` 노출 대신
 * 이 Class로 노출 Field를 명시적으로 제한한다. 값이 없는 로컬 실행에서도 기동이 깨지지 않도록
 * 모든 Field에 안전한 기본값을 둔다.
 */
@ConfigurationProperties(prefix = "app.deployment")
data class DeploymentInfoProperties(
    val service: String = "GETI-Server",
    val version: String = "0.0.1-SNAPSHOT",
    val gitSha: String = "unknown",
    val buildTime: String = "unknown",
    val environment: String = "local",
)
