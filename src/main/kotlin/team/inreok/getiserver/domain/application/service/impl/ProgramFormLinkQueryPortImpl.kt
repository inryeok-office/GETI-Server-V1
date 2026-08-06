package team.inreok.getiserver.domain.application.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.query.ProgramFormLinkQueryPort
import team.inreok.getiserver.domain.application.query.ProgramFormSnapshot
import team.inreok.getiserver.domain.application.repository.FormRepository

@Service
class ProgramFormLinkQueryPortImpl(
    private val formRepository: FormRepository,
) : ProgramFormLinkQueryPort {
    @Suppress("ReturnCount")
    @Transactional(readOnly = true)
    override fun findLinkableProgramForm(
        formId: Long,
        ownerMemberId: Long,
    ): ProgramFormSnapshot? {
        val form = formRepository.findById(formId).orElse(null) ?: return null
        if (!isLinkableProgramForm(form, ownerMemberId)) return null
        return ProgramFormSnapshot(formId = requireNotNull(form.id), currentVersion = form.currentVersion)
    }

    @Suppress("ReturnCount")
    @Transactional(readOnly = true)
    override fun findActiveVersion(formId: Long): Int? {
        val form = formRepository.findById(formId).orElse(null) ?: return null
        if (form.status != FormStatus.ACTIVE) return null
        return form.currentVersion
    }

    private fun isLinkableProgramForm(
        form: Form,
        ownerMemberId: Long,
    ): Boolean =
        form.ownerMemberId == ownerMemberId &&
            form.formType == FormType.PROGRAM &&
            form.status == FormStatus.ACTIVE
}
