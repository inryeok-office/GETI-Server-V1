package team.inreok.getiserver.domain.notification.entity.type

/**
 * Bot이 렌더링할 메시지 종류다(후속 요구사항 문서 §11). 서버는 Discord Embed를 직접 만들지
 * 않고 이 Template 이름과 의미 데이터만 넘긴다(§3).
 *
 * 값과 직렬화 문자열이 GETI-Bot-V1의 `TEMPLATES`(`src/internal-api/types.ts`)와 정확히 일치해야
 * 한다. 나아가 Bot은 `refineTemplateConsistency`로 **targetType/action/template 세 값의 조합**까지
 * 검증하므로(어긋나면 `400 INVALID_REQUEST`, 재시도 불가), 각 Template이 자신에게 유효한
 * [targetType]과 [action]을 직접 들고 있게 해서 잘못된 조합을 애초에 만들 수 없게 한다.
 * Bot의 `TEMPLATE_INFO` Map과 1:1로 대응한다.
 */
enum class DiscordMessageTemplate(
    val targetType: DiscordDeliveryTargetType,
    val action: DiscordDeliveryAction,
) {
    JOB_PUBLISHED(DiscordDeliveryTargetType.JOB, DiscordDeliveryAction.CREATE),
    JOB_UPDATED(DiscordDeliveryTargetType.JOB, DiscordDeliveryAction.UPDATE),
    JOB_CLOSED(DiscordDeliveryTargetType.JOB, DiscordDeliveryAction.CLOSE_NOTICE),
    JOB_DELETED(DiscordDeliveryTargetType.JOB, DiscordDeliveryAction.DELETE_NOTICE),

    PROGRAM_PUBLISHED(DiscordDeliveryTargetType.PROGRAM, DiscordDeliveryAction.CREATE),
    PROGRAM_UPDATED(DiscordDeliveryTargetType.PROGRAM, DiscordDeliveryAction.UPDATE),
    PROGRAM_CLOSED(DiscordDeliveryTargetType.PROGRAM, DiscordDeliveryAction.CLOSE_NOTICE),
    PROGRAM_DELETED(DiscordDeliveryTargetType.PROGRAM, DiscordDeliveryAction.DELETE_NOTICE),

    INQUIRY_CREATED(DiscordDeliveryTargetType.INQUIRY, DiscordDeliveryAction.CREATE),
}
