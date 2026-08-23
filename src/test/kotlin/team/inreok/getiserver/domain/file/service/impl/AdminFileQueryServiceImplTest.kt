package team.inreok.getiserver.domain.file.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.file.access.FileUploaderAccessor
import team.inreok.getiserver.domain.file.access.FileUploaderSnapshot
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.AdminFileQueryService
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminFileQueryServiceImplTest {
    @Mock
    private lateinit var storedFileRepository: StoredFileRepository

    @Mock
    private lateinit var fileUploaderAccessor: FileUploaderAccessor

    private val service: AdminFileQueryService by lazy {
        AdminFileQueryServiceImpl(storedFileRepository, fileUploaderAccessor)
    }

    private val fixedTime = LocalDateTime.of(2026, 8, 20, 9, 0)

    private fun fileOf(
        id: Long = 1L,
        uploaderMemberId: Long? = 7L,
        purpose: FilePurpose = FilePurpose.JOB_APPLICATION,
    ) = StoredFile(
        purpose = purpose,
        objectKey = "$purpose/2026/08/${UUID.randomUUID()}",
        originalName = "이력서.pdf",
        contentType = "application/pdf",
        sizeBytes = 204_800,
        uploaderMemberId = uploaderMemberId,
        extension = "pdf",
    ).apply {
        this.id = id
        this.createdAt = fixedTime
    }

    // 기본(무필터) searchForAdmin() 호출을 Stub한다(JobApplicationAdminServiceImplTest.givenDefaultSearch와
    // 동일한 관례).
    private fun givenDefaultSearch(page: Page<StoredFile>) {
        given(
            storedFileRepository.searchForAdmin(
                status = null,
                purpose = null,
                hasOriginalName = false,
                originalName = "",
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(page)
    }

    @Test
    fun `목록을 조회하면 항목을 그대로 매핑한다`() {
        val file = fileOf()
        file.markUploaded()
        file.linkTo(FileOwnerType.JOB_APPLICATION, 15L, fixedTime)
        givenDefaultSearch(PageImpl(listOf(file), PageRequest.of(0, 20), 1))
        given(fileUploaderAccessor.findAllByIds(setOf(7L)))
            .willReturn(mapOf(7L to FileUploaderSnapshot(memberId = 7L, name = "홍길동")))

        val result = service.list(null, null, null, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
        val item = result.content[0]
        assertThat(item.fileId).isEqualTo(1L)
        assertThat(item.originalName).isEqualTo("이력서.pdf")
        assertThat(item.sizeBytes).isEqualTo(204_800)
        assertThat(item.status).isEqualTo(FileStatus.LINKED)
        assertThat(item.uploader?.memberId).isEqualTo(7L)
        assertThat(item.uploader?.name).isEqualTo("홍길동")
        assertThat(item.ownerType).isEqualTo(FileOwnerType.JOB_APPLICATION)
        assertThat(item.ownerId).isEqualTo(15L)
        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.page).isEqualTo(0)
        assertThat(result.first).isTrue()
        assertThat(result.last).isTrue()
    }

    @Test
    fun `업로더 회원 ID가 없으면 uploader는 null이고 배치 조회를 호출하지 않는다`() {
        val file = fileOf(uploaderMemberId = null)
        givenDefaultSearch(PageImpl(listOf(file), PageRequest.of(0, 20), 1))

        val result = service.list(null, null, null, PageRequest.of(0, 20))

        assertThat(result.content[0].uploader).isNull()
        verifyNoInteractions(fileUploaderAccessor)
    }

    @Test
    fun `조회된 업로더 id가 배치 조회 결과에 없으면 이름은 null이다`() {
        val file = fileOf()
        givenDefaultSearch(PageImpl(listOf(file), PageRequest.of(0, 20), 1))
        given(fileUploaderAccessor.findAllByIds(setOf(7L))).willReturn(emptyMap())

        val result = service.list(null, null, null, PageRequest.of(0, 20))

        assertThat(result.content[0].uploader?.memberId).isEqualTo(7L)
        assertThat(result.content[0].uploader?.name).isNull()
    }

    @Test
    fun `결과가 비어 있으면 빈 목록을 반환한다`() {
        givenDefaultSearch(PageImpl(emptyList(), PageRequest.of(0, 20), 0))

        val result = service.list(null, null, null, PageRequest.of(0, 20))

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isZero()
    }

    @Test
    fun `status와 purpose Filter는 Repository에 그대로 전달된다`() {
        given(
            storedFileRepository.searchForAdmin(
                status = FileStatus.LINKED,
                purpose = FilePurpose.JOB_APPLICATION,
                hasOriginalName = false,
                originalName = "",
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(PageImpl(emptyList(), PageRequest.of(0, 20), 0))

        service.list(FileStatus.LINKED, FilePurpose.JOB_APPLICATION, null, PageRequest.of(0, 20))

        verify(storedFileRepository).searchForAdmin(
            FileStatus.LINKED,
            FilePurpose.JOB_APPLICATION,
            false,
            "",
            PageRequest.of(0, 20),
        )
    }

    @Test
    fun `검색어는 이스케이프되어 Repository에 전달된다`() {
        val captor = ArgumentCaptor.forClass(String::class.java)
        given(
            storedFileRepository.searchForAdmin(
                isNull(),
                isNull(),
                eq(true),
                captor.capture() ?: "",
                any(Pageable::class.java) ?: PageRequest.of(0, 20),
            ),
        ).willReturn(PageImpl(emptyList(), PageRequest.of(0, 20), 0))

        service.list(null, null, "이력%서_1", PageRequest.of(0, 20))

        assertThat(captor.value).isEqualTo("이력\\%서\\_1")
    }

    @Test
    fun `공백만 있는 검색어는 검색어 없음으로 취급된다`() {
        givenDefaultSearch(PageImpl(emptyList(), PageRequest.of(0, 20), 0))

        service.list(null, null, "   ", PageRequest.of(0, 20))

        verify(storedFileRepository).searchForAdmin(null, null, false, "", PageRequest.of(0, 20))
    }
}
