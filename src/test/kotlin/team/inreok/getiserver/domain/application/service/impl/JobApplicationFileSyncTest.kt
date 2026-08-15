package team.inreok.getiserver.domain.application.service.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import tools.jackson.databind.json.JsonMapper

/**
 * `syncApplicationFiles`(Issue #134)만 따로 검증한다. `JobApplicationServiceImplTest`가 이미
 * `executeAction` 전체를 검증하므로, Service를 다시 세팅하지 않고 이 순수 함수를 직접 호출해 유지·
 * 추가·제거 Diff 로직만 집중적으로 확인한다(detekt LargeClass 회피 목적도 있다).
 */
@ExtendWith(MockitoExtension::class)
class JobApplicationFileSyncTest {
    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    private val jsonMapper = JsonMapper()

    private fun applicationOf(
        answers: List<ApplicationAnswer>,
        id: Long = 1L,
    ) = JobApplication(
        jobId = 1L,
        applicantMemberId = 1L,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = jsonMapper.writeValueAsString(answers),
    ).apply { this.id = id }

    private fun fileSnapshotOf(fileId: Long) =
        FileSnapshot(fileId = fileId, originalName = "file-$fileId.pdf", contentType = "application/pdf", size = 100)

    @Test
    fun `새로 참조한 fileId를 연결하고 기존 연결이 없으면 해제하지 않는다`() {
        val application =
            applicationOf(
                listOf(
                    ApplicationAnswer(fieldId = "motivation", value = jsonMapper.readTree("\"답변\"")),
                    ApplicationAnswer(fieldId = "resume", value = null, fileIds = listOf(1L, 2L)),
                ),
            )
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())

        syncApplicationFiles(fileLinkPort, jsonMapper, application, requesterId = 1L)

        verify(fileLinkPort).validateAndLink(
            requesterId = 1L,
            fileIds = listOf(1L, 2L),
            purpose = FilePurpose.JOB_APPLICATION,
            ownerId = 1L,
        )
        verify(fileLinkPort, never()).unlinkAllOf(FileOwnerType.JOB_APPLICATION, 1L)
    }

    @Test
    fun `원하는 fileId 집합이 이미 연결된 집합과 같으면 File 도메인을 다시 호출하지 않는다`() {
        val application =
            applicationOf(listOf(ApplicationAnswer(fieldId = "resume", value = null, fileIds = listOf(1L))))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(listOf(fileSnapshotOf(1L)))

        syncApplicationFiles(fileLinkPort, jsonMapper, application, requesterId = 1L)

        verify(fileLinkPort, never()).unlinkAllOf(FileOwnerType.JOB_APPLICATION, 1L)
        verify(fileLinkPort, never()).validateAndLink(1L, listOf(1L), FilePurpose.JOB_APPLICATION, 1L)
    }

    @Test
    fun `fileId 집합이 바뀌면 기존 연결을 모두 해제하고 새 집합으로 다시 연결한다`() {
        val application =
            applicationOf(listOf(ApplicationAnswer(fieldId = "resume", value = null, fileIds = listOf(2L, 3L))))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(listOf(fileSnapshotOf(1L)))

        syncApplicationFiles(fileLinkPort, jsonMapper, application, requesterId = 1L)

        verify(fileLinkPort).unlinkAllOf(FileOwnerType.JOB_APPLICATION, 1L)
        verify(fileLinkPort).validateAndLink(
            requesterId = 1L,
            fileIds = listOf(2L, 3L),
            purpose = FilePurpose.JOB_APPLICATION,
            ownerId = 1L,
        )
    }

    @Test
    fun `답변에 fileId가 하나도 없으면 기존 연결만 모두 해제한다`() {
        val application = applicationOf(listOf(ApplicationAnswer(fieldId = "resume", value = null, fileIds = null)))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(listOf(fileSnapshotOf(1L)))

        syncApplicationFiles(fileLinkPort, jsonMapper, application, requesterId = 1L)

        verify(fileLinkPort).unlinkAllOf(FileOwnerType.JOB_APPLICATION, 1L)
        verify(fileLinkPort, never()).validateAndLink(1L, emptyList(), FilePurpose.JOB_APPLICATION, 1L)
    }
}
