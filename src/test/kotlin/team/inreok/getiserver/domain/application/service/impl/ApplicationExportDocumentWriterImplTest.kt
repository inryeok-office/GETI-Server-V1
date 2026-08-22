package team.inreok.getiserver.domain.application.service.impl

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.application.service.ApplicationAnswerExportRow
import team.inreok.getiserver.domain.application.service.ApplicationAnswersExportData
import team.inreok.getiserver.domain.application.service.ApplicationProfileExportData
import java.io.ByteArrayInputStream

class ApplicationExportDocumentWriterImplTest {
    private val writer = ApplicationExportDocumentWriterImpl()

    @Test
    fun `PROFILE XLSX는 생성 후 다시 열 수 있고 applicationId와 snapshot 필드를 포함한다`() {
        val entry =
            writer.writeProfile(
                ApplicationProfileExportData(
                    applicationId = 42L,
                    applicantName = "홍길동",
                    contactEmail = "snapshot@example.com",
                    contactPhone = "010-0000-0000",
                    applicantCohort = 3,
                    applicantDepartment = "컴퓨터공학",
                ),
            )

        XSSFWorkbook(ByteArrayInputStream(entry.content)).use { workbook ->
            val sheet = workbook.getSheet("Profile")
            assertThat(sheet.getRow(0).getCell(0).stringCellValue).isEqualTo("applicationId")
            assertThat(sheet.getRow(1).getCell(0).stringCellValue).isEqualTo("42")
            assertThat(sheet.getRow(1).getCell(2).stringCellValue).isEqualTo("snapshot@example.com")
            assertThat(entry.displayName).isEqualTo("application-42-profile.xlsx")
        }
    }

    @Test
    fun `ANSWERS XLSX는 생성 후 다시 열 수 있고 질문과 답변을 포함한다`() {
        val entry =
            writer.writeAnswers(
                ApplicationAnswersExportData(
                    applicationId = 42L,
                    rows = listOf(ApplicationAnswerExportRow("motivation", "지원 동기", "성장하고 싶습니다")),
                ),
            )

        XSSFWorkbook(ByteArrayInputStream(entry.content)).use { workbook ->
            val sheet = workbook.getSheet("Answers")
            assertThat(sheet.getRow(0).getCell(0).stringCellValue).isEqualTo("applicationId")
            assertThat(sheet.getRow(1).getCell(1).stringCellValue).isEqualTo("motivation")
            assertThat(sheet.getRow(1).getCell(2).stringCellValue).isEqualTo("지원 동기")
            assertThat(sheet.getRow(1).getCell(3).stringCellValue).isEqualTo("성장하고 싶습니다")
            assertThat(entry.displayName).isEqualTo("application-42-answers.xlsx")
        }
    }
}
