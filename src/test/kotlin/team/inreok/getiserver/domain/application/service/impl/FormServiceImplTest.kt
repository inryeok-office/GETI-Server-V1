package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.application.dto.CreateFormRequest
import team.inreok.getiserver.domain.application.dto.FormAction
import team.inreok.getiserver.domain.application.dto.FormActionRequest
import team.inreok.getiserver.domain.application.dto.FormFieldRequest
import team.inreok.getiserver.domain.application.dto.UpdateFormRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.FormVersion
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.exception.FormActionInvalidException
import team.inreok.getiserver.domain.application.exception.FormArchivedException
import team.inreok.getiserver.domain.application.exception.FormNotFoundException
import team.inreok.getiserver.domain.application.exception.FormNotOwnedException
import team.inreok.getiserver.domain.application.exception.InvalidFormFieldException
import team.inreok.getiserver.domain.application.exception.NotFormOwnerException
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import team.inreok.getiserver.domain.application.service.FormService
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class FormServiceImplTest {
    @Mock
    private lateinit var formRepository: FormRepository

    @Mock
    private lateinit var formVersionRepository: FormVersionRepository

    private val service: FormService by lazy { FormServiceImpl(formRepository, formVersionRepository, JsonMapper()) }

    private val fixedTime: LocalDateTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

    private fun formOf(
        id: Long = 1L,
        ownerMemberId: Long = 100L,
        name: String = "인턴 지원서",
        formType: FormType = FormType.JOB,
        status: FormStatus = FormStatus.DRAFT,
        currentVersion: Int = 1,
    ) = Form(ownerMemberId = ownerMemberId, name = name, formType = formType, status = status).apply {
        this.id = id
        this.currentVersion = currentVersion
        this.createdAt = fixedTime
        this.updatedAt = fixedTime
    }

    private fun formVersionOf(
        formId: Long = 1L,
        version: Int = 1,
        schemaData: String =
            """[{"key":"motivation","type":"TEXT","label":"지원 동기","required":true,"order":0}]""",
    ) = FormVersion(formId = formId, version = version, schemaData = schemaData).apply {
        this.id = 1L
        this.createdAt = fixedTime
    }

    private fun textFieldRequest(key: String = "motivation") =
        FormFieldRequest(key = key, type = FormFieldType.TEXT, label = "지원 동기", required = true)

    private fun anyForm(): Form = any(Form::class.java) ?: Form(ownerMemberId = 0, name = "", formType = FormType.JOB)

    private fun anyFormVersion(): FormVersion =
        any(FormVersion::class.java) ?: FormVersion(formId = 0, version = 1, schemaData = "[]")

    // ---------- create ----------

    @Test
    fun `양식을 생성하면 FormVersion 1을 함께 만들고 생성 결과를 반환한다`() {
        val request =
            CreateFormRequest(
                name = "2026 하계 인턴 지원서",
                formType = FormType.JOB,
                fields = listOf(textFieldRequest()),
            )
        given(formRepository.saveAndFlush(anyForm())).willAnswer { invocation ->
            (invocation.arguments[0] as Form).apply {
                id = 10L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        val result = service.create(100L, request)

        assertThat(result.formId).isEqualTo(10L)
        assertThat(result.version).isEqualTo(1)
        assertThat(result.status).isEqualTo(FormStatus.DRAFT)
        verify(formVersionRepository).save(anyFormVersion())
    }

    @Test
    fun `ARCHIVED 상태로 생성을 요청하면 InvalidFormFieldException을 던진다`() {
        val request =
            CreateFormRequest(
                name = "지원서",
                formType = FormType.JOB,
                fields = listOf(textFieldRequest()),
                status = FormStatus.ARCHIVED,
            )

        assertThatThrownBy { service.create(100L, request) }
            .isInstanceOf(InvalidFormFieldException::class.java)
        verify(formRepository, never()).saveAndFlush(anyForm())
    }

    @Test
    fun `양식 이름이 공백이면 InvalidFormFieldException을 던진다`() {
        val request = CreateFormRequest(name = "   ", formType = FormType.JOB, fields = listOf(textFieldRequest()))

        assertThatThrownBy { service.create(100L, request) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `필드 검증에 실패하면 InvalidFormFieldException이 그대로 전파된다`() {
        val request =
            CreateFormRequest(
                name = "지원서",
                formType = FormType.JOB,
                fields = listOf(textFieldRequest(), textFieldRequest()),
            )

        assertThatThrownBy { service.create(100L, request) }
            .isInstanceOf(InvalidFormFieldException::class.java)
        verify(formRepository, never()).saveAndFlush(anyForm())
    }

    // ---------- list ----------

    @Test
    fun `내 양식 목록을 조회하면 소유자 조건으로 검색해 Page 결과를 반환한다`() {
        val pageable = PageRequest.of(0, 20)
        given(formRepository.search(100L, FormType.JOB, null, pageable))
            .willReturn(PageImpl(listOf(formOf()), pageable, 1))

        val result = service.list(100L, FormType.JOB, null, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].formId).isEqualTo(1L)
        assertThat(result.totalElements).isEqualTo(1)
    }

    // ---------- get ----------

    @Test
    fun `양식 상세를 조회하면 현재 버전의 필드 구조를 함께 반환한다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf()))
        given(formVersionRepository.findByFormIdAndVersion(1L, 1)).willReturn(formVersionOf())

        val result = service.get(1L, 100L)

        assertThat(result.formId).isEqualTo(1L)
        assertThat(result.schemaData).hasSize(1)
        assertThat(result.schemaData[0].fieldId).isEqualTo("motivation")
        assertThat(result.schemaData[0].title).isEqualTo("지원 동기")
    }

    @Test
    fun `존재하지 않는 양식을 조회하면 FormNotFoundException을 던진다`() {
        given(formRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.get(999L, 100L) }
            .isInstanceOf(FormNotFoundException::class.java)
    }

    @Test
    fun `다른 사용자의 양식을 조회하면 NotFormOwnerException을 던진다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(ownerMemberId = 100L)))

        assertThatThrownBy { service.get(1L, 200L) }
            .isInstanceOf(NotFormOwnerException::class.java)
    }

    // ---------- update ----------

    @Test
    fun `fields 없이 이름만 수정하면 버전이 오르지 않는다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(currentVersion = 1)))

        val result = service.update(1L, 100L, UpdateFormRequest(name = "새 이름"))

        assertThat(result.version).isEqualTo(1)
        verify(formVersionRepository, never()).save(anyFormVersion())
    }

    @Test
    fun `fields를 전달하면 새 FormVersion을 만들고 버전이 오른다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(currentVersion = 1)))

        val result = service.update(1L, 100L, UpdateFormRequest(fields = listOf(textFieldRequest())))

        assertThat(result.version).isEqualTo(2)
        verify(formVersionRepository).save(anyFormVersion())
    }

    @Test
    fun `수정 결과는 공고-양식 연결 전이라 affectedJobIds가 항상 빈 배열이고 notificationCreated는 false다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf()))

        val result = service.update(1L, 100L, UpdateFormRequest(name = "새 이름"))

        assertThat(result.affectedJobIds).isEmpty()
        assertThat(result.notificationCreated).isFalse()
    }

    @Test
    fun `PATCH로 DRAFT에서 ACTIVE로 status를 바꿀 수 있다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.DRAFT)))

        val result = service.update(1L, 100L, UpdateFormRequest(status = FormStatus.ACTIVE))

        // FormUpdateResponse는 status를 직접 담지 않으므로 get()으로 실제 반영 여부를 확인한다.
        assertThat(result.formId).isEqualTo(1L)
        given(formVersionRepository.findByFormIdAndVersion(1L, 1)).willReturn(formVersionOf())
        assertThat(service.get(1L, 100L).status).isEqualTo(FormStatus.ACTIVE)
    }

    @Test
    fun `PATCH로 ACTIVE 양식을 DRAFT로 되돌리려 하면 FormActionInvalidException을 던진다`() {
        // POST .../actions에는 ACTIVE를 DRAFT로 되돌리는 Action이 없다. PATCH의 status Field가
        // 이 규칙을 우회하지 못하도록 막는다(코드 리뷰 Finding #1, PR #77).
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.ACTIVE)))

        assertThatThrownBy { service.update(1L, 100L, UpdateFormRequest(status = FormStatus.DRAFT)) }
            .isInstanceOf(FormActionInvalidException::class.java)
    }

    @Test
    fun `PATCH로 현재와 같은 status를 다시 보내면 아무 일도 일어나지 않는다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.DRAFT)))

        val result = service.update(1L, 100L, UpdateFormRequest(name = "새 이름", status = FormStatus.DRAFT))

        assertThat(result.formId).isEqualTo(1L)
    }

    @Test
    fun `보관된 양식을 수정하면 FormArchivedException을 던진다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.ARCHIVED)))

        assertThatThrownBy { service.update(1L, 100L, UpdateFormRequest(name = "새 이름")) }
            .isInstanceOf(FormArchivedException::class.java)
    }

    @Test
    fun `다른 사용자의 양식을 수정하면 FormNotOwnedException을 던진다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(ownerMemberId = 100L)))

        assertThatThrownBy { service.update(1L, 200L, UpdateFormRequest(name = "새 이름")) }
            .isInstanceOf(FormNotOwnedException::class.java)
    }

    @Test
    fun `존재하지 않는 양식을 수정하면 FormNotFoundException을 던진다`() {
        given(formRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.update(999L, 100L, UpdateFormRequest(name = "새 이름")) }
            .isInstanceOf(FormNotFoundException::class.java)
    }

    // ---------- actions ----------

    @Test
    fun `DUPLICATE Action은 최신 필드 구조만 복사한 새 양식을 생성한다`() {
        val original = formOf(id = 1L, currentVersion = 2)
        given(formRepository.findById(1L)).willReturn(Optional.of(original))
        given(formVersionRepository.findByFormIdAndVersion(1L, 2)).willReturn(formVersionOf(formId = 1L, version = 2))
        given(formRepository.saveAndFlush(anyForm())).willAnswer { invocation ->
            (invocation.arguments[0] as Form).apply {
                id = 20L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        val result = service.executeAction(1L, 100L, FormActionRequest(action = FormAction.DUPLICATE))

        assertThat(result.formId).isEqualTo(20L)
        assertThat(result.status).isEqualTo(FormStatus.DRAFT)
        assertThat(result.version).isEqualTo(1)
        assertThat(result.name).isEqualTo("인턴 지원서 복사본")
        // 원본은 그대로 유지된다(재지원 이력·상태 전이와 무관하게 복제는 새 Row만 만든다).
        assertThat(original.status).isEqualTo(FormStatus.DRAFT)
    }

    @Test
    fun `DUPLICATE Action에 newName을 전달하면 그 이름을 사용한다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf()))
        given(formVersionRepository.findByFormIdAndVersion(1L, 1)).willReturn(formVersionOf())
        given(formRepository.saveAndFlush(anyForm())).willAnswer { invocation ->
            (invocation.arguments[0] as Form).apply {
                id = 21L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        val result =
            service.executeAction(
                1L,
                100L,
                FormActionRequest(action = FormAction.DUPLICATE, newName = "새 지원서"),
            )

        assertThat(result.name).isEqualTo("새 지원서")
    }

    @Test
    fun `DRAFT 양식을 ACTIVATE하면 ACTIVE로 전이된다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.DRAFT)))

        val result = service.executeAction(1L, 100L, FormActionRequest(action = FormAction.ACTIVATE))

        assertThat(result.status).isEqualTo(FormStatus.ACTIVE)
    }

    @Test
    fun `ACTIVE 양식을 ACTIVATE하면 FormActionInvalidException을 던진다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.ACTIVE)))

        assertThatThrownBy { service.executeAction(1L, 100L, FormActionRequest(action = FormAction.ACTIVATE)) }
            .isInstanceOf(FormActionInvalidException::class.java)
    }

    @Test
    fun `ACTIVE 양식을 ARCHIVE하면 ARCHIVED로 전이된다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.ACTIVE)))

        val result = service.executeAction(1L, 100L, FormActionRequest(action = FormAction.ARCHIVE))

        assertThat(result.status).isEqualTo(FormStatus.ARCHIVED)
    }

    @Test
    fun `ARCHIVED 양식을 ARCHIVE하면 FormActionInvalidException을 던진다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(status = FormStatus.ARCHIVED)))

        assertThatThrownBy { service.executeAction(1L, 100L, FormActionRequest(action = FormAction.ARCHIVE)) }
            .isInstanceOf(FormActionInvalidException::class.java)
    }

    @Test
    fun `다른 사용자의 양식에 Action을 수행하면 FormNotOwnedException을 던진다`() {
        given(formRepository.findById(1L)).willReturn(Optional.of(formOf(ownerMemberId = 100L)))

        assertThatThrownBy { service.executeAction(1L, 200L, FormActionRequest(action = FormAction.ARCHIVE)) }
            .isInstanceOf(FormNotOwnedException::class.java)
    }
}
