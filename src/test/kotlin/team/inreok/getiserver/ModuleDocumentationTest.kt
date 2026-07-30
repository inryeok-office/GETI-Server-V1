package team.inreok.getiserver

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModuleDocumentationTest {
    private val modules = ApplicationModules.of(GetiServerApplication::class.java)

    @Test
    fun `generate module documentation`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeModuleCanvases()
    }
}
