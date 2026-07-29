package team.inreok.geti.getiserver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ApplicationProfileConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())

    @Test
    fun `local profile enables debug logging for application package`() {
        contextRunner
            .withPropertyValues("spring.profiles.active=local")
            .run { context ->
                assertThat(context.environment.getProperty("logging.level.team.inreok.geti.getiserver"))
                    .isEqualTo("DEBUG")
            }
    }

    @Test
    fun `default profile does not enable debug logging`() {
        contextRunner
            .run { context ->
                assertThat(context.environment.getProperty("logging.level.team.inreok.geti.getiserver"))
                    .isNull()
            }
    }
}
