package team.inreok.getiserver.domain.application.entity.type

enum class JobApplicationStatus {
    DRAFT,
    SUBMITTED,
    EDIT_REQUESTED,
    EDIT_ALLOWED,
    REVISION_REQUESTED,
    APPROVED,
    REJECTED,
    FORWARDED,
    WITHDRAWN,
}
