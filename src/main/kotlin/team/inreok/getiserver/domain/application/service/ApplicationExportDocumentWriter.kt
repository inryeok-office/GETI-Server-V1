package team.inreok.getiserver.domain.application.service

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.file.archive.FileArchiveContentEntry

@NamedInterface
data class ApplicationProfileExportData(
    val applicationId: Long,
    val applicantName: String?,
    val contactEmail: String,
    val contactPhone: String?,
    val applicantCohort: Int?,
    val applicantDepartment: String?,
)

@NamedInterface
data class ApplicationAnswerExportRow(
    val fieldId: String,
    val question: String,
    val answer: String,
)

@NamedInterface
data class ApplicationAnswersExportData(
    val applicationId: Long,
    val rows: List<ApplicationAnswerExportRow>,
)

/** 지원자 자료를 실제 XLSX 문서로 변환한다. 생성 결과는 ZIP Entry로 File 도메인에 전달한다. */
interface ApplicationExportDocumentWriter {
    fun writeProfile(data: ApplicationProfileExportData): FileArchiveContentEntry

    fun writeAnswers(data: ApplicationAnswersExportData): FileArchiveContentEntry
}
