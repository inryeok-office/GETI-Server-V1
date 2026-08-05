package team.inreok.getiserver.domain.application.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.dto.CreateFormRequest
import team.inreok.getiserver.domain.application.dto.FormActionRequest
import team.inreok.getiserver.domain.application.dto.FormActionResponse
import team.inreok.getiserver.domain.application.dto.FormCreateResponse
import team.inreok.getiserver.domain.application.dto.FormDetailResponse
import team.inreok.getiserver.domain.application.dto.FormListResponse
import team.inreok.getiserver.domain.application.dto.FormUpdateResponse
import team.inreok.getiserver.domain.application.dto.UpdateFormRequest
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType

interface FormService {
    fun create(
        ownerMemberId: Long,
        request: CreateFormRequest,
    ): FormCreateResponse

    /** 현재 로그인 사용자가 소유한 양식만 반환한다. */
    fun list(
        ownerMemberId: Long,
        formType: FormType?,
        status: FormStatus?,
        pageable: Pageable,
    ): FormListResponse

    /** 양식이 없으면 [team.inreok.getiserver.domain.application.exception.FormNotFoundException],
     * 소유자가 아니면 [team.inreok.getiserver.domain.application.exception.NotFormOwnerException]. */
    fun get(
        formId: Long,
        ownerMemberId: Long,
    ): FormDetailResponse

    /** 양식이 없으면 FormNotFoundException, 소유자가 아니면 FormNotOwnedException,
     * 보관된 양식이면 FormArchivedException. */
    fun update(
        formId: Long,
        ownerMemberId: Long,
        request: UpdateFormRequest,
    ): FormUpdateResponse

    fun executeAction(
        formId: Long,
        ownerMemberId: Long,
        request: FormActionRequest,
    ): FormActionResponse
}
