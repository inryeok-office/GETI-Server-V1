package team.inreok.getiserver.domain.application.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.CreateFormRequest
import team.inreok.getiserver.domain.application.dto.FormAction
import team.inreok.getiserver.domain.application.dto.FormActionRequest
import team.inreok.getiserver.domain.application.dto.FormActionResponse
import team.inreok.getiserver.domain.application.dto.FormCreateResponse
import team.inreok.getiserver.domain.application.dto.FormDetailResponse
import team.inreok.getiserver.domain.application.dto.FormFieldResponse
import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import team.inreok.getiserver.domain.application.dto.FormListResponse
import team.inreok.getiserver.domain.application.dto.FormSummaryResponse
import team.inreok.getiserver.domain.application.dto.FormUpdateResponse
import team.inreok.getiserver.domain.application.dto.UpdateFormRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.FormVersion
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
import tools.jackson.databind.ObjectMapper

@Service
class FormServiceImpl(
    private val formRepository: FormRepository,
    private val formVersionRepository: FormVersionRepository,
    private val objectMapper: ObjectMapper,
) : FormService {
    @Transactional
    override fun create(
        ownerMemberId: Long,
        request: CreateFormRequest,
    ): FormCreateResponse {
        if (request.status == FormStatus.ARCHIVED) {
            throw InvalidFormFieldException("양식을 생성 시점에 ARCHIVED 상태로 만들 수 없습니다.")
        }
        val name = request.name.trim()
        if (name.isEmpty()) throw InvalidFormFieldException("양식 이름은 공백일 수 없습니다.")
        val schemas = validateAndBuildFieldSchemas(request.fields)

        val form =
            Form(
                ownerMemberId = ownerMemberId,
                name = name,
                formType = request.formType,
                status = request.status,
            ).apply {
                description = request.description?.trim()?.takeIf { it.isNotEmpty() }
            }

        // @CreationTimestamp는 Flush 시점에 채워진다. 응답의 createdAt이 null로 나가지 않도록
        // 저장과 동시에 Flush한다(Company 패턴과 동일).
        val saved = formRepository.saveAndFlush(form)
        formVersionRepository.save(
            FormVersion(formId = requireNotNull(saved.id), version = 1, schemaData = writeSchemas(schemas)),
        )

        return FormCreateResponse(
            formId = requireNotNull(saved.id),
            version = saved.currentVersion,
            status = saved.status,
            createdAt = requireNotNull(saved.createdAt),
        )
    }

    @Transactional(readOnly = true)
    override fun list(
        ownerMemberId: Long,
        formType: FormType?,
        status: FormStatus?,
        pageable: Pageable,
    ): FormListResponse {
        val page = formRepository.search(ownerMemberId, formType, status, pageable)
        return FormListResponse(
            content = page.content.map(FormSummaryResponse::from),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional(readOnly = true)
    override fun get(
        formId: Long,
        ownerMemberId: Long,
    ): FormDetailResponse {
        val form = findForm(formId)
        if (form.ownerMemberId != ownerMemberId) throw NotFormOwnerException()
        val schemas = readSchemas(currentVersionOf(form))

        return FormDetailResponse(
            formId = formId,
            name = form.name,
            formType = form.formType,
            status = form.status,
            description = form.description,
            schemaData = schemas.map(FormFieldResponse::from),
            createdAt = requireNotNull(form.createdAt),
            updatedAt = requireNotNull(form.updatedAt),
        )
    }

    @Transactional
    override fun update(
        formId: Long,
        ownerMemberId: Long,
        request: UpdateFormRequest,
    ): FormUpdateResponse {
        val form = findForm(formId)
        if (form.ownerMemberId != ownerMemberId) throw FormNotOwnedException()
        if (form.status == FormStatus.ARCHIVED) throw FormArchivedException()

        applyNameIfPresent(form, request.name)
        // create()와 동일하게 정규화한다(공백만 있으면 null로 저장). 코드 리뷰 P3 반영(PR #77).
        request.description?.let { form.description = it.trim().takeIf(String::isNotEmpty) }
        applyStatusIfPresent(form, request.status)

        // fields를 전달했을 때만 새 Form Version을 만든다(name/status만 바뀌는 경우는 제출된
        // 지원서 Snapshot과 무관해 버전을 올리지 않는다 — 계획 문서 §3.4/§4 결정 사항 4).
        request.fields?.let { fields ->
            val schemas = validateAndBuildFieldSchemas(fields)
            val newVersion = form.currentVersion + 1
            formVersionRepository.save(
                FormVersion(formId = formId, version = newVersion, schemaData = writeSchemas(schemas)),
            )
            form.currentVersion = newVersion
        }

        formRepository.flush()

        // 공고-양식 연결(affectedJobIds)과 Notification 연동은 후속 Phase다. 아직 구현되지
        // 않은 기능을 있는 것처럼 응답하지 않는다(요구사항 20절 취지와 동일).
        return FormUpdateResponse(
            formId = formId,
            version = form.currentVersion,
            affectedJobIds = emptyList(),
            notificationCreated = false,
            updatedAt = requireNotNull(form.updatedAt),
        )
    }

    @Transactional
    override fun executeAction(
        formId: Long,
        ownerMemberId: Long,
        request: FormActionRequest,
    ): FormActionResponse {
        val form = findForm(formId)
        if (form.ownerMemberId != ownerMemberId) throw FormNotOwnedException()

        return when (request.action) {
            FormAction.DUPLICATE -> {
                duplicate(form, request.newName)
            }

            FormAction.ACTIVATE -> {
                transitionStatus(
                    form,
                    FormAction.ACTIVATE,
                    setOf(FormStatus.DRAFT, FormStatus.ARCHIVED),
                    FormStatus.ACTIVE,
                )
            }

            FormAction.ARCHIVE -> {
                transitionStatus(
                    form,
                    FormAction.ARCHIVE,
                    setOf(FormStatus.DRAFT, FormStatus.ACTIVE),
                    FormStatus.ARCHIVED,
                )
            }
        }
    }

    private fun duplicate(
        original: Form,
        newName: String?,
    ): FormActionResponse {
        val latestVersion = currentVersionOf(original)
        val name = newName?.trim()?.takeIf { it.isNotEmpty() } ?: "${original.name} 복사본"

        val duplicated =
            Form(
                ownerMemberId = original.ownerMemberId,
                name = name,
                formType = original.formType,
                status = FormStatus.DRAFT,
            ).apply { description = original.description }

        val saved = formRepository.saveAndFlush(duplicated)
        // 원본 지원서·답변은 복제하지 않는다(요구사항 5.6절). 최신 버전의 필드 구조만 그대로 복사한다.
        formVersionRepository.save(
            FormVersion(formId = requireNotNull(saved.id), version = 1, schemaData = latestVersion.schemaData),
        )

        return FormActionResponse(
            formId = requireNotNull(saved.id),
            name = saved.name,
            formType = saved.formType,
            status = saved.status,
            version = saved.currentVersion,
            updatedAt = requireNotNull(saved.updatedAt),
        )
    }

    private fun transitionStatus(
        form: Form,
        action: FormAction,
        allowedFrom: Set<FormStatus>,
        to: FormStatus,
    ): FormActionResponse {
        if (form.status !in allowedFrom) {
            throw FormActionInvalidException("현재 상태(${form.status})에서는 $action Action을 수행할 수 없습니다.")
        }
        form.status = to
        formRepository.flush()

        return FormActionResponse(
            formId = requireNotNull(form.id),
            name = form.name,
            formType = form.formType,
            status = form.status,
            version = form.currentVersion,
            updatedAt = requireNotNull(form.updatedAt),
        )
    }

    private fun findForm(formId: Long): Form =
        formRepository.findById(formId).orElseThrow { FormNotFoundException(formId) }

    // Form이 존재하면 currentVersion에 해당하는 FormVersion도 항상 존재해야 한다(생성·수정
    // 시점에 항상 함께 저장하는 불변식). 없다면 데이터 정합성이 깨진 것이라 예외로 드러낸다.
    private fun currentVersionOf(form: Form): FormVersion =
        formVersionRepository.findByFormIdAndVersion(requireNotNull(form.id), form.currentVersion)
            ?: error("Form(id=${form.id})의 현재 버전(${form.currentVersion}) FormVersion을 찾을 수 없습니다.")

    private fun writeSchemas(schemas: List<FormFieldSchema>): String = objectMapper.writeValueAsString(schemas)

    private fun readSchemas(version: FormVersion): List<FormFieldSchema> =
        readFormFieldSchemas(objectMapper, version.schemaData)
}
