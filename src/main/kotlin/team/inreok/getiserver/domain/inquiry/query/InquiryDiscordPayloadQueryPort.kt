package team.inreok.getiserver.domain.inquiry.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `notification` Module이 Discord로 보낼 문의 접수 알림의 의미 데이터를 읽는 공개 계약이다
 * (후속 요구사항 문서 §19·§28). 방향과 이유는
 * [team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort]와 같다.
 *
 * 문의 응답의 `discordDeliveryStatus`/`discordDelivered` 필드와, 그 값을 읽던 Module 내부 계약
 * `InquiryDiscordDeliveryQueryPort`(항상 `PENDING`만 반환)는 제거됐다 -- Discord 상태는 이제
 * `discord_deliveries`를 소유한 Notification의 별도 조회 API로만 제공한다
 * (`discord-event-wiring-plan.md` §6.2). 이 Port는 그 정리와 무관하게 다른 Module에 **문의
 * 내용**을 넘기는 공개 계약이다.
 *
 * ## 개인정보 최소화 (§40)
 *
 * Snapshot에 **이메일·전화번호·문의 본문·첨부파일 URL·인증 정보를 담지 않는다.** Discord
 * 문의 알림은 "새 문의가 접수됐다"는 관리자 Alert이지 문의 내용 전달 수단이 아니다.
 * GETI-Bot-V1의 `inquiryCreatedDataSchema`도 `.strict()`로 `inquiryId`/`category`/
 * `requesterName`/`submittedAt`만 받아, 그 외 필드가 섞이면 요청 자체가 거절된다 -- 서버와 Bot
 * 양쪽에서 이중으로 막힌다.
 *
 * 제목도 담지 않는다. Bot Schema에 대응 필드가 없고, 문의 제목에는 본인이 식별될 수 있는
 * 내용이 들어갈 수 있다.
 */
@NamedInterface
interface InquiryDiscordPayloadQueryPort {
    /** 존재하지 않으면 null을 반환한다. */
    fun findById(inquiryId: Long): InquiryDiscordPayloadSnapshot?
}

@NamedInterface
data class InquiryDiscordPayloadSnapshot(
    val inquiryId: Long,
    /** `InquiryType.name`. Bot Schema의 `category`에 대응한다. */
    val category: String,
    /** 작성자 이름. 탈퇴했거나 이름이 없으면 null이다. */
    val requesterName: String?,
    val createdAt: LocalDateTime?,
)
