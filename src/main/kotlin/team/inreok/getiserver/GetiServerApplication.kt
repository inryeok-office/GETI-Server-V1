package team.inreok.getiserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// CollectorScheduler(domain.collector.scheduler)의 일일 수집 @Scheduled 실행을 위해 필요하다(Issue #62).
@EnableScheduling
@SpringBootApplication
class GetiServerApplication

fun main(args: Array<String>) {
    runApplication<GetiServerApplication>(*args)
}
