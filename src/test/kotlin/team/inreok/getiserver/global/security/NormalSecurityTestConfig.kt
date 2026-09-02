package team.inreok.getiserver.global.security

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper

/** Restores the preserved authorization rules for controller contract tests only. */
@TestConfiguration(proxyBeanMethods = false)
class NormalSecurityTestConfig {
    @Bean
    fun securityConfig(objectMapper: ObjectMapper): SecurityConfig = SecurityConfig(objectMapper)

    @Bean
    fun jwtAuthenticationFilter(jwtTokenProvider: JwtTokenProvider): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtTokenProvider)

    @Bean
    @Order(1)
    fun normalSecurityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        securityConfig: SecurityConfig,
    ): SecurityFilterChain = securityConfig.normalSecurityFilterChainForTest(http, jwtAuthenticationFilter)
}
