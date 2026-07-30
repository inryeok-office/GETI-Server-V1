package team.inreok.getiserver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    private val modules = ApplicationModules.of(GetiServerApplication::class.java)

    @Test
    fun `application modules follow declared boundaries`() {
        modules.verify()
    }

    @Test
    fun `domain 하위 각 Package와 global이 개별 Application Module로 인식된다`() {
        val moduleNames = modules.map { it.identifier.toString() }.toSet()

        assertThat(moduleNames).containsExactlyInAnyOrder(
            "global",
            "domain.member",
            "domain.auth",
            "domain.file",
            "domain.company",
            "domain.job",
            "domain.ai",
            "domain.recommendation",
            "domain.application",
            "domain.program",
            "domain.portfolio",
            "domain.notification",
            "domain.inquiry",
            "domain.collector",
            "domain.operation",
            "domain.audit",
        )
    }
}
