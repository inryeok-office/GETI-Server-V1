package team.inreok.getiserver.global.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer

/**
 * Notion API 명세서(공통 목록 조회 규칙)가 확정한 최대 Page Size(100)를 서버에서 강제한다.
 * 요청한 size가 100을 넘으면 Spring Data가 자동으로 100으로 잘라낸다.
 */
@Configuration
class WebPageableConfig {
    @Bean
    fun pageableCustomizer(): PageableHandlerMethodArgumentResolverCustomizer =
        PageableHandlerMethodArgumentResolverCustomizer { resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE) }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
