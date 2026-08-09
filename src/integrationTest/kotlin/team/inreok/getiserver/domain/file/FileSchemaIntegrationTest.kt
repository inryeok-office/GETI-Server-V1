package team.inreok.getiserver.domain.file

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * V17이 `files`에 추가한 제약과 Enum Mapping을 실제 PostgreSQL로 검증한다.
 *
 * `src/test`는 H2를 쓰고 PostgreSQL 전용 구문(Partial Index, CHECK 표현식)을 그대로 검증할 수
 * 없어(docs/development/persistence.md) 이 Test가 필요하다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class FileSchemaIntegrationTest
    @Autowired
    constructor(
        private val entityManager: EntityManager,
        private val fileRepository: StoredFileRepository,
    ) {
        @Test
        fun `업로드 직후 파일은 owner 없이 저장된다`() {
            // V2의 owner_type/owner_id는 NOT NULL이었다. V17이 이를 완화해야만 "먼저 업로드하고
            // 나중에 연결하는" 2단계 흐름의 중간 상태를 표현할 수 있다.
            val saved = fileRepository.saveAndFlush(pendingFile().apply { markUploaded() })

            assertThat(saved.status).isEqualTo(FileStatus.UPLOADED)
            assertThat(saved.ownerType).isNull()
            assertThat(saved.ownerId).isNull()
        }

        @Test
        fun `purpose와 status는 Enum 이름 그대로 문자열로 저장된다`() {
            val saved = fileRepository.saveAndFlush(pendingFile().apply { markUploaded() })
            entityManager.clear()

            @Suppress("UNCHECKED_CAST")
            val row =
                entityManager
                    .createNativeQuery("SELECT purpose, status FROM files WHERE id = :id")
                    .setParameter("id", saved.id)
                    .singleResult as Array<Any>

            assertThat(row[0]).isEqualTo("JOB_APPLICATION")
            assertThat(row[1]).isEqualTo("UPLOADED")
        }

        @Test
        fun `연결하면 owner_type, owner_id, linked_at이 함께 채워진다`() {
            val file = pendingFile().apply { markUploaded() }

            val saved =
                fileRepository.saveAndFlush(
                    file.apply { linkTo(FileOwnerType.JOB_APPLICATION, 42L, LocalDateTime.now()) },
                )

            assertThat(saved.status).isEqualTo(FileStatus.LINKED)
            assertThat(saved.ownerType).isEqualTo(FileOwnerType.JOB_APPLICATION)
            assertThat(saved.ownerId).isEqualTo(42L)
            assertThat(saved.linkedAt).isNotNull()
        }

        @Test
        fun `object_key는 중복될 수 없다`() {
            val key = "JOB_APPLICATION/2026/08/${UUID.randomUUID()}"
            fileRepository.saveAndFlush(pendingFile(objectKey = key))

            // 업로드가 PENDING Row를 먼저 커밋해 Key를 선점하는 구조라 DB가 중복을 막아 준다.
            assertThatThrownBy { fileRepository.saveAndFlush(pendingFile(objectKey = key)) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `미연결 상태에 owner를 채우면 CHECK 제약에 걸린다`() {
            // Entity Method로는 만들 수 없는 조합이라 SQL로 직접 시도한다. Application 계층에
            // 버그가 생겨도 DB가 마지막 방어선이 되는지 확인하는 것이 목적이다.
            val saved = fileRepository.saveAndFlush(pendingFile().apply { markUploaded() })
            entityManager.clear()

            assertThatThrownBy {
                entityManager
                    .createNativeQuery(
                        "UPDATE files SET owner_type = 'JOB_APPLICATION', owner_id = 1 WHERE id = :id",
                    ).setParameter("id", saved.id)
                    .executeUpdate()
            }.hasMessageContaining("ck_files_link_state")
        }

        @Test
        fun `연결 상태인데 owner가 비면 CHECK 제약에 걸린다`() {
            val saved =
                fileRepository.saveAndFlush(
                    pendingFile().apply {
                        markUploaded()
                        linkTo(FileOwnerType.JOB_APPLICATION, 42L, LocalDateTime.now())
                    },
                )
            entityManager.clear()

            assertThatThrownBy {
                entityManager
                    .createNativeQuery("UPDATE files SET owner_type = NULL, owner_id = NULL WHERE id = :id")
                    .setParameter("id", saved.id)
                    .executeUpdate()
            }.hasMessageContaining("ck_files_link_state")
        }

        @Test
        fun `Cleanup 조회용 status, created_at 인덱스가 존재한다`() {
            @Suppress("UNCHECKED_CAST")
            val indexNames =
                entityManager
                    .createNativeQuery("SELECT indexname FROM pg_indexes WHERE tablename = 'files'")
                    .resultList as List<String>

            assertThat(indexNames).contains("idx_files_status_created_at", "idx_files_purpose")
        }

        @Test
        fun `리소스에 연결된 파일을 owner로 조회할 수 있다`() {
            val linked =
                fileRepository.saveAndFlush(
                    pendingFile().apply {
                        markUploaded()
                        linkTo(FileOwnerType.JOB_APPLICATION, 99L, LocalDateTime.now())
                    },
                )
            fileRepository.saveAndFlush(pendingFile().apply { markUploaded() })
            entityManager.clear()

            val found =
                fileRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                    FileOwnerType.JOB_APPLICATION,
                    99L,
                    FileStatus.LINKED,
                )

            assertThat(found.map { it.id }).containsExactly(linked.id)
        }

        @Test
        fun `목적별 연결 개수를 셀 수 있다`() {
            repeat(2) {
                fileRepository.saveAndFlush(
                    pendingFile().apply {
                        markUploaded()
                        linkTo(FileOwnerType.JOB_APPLICATION, 77L, LocalDateTime.now())
                    },
                )
            }
            entityManager.clear()

            val count =
                fileRepository.countByOwnerTypeAndOwnerIdAndPurposeAndStatus(
                    ownerType = FileOwnerType.JOB_APPLICATION,
                    ownerId = 77L,
                    purpose = FilePurpose.JOB_APPLICATION,
                    status = FileStatus.LINKED,
                )

            assertThat(count).isEqualTo(2)
        }

        private fun pendingFile(objectKey: String = "JOB_APPLICATION/2026/08/${UUID.randomUUID()}") =
            StoredFile(
                purpose = FilePurpose.JOB_APPLICATION,
                objectKey = objectKey,
                originalName = "이력서.pdf",
                contentType = "application/pdf",
                sizeBytes = 1024L,
                uploaderMemberId = null,
                extension = "pdf",
            )

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
