package team.inreok.getiserver.global.openapi

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

/**
 * Springdoc OpenAPI 공통 설정이다. Swagger UI(`/swagger-ui/index.html`)와 OpenAPI JSON
 * (`/v3/api-docs`)은 Springdoc이 이 Class의 Annotation과 각 Controller/DTO의 Swagger
 * Annotation을 조합해 자동 생성한다. Production 노출 여부는 `springdoc.api-docs.enabled`/
 * `springdoc.swagger-ui.enabled` Property로 제어한다(`application-prod.yaml`에서 false로
 * 재정의, docs/ai/openapi-documentation.md 참고).
 */
@OpenAPIDefinition(
    info =
        Info(
            title = "GETI Server API",
            description =
                "GETI 백엔드 API 문서. 인증이 필요한 API는 우측 상단 Authorize 버튼으로 " +
                    "Bearer {accessToken}을 입력하면 Swagger UI에서 바로 호출할 수 있다.",
            version = "v1",
        ),
)
@SecurityScheme(
    name = BEARER_AUTH_SCHEME,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
@Configuration
class OpenApiConfig

const val BEARER_AUTH_SCHEME = "bearerAuth"
