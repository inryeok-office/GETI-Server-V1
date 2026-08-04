package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.external.CompanyExternalImportCommand
import team.inreok.getiserver.domain.company.external.impl.CompanyExternalImportUseCaseImpl
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PR #68 Code Review Finding #3(Medium)을 회귀 방지하기 위한 Test다: 동시에 같은 기업명으로
 * `findOrCreateExternal`을 호출했을 때, 먼저 Commit된 쪽이 이기고 나중 호출은
 * `UnexpectedRollbackException` 없이 먼저 생성된 기업을 재사용해야 한다. `@DataJpaTest`가 Test를
 * Transaction으로 감싸 끝에 Rollback하면 Thread 간 Commit이 서로 보이지 않아 경쟁 조건 자체가
 * 재현되지 않으므로, 이 Test는 `NOT_SUPPORTED`로 그 기본 동작을 끈다(JobQueryIntegrationTest의
 * 동시성 Test와 동일한 이유).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class CompanyExternalImportUseCaseIntegrationTest
    @Autowired
    constructor(
        private val companyRepository: CompanyRepository,
    ) {
        private val useCase = CompanyExternalImportUseCaseImpl(companyRepository)

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        fun `동시에 같은 기업명을 등록해도 예외 없이 하나의 기업으로 수렴한다`() {
            val companyName = "동시생성경쟁기업-${System.nanoTime()}"
            val threads = 8
            val executor = Executors.newFixedThreadPool(threads)

            val results = java.util.Collections.synchronizedList(mutableListOf<Result<Long>>())
            repeat(threads) {
                executor.submit {
                    val result =
                        runCatching {
                            useCase.findOrCreateExternal(CompanyExternalImportCommand(companyName, "MMA")).companyId
                        }
                    results.add(result)
                }
            }
            executor.shutdown()
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue()

            // 어떤 호출도 UnexpectedRollbackException 등으로 실패해서는 안 된다(Finding #3).
            assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() }
            // 모든 호출이 같은 companyId로 수렴해야 한다(중복 Row 생성 없음).
            assertThat(results.map { it.getOrThrow() }.distinct()).hasSize(1)

            val stored = companyRepository.findAll().filter { it.name == companyName }
            assertThat(stored).hasSize(1)
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
