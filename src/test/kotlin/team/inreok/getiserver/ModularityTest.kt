package team.inreok.getiserver

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    private val modules = ApplicationModules.of(GetiServerApplication::class.java)

    @Test
    fun `application modules follow declared boundaries`() {
        modules.verify()
    }
}
