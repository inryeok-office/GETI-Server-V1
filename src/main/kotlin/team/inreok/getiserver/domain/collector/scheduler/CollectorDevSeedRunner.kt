package team.inreok.getiserver.domain.collector.scheduler

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.company.external.CompanyExternalImportCommand
import team.inreok.getiserver.domain.company.external.CompanyExternalImportUseCase
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertCommand
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertUseCase
import team.inreok.getiserver.domain.job.upsert.JobImportOutcome
import java.time.LocalDateTime

/**
 * `COLLECTOR_SEED_ENABLED=true`(기본값 develop Profile)일 때만 화면 검증용 개발 Fixture 공고를
 * 만든다(Issue #62). 외부 API를 호출하지 않고 실제 Job/Company 공개 계약(`CollectedJobUpsertUseCase`,
 * `CompanyExternalImportUseCase`)만 거쳐, Collector가 실제로 수집한 공고와 동일한 경로로
 * 저장된다. `DEV-SEED-` Prefix가 붙은 고정 `externalJobId`를 사용해 재배포돼도 값이 그대로면
 * UNCHANGED로 처리되어 중복 생성되지 않는다(날짜도 `now()` 상대값이 아니라 고정 값을 써서
 * 재배포마다 내용이 달라지지 않게 한다).
 */
@Component
class CollectorDevSeedRunner(
    private val companyExternalImportUseCase: CompanyExternalImportUseCase,
    private val collectedJobUpsertUseCase: CollectedJobUpsertUseCase,
    @param:Value("\${app.collector.seed.enabled:false}") private val seedEnabled: Boolean,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!seedEnabled) return

        val results = SEED_JOBS.map(::upsertSeedJob)
        val counts = results.groupingBy { it }.eachCount()
        log.info("Collector 개발용 Seed 적재 완료: {}건 처리, outcome={}", results.size, counts)
    }

    private fun upsertSeedJob(seed: SeedJob): JobImportOutcome {
        val company =
            companyExternalImportUseCase.findOrCreateExternal(
                CompanyExternalImportCommand(companyName = seed.companyName, sourceCode = seed.sourceCode.name),
            )
        val result =
            collectedJobUpsertUseCase.upsert(
                CollectedJobUpsertCommand(
                    companyId = company.companyId,
                    sourceName = seed.sourceCode.name,
                    externalJobId = seed.externalJobId,
                    title = seed.title,
                    content = seed.content,
                    externalUrl = seed.externalUrl,
                    startDate = seed.startDate,
                    endDate = seed.endDate,
                    publish = seed.content != null,
                ),
            )
        return result.outcome
    }

    private data class SeedJob(
        val sourceCode: JobSourceCode,
        val externalJobId: String,
        val title: String,
        val companyName: String,
        val content: String?,
        val externalUrl: String?,
        val startDate: LocalDateTime?,
        val endDate: LocalDateTime?,
    )

    private companion object {
        private val log = LoggerFactory.getLogger(CollectorDevSeedRunner::class.java)

        // "모집 중"/"마감 임박"/"마감" 상태를 실행 시점의 now()가 아니라 고정된 날짜로 표현한다.
        // now() 상대값을 쓰면 재배포 때마다 날짜가 달라져 UNCHANGED가 아니라 매번 UPDATE로 잡힌다.
        private val OPEN_START = LocalDateTime.of(2026, 1, 1, 0, 0)
        private val OPEN_END = LocalDateTime.of(2027, 12, 31, 23, 59)
        private val CLOSING_SOON_START = LocalDateTime.of(2026, 1, 1, 0, 0)
        private val CLOSING_SOON_END = LocalDateTime.of(2026, 8, 10, 23, 59)
        private val CLOSED_START = LocalDateTime.of(2025, 1, 1, 0, 0)
        private val CLOSED_END = LocalDateTime.of(2025, 6, 30, 23, 59)

        val SEED_JOBS =
            listOf(
                SeedJob(
                    sourceCode = JobSourceCode.MMA,
                    externalJobId = "DEV-SEED-MMA-001",
                    title = "[개발용] 병역일터 모집 중 공고",
                    companyName = "개발용 가상기업 병역일터A",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/mma-001",
                    startDate = OPEN_START,
                    endDate = OPEN_END,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.MMA,
                    externalJobId = "DEV-SEED-MMA-002",
                    title = "[개발용] 병역일터 마감 임박 공고",
                    companyName = "개발용 가상기업 병역일터B",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/mma-002",
                    startDate = CLOSING_SOON_START,
                    endDate = CLOSING_SOON_END,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.JOB_ALIO,
                    externalJobId = "DEV-SEED-JOB_ALIO-001",
                    title = "[개발용] 공공기관 채용정보 마감 공고",
                    companyName = "개발용 가상기업 알리오A",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/job-alio-001",
                    startDate = CLOSED_START,
                    endDate = CLOSED_END,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.JOB_ALIO,
                    externalJobId = "DEV-SEED-JOB_ALIO-002",
                    title = "[개발용] 공공기관 채용정보 선택 필드 누락 공고",
                    companyName = "개발용 가상기업 알리오B",
                    // 본문(content)이 없는 PARTIAL 품질 공고를 흉내낸다 — publish=false가 되어 DRAFT로 남는다.
                    content = null,
                    externalUrl = "https://example.com/dev-seed/job-alio-002",
                    startDate = OPEN_START,
                    endDate = null,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.CLEAN_EYE,
                    externalJobId = "DEV-SEED-CLEAN_EYE-001",
                    title = "[개발용] 클린아이 모집 중 공고",
                    companyName = "개발용 가상기업 클린아이A",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/clean-eye-001",
                    startDate = OPEN_START,
                    endDate = OPEN_END,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.CLEAN_EYE,
                    externalJobId = "DEV-SEED-CLEAN_EYE-002",
                    title = "[개발용] 클린아이 마감 공고",
                    companyName = "개발용 가상기업 클린아이B",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/clean-eye-002",
                    startDate = CLOSED_START,
                    endDate = CLOSED_END,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.NARA_ILTEO,
                    externalJobId = "DEV-SEED-NARA_ILTEO-001",
                    title = "[개발용] 나라일터 모집 중 공고",
                    companyName = "개발용 가상기업 나라일터A",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/nara-ilteo-001",
                    startDate = OPEN_START,
                    endDate = OPEN_END,
                ),
                SeedJob(
                    sourceCode = JobSourceCode.NARA_ILTEO,
                    externalJobId = "DEV-SEED-NARA_ILTEO-002",
                    title = "[개발용] 나라일터 마감 임박 공고",
                    companyName = "개발용 가상기업 나라일터B",
                    content = "화면 검증용 개발 Fixture 공고입니다. 실제 채용공고가 아닙니다.",
                    externalUrl = "https://example.com/dev-seed/nara-ilteo-002",
                    startDate = CLOSING_SOON_START,
                    endDate = CLOSING_SOON_END,
                ),
            )
    }
}
