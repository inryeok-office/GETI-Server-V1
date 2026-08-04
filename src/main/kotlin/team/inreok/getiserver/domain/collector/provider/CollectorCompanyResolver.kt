package team.inreok.getiserver.domain.collector.provider

/**
 * 정규화된 공고의 기업명(String)을 실제 Company Module의 `companyId`로 해석하기 위한 확장
 * 지점이다. `domain.company.query.CompanyQuery`는 ID 기준 조회만 공개하고 있어(Issue #56)
 * 기업명으로 기업을 찾는 공개 계약이 아직 없다. 이 Interface를 구현하는 Production Bean은
 * 아직 없으므로 [team.inreok.getiserver.domain.collector.service.impl.CollectorExecutionServiceImpl]는
 * 항상 해석 불가로 처리하고 해당 공고를 CollectionRunError로 기록한다(임의 기업 생성·고정 ID
 * 사용 금지, 최종 보고의 Blocker 참고). Test는 이 Interface의 Fake 구현으로 전체 성공 경로를
 * 검증한다.
 */
fun interface CollectorCompanyResolver {
    fun resolve(companyName: String): Long?
}
