package team.inreok.getiserver.domain.application.service.impl

import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.application.service.ApplicationAnswerExportRow
import team.inreok.getiserver.domain.application.service.ApplicationAnswersExportData
import team.inreok.getiserver.domain.application.service.ApplicationExportDocumentWriter
import team.inreok.getiserver.domain.application.service.ApplicationProfileExportData
import team.inreok.getiserver.domain.file.archive.FileArchiveContentEntry
import java.io.ByteArrayOutputStream

@Component
class ApplicationExportDocumentWriterImpl : ApplicationExportDocumentWriter {
    override fun writeProfile(data: ApplicationProfileExportData): FileArchiveContentEntry =
        documentEntry(data.applicationId, "profile") { workbook ->
            val sheet = workbook.createSheet("Profile")
            val headers =
                listOf(
                    "applicationId",
                    "applicantName",
                    "contactEmail",
                    "contactPhone",
                    "applicantCohort",
                    "applicantDepartment",
                )
            writeHeader(sheet, headers)
            writeRow(
                sheet,
                1,
                listOf(
                    data.applicationId.toString(),
                    data.applicantName.orEmpty(),
                    data.contactEmail,
                    data.contactPhone.orEmpty(),
                    data.applicantCohort?.toString().orEmpty(),
                    data.applicantDepartment.orEmpty(),
                ),
            )
        }

    override fun writeAnswers(data: ApplicationAnswersExportData): FileArchiveContentEntry =
        documentEntry(data.applicationId, "answers") { workbook ->
            val sheet = workbook.createSheet("Answers")
            writeHeader(sheet, listOf("applicationId", "fieldId", "question", "answer"))
            data.rows.forEachIndexed { index, row ->
                writeRow(sheet, index + 1, listOf(data.applicationId.toString(), row.fieldId, row.question, row.answer))
            }
        }

    private fun documentEntry(
        applicationId: Long,
        materialName: String,
        populate: (Workbook) -> Unit,
    ): FileArchiveContentEntry {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            populate(workbook)
            workbook.write(output)
        }
        return FileArchiveContentEntry("application-$applicationId-$materialName.xlsx", output.toByteArray())
    }

    private fun writeHeader(
        sheet: Sheet,
        headers: List<String>,
    ) {
        val row = sheet.createRow(0)
        headers.forEachIndexed { index, header -> row.createCell(index).setCellValue(header) }
    }

    private fun writeRow(
        sheet: Sheet,
        rowIndex: Int,
        values: List<String>,
    ) {
        val row = sheet.createRow(rowIndex)
        values.forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
    }
}
