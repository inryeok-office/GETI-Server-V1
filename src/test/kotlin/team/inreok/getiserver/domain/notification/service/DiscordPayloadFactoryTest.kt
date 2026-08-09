package team.inreok.getiserver.domain.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadSnapshot
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadSnapshot
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadSnapshot
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * GETI-Bot-V1 `src/renderer/schemas.ts`의 `.strict()` Schema를 서버가 위반하지 않는지 검증한다.
 * 위반하면 Bot이 `400 INVALID_REQUEST`(retryable=false)를 돌려주고 자동 재시도 없이 즉시
 * FAILED가 되므로, 이 Test가 실질적인 Contract 방어선이다.
 */
class DiscordPayloadFactoryTest {
    private val factory = DiscordPayloadFactory()
    private val deadline = LocalDateTime.of(2026, 8, 31, 18, 0)

    @Test
    fun `공고 게시 Payload는 Bot Schema가 받는 필드만 담는다`() {
        val data = factory.forJob(DiscordMessageTemplate.JOB_PUBLISHED, jobSnapshot())

        assertThat(data).containsOnlyKeys("jobId", "title", "companyName", "deadline")
        assertThat(data["jobId"]).isEqualTo("10")
        assertThat(data["title"]).isEqualTo("백엔드 신입 채용")
        assertThat(data["companyName"]).isEqualTo("인력개발원")
    }

    @Test
    fun `공고 삭제 Payload에는 companyName과 deadline을 담지 않는다`() {
        // jobDeletedDataSchema는 jobId/title/reason만 받고 .strict()라 나머지는 400이다.
        val data = factory.forJob(DiscordMessageTemplate.JOB_DELETED, jobSnapshot())

        assertThat(data).containsOnlyKeys("jobId", "title")
    }

    @Test
    fun `제목이 200자를 넘으면 잘라서 상한을 지킨다`() {
        // jobs_title은 DB에서 500자까지 허용되지만 Bot titleSchema는 200자다.
        val data = factory.forJob(DiscordMessageTemplate.JOB_PUBLISHED, jobSnapshot(title = "가".repeat(500)))

        val title = requireNotNull(data["title"])
        assertThat(title.length).isEqualTo(200)
        assertThat(title).endsWith("…")
    }

    @Test
    fun `상한 이하 제목은 자르지 않는다`() {
        val data = factory.forJob(DiscordMessageTemplate.JOB_PUBLISHED, jobSnapshot(title = "가".repeat(200)))

        assertThat(data["title"]).isEqualTo("가".repeat(200))
    }

    @Test
    fun `값이 없는 선택 필드는 아예 담지 않는다`() {
        // Bot Schema의 문자열은 trim 후 min(1)이라 빈 문자열을 보내면 400이다.
        val data =
            factory.forJob(
                DiscordMessageTemplate.JOB_PUBLISHED,
                jobSnapshot(companyName = null, recruitmentEndedAt = null),
            )

        assertThat(data).containsOnlyKeys("jobId", "title")
    }

    @Test
    fun `공백뿐인 선택 필드도 담지 않는다`() {
        val data = factory.forJob(DiscordMessageTemplate.JOB_PUBLISHED, jobSnapshot(companyName = "   "))

        assertThat(data).doesNotContainKey("companyName")
    }

    @Test
    fun `시각은 Offset이 포함된 ISO-8601로 직렬화한다`() {
        val data = factory.forJob(DiscordMessageTemplate.JOB_PUBLISHED, jobSnapshot())

        val deadlineText = requireNotNull(data["deadline"])
        // Timezone 없이 보내면 Bot이 어느 시각인지 확정할 수 없다(요구사항 §47).
        assertThat(OffsetDateTime.parse(deadlineText).toLocalDateTime()).isEqualTo(deadline)
        assertThat(OffsetDateTime.parse(deadlineText).offset)
            .isEqualTo(deadline.atZone(ZoneId.systemDefault()).offset)
        assertThat(deadlineText.length).isLessThanOrEqualTo(50)
    }

    @Test
    fun `프로그램 게시 Payload는 설명과 행사 기간을 담는다`() {
        val data = factory.forProgram(DiscordMessageTemplate.PROGRAM_PUBLISHED, programSnapshot())

        assertThat(data).containsOnlyKeys("programId", "title", "description", "startAt", "endAt")
    }

    @Test
    fun `프로그램 수정 Payload에는 설명과 기간을 담지 않는다`() {
        // programUpdatedDataSchema는 programId/title/changes/url만 받는다.
        val data = factory.forProgram(DiscordMessageTemplate.PROGRAM_UPDATED, programSnapshot())

        assertThat(data).containsOnlyKeys("programId", "title")
    }

    @Test
    fun `프로그램 설명이 500자를 넘으면 잘라서 상한을 지킨다`() {
        val data =
            factory.forProgram(
                DiscordMessageTemplate.PROGRAM_PUBLISHED,
                programSnapshot(bodyMarkdown = "나".repeat(900)),
            )

        assertThat(requireNotNull(data["description"]).length).isEqualTo(500)
    }

    @Test
    fun `문의 Payload에 개인정보를 담지 않는다`() {
        val data = factory.forInquiry(inquirySnapshot())

        // 이메일·전화번호·문의 본문·제목은 Snapshot 자체에 없어 실을 수 없다(요구사항 §40).
        assertThat(data).containsOnlyKeys("inquiryId", "category", "requesterName", "submittedAt")
    }

    @Test
    fun `작성자를 찾을 수 없으면 이름을 담지 않는다`() {
        val data = factory.forInquiry(inquirySnapshot(requesterName = null))

        assertThat(data).doesNotContainKey("requesterName")
    }

    @Test
    fun `작성자 이름이 100자를 넘으면 잘라서 상한을 지킨다`() {
        val data = factory.forInquiry(inquirySnapshot(requesterName = "다".repeat(150)))

        assertThat(requireNotNull(data["requesterName"]).length).isEqualTo(100)
    }

    private fun jobSnapshot(
        title: String = "백엔드 신입 채용",
        companyName: String? = "인력개발원",
        recruitmentEndedAt: LocalDateTime? = deadline,
    ) = JobDiscordPayloadSnapshot(
        jobId = 10L,
        title = title,
        companyId = 1L,
        companyName = companyName,
        recruitmentEndedAt = recruitmentEndedAt,
    )

    private fun programSnapshot(bodyMarkdown: String? = "설명") =
        ProgramDiscordPayloadSnapshot(
            programId = 20L,
            title = "여름 캠프",
            bodyMarkdown = bodyMarkdown,
            eventStartedAt = deadline,
            eventEndedAt = deadline.plusDays(1),
        )

    private fun inquirySnapshot(requesterName: String? = "홍길동") =
        InquiryDiscordPayloadSnapshot(
            inquiryId = 30L,
            category = "SERVICE",
            requesterName = requesterName,
            createdAt = deadline,
        )
}
