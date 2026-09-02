plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("com.diffplug.spotless") version "8.9.0"
    id("dev.detekt") version "2.0.0-alpha.3"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

group = "team.inreok"
version = "0.0.1-SNAPSHOT"
description = "GETI-Server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
        // Spring Boot Dependency Management가 spring-boot-dependencies POM 안에서
        // testcontainers-bom을 Import하지만, io.spring.dependency-management Plugin은
        // 이런 중첩 Import를 항상 전파하지 않는다(실측 결과 org.testcontainers:postgresql,
        // org.testcontainers:junit-jupiter Version이 해석되지 않음). Spring Boot 4.1.0이
        // 관리하는 것과 동일한 Version(testcontainers.version)을 직접 Import해 해결한다.
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")

        // AWS SDK v2(S3). Spring Boot Dependency Management가 AWS SDK를 관리하지 않으므로
        // BOM을 직접 Import해 s3/s3-presigner와 전이 의존(http-client, auth 등) Version을
        // 한곳에서 맞춘다(File 도메인, Issue #85).
        mavenBom("software.amazon.awssdk:bom:2.51.3")
    }
}

sourceSets {
    create("integrationTest") {
        // src/integrationTest/{kotlin,resources}는 Gradle의 기본 Convention이라 별도로 srcDir를
        // 추가하지 않는다(추가하면 동일 Directory가 중복 등록되어 Resource 처리 시 오류가 난다).
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    // Spring Modulith: Application Module 경계 검증(Test 전용, Production Runtime 미사용)
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    // Spring Modulith Named Interface(@NamedInterface) Annotation. domain.collector가 domain.operation의
    // 공개 Type(OperationStatus)을 참조하기 위해 src/main/java의 package-info.java에서만 사용한다.
    // compileOnly라 Runtime Classpath와 최종 Artifact에는 포함되지 않는다(ArchUnit 기반 구조 검증은
    // Class 파일의 Annotation 메타데이터만 읽고, 이 검증은 spring-modulith-starter-test가 이미 있는
    // src/test에서만 수행한다).
    compileOnly("org.springframework.modulith:spring-modulith-api")

    // Spring Web / Validation / Actuator
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // API 문서화(Swagger UI, OpenAPI 3). springdoc-openapi 3.0.3은 Spring Boot 4(Jakarta EE 9)를
    // 공식 지원하는 Version이다(springdoc.org 확인). Production 노출 여부는 `springdoc.*`
    // Property로 제어하고(application-prod.yaml), 별도 Downgrade나 Springfox는 도입하지 않는다.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // Security: JWT 인증(Filter Chain). OAuth Token 교환/UserInfo 조회는 RestClient로 직접 구현하고
    // Spring OAuth2 Client 자동 구성(ClientRegistrationRepository 기반 Flow)은 쓰지 않으므로
    // spring-boot-starter-oauth2-client는 두지 않는다(코드 리뷰 Minor 반영).
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Spring Boot 4.x는 RestClientAutoConfiguration을 spring-boot-autoconfigure가 아니라 이 별도
    // 모듈로 분리했다. OAuthLoginServiceImpl이 주입받는 RestClient.Builder Bean은 이 Dependency가
    // 있어야 자동 등록된다(없으면 Application Context 자체가 기동되지 않는다).
    implementation("org.springframework.boot:spring-boot-restclient")

    // JWT 발급/검증(jjwt). Jackson 3.x(tools.jackson) 기반인 프로젝트와 Jackson 2.x가 섞이지
    // 않도록 jjwt-jackson 대신 jjwt-gson을 사용한다.
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")

    // Persistence: PostgreSQL(JPA/Hibernate) + Flyway, Redis(Lettuce)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Search(Elasticsearch + Nori, Issue #69). Version은 Spring Boot Dependency Management가
    // 관리하는 값을 그대로 쓰고 임의로 고정하지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

    // Object Storage(S3 Compatible). Local은 MinIO, 운영은 AWS S3를 쓰지만 Adapter는 하나이며
    // endpoint/path-style 설정으로만 구분한다(Issue #85). S3Client와 S3Presigner가 모두 필요해
    // s3 Artifact 하나로 충분하다(Presigner는 s3에 포함되어 있다).
    implementation("software.amazon.awssdk:s3")

    // 지원자 PROFILE/ANSWERS 문서(XLSX) 생성(Issue #218). Apache POI OOXML은 Apache-2.0
    // License이며 Java 25 및 현재 Spring Boot/Jackson 3 조합과 독립적으로 동작하는 문서 생성
    // Library다. XLSX 구조를 테스트에서 실제로 다시 열어 검증할 수 있어 CSV보다 계약을 명확히
    // 유지할 수 있다.
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    // 업로드 파일의 실제 형식(Magic Number) 탐지. 확장자·선언 MIME만 믿으면 이름만 바꾼 실행
    // 파일을 막을 수 없다(Issue #85). 문서 본문을 파싱하는 tika-parsers는 쓰지 않는다 —
    // 형식 탐지에는 tika-core만 있으면 되고 Dependency도 훨씬 가볍다. Spring Boot Dependency
    // Management가 Tika를 관리하지 않아 Version을 직접 고정한다(4.0.0은 아직 beta라 제외).
    implementation("org.apache.tika:tika-core:4.0.0")

    // Kotlin / Jackson
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")

    // Integration Test(Docker/Testcontainers 필요, src/integrationTest 전용)
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-data-redis-test")
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-data-elasticsearch-test")
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:testcontainers-junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:testcontainers-postgresql")
    "integrationTestImplementation"("com.redis:testcontainers-redis")
    "integrationTestImplementation"("org.testcontainers:testcontainers-elasticsearch")
    // MinIO Container. Version은 위 testcontainers-bom(2.0.5)이 관리하므로 고정하지 않는다.
    "integrationTestImplementation"("org.testcontainers:testcontainers-minio")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Docker(Testcontainers)가 필요한 Persistence 통합 테스트를 실행한다."
        group = "verification"
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        useJUnitPlatform()
        // Integration Test는 각 Test가 필요한 Scheduler Method를 직접 호출하거나 명시적으로
        // 검증한다. 운영 @Scheduled Thread가 Test 데이터와 경합하면 테스트가 검증하는 호출과
        // 무관하게 상태를 먼저 바꿀 수 있으므로, Application의 Scheduling 인프라만 이 Task에서
        // 비활성화한다. 기본값은 true라 운영 실행 동작은 바뀌지 않는다.
        systemProperty("app.scheduling.enabled", "false")
        shouldRunAfter(tasks.test)
    }

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "gradle/**/*.gradle.kts")
        ktlint("1.8.0")
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("config/detekt/detekt.yml"))
}

kover {
    currentProject {
        instrumentation {
            // integrationTest는 Docker(Testcontainers)가 필요해 check/build에서 분리했다.
            // Kover가 기본으로 모든 Test Task를 Coverage 측정에 포함시켜 check가 이 Task에
            // 의존하게 되므로, 명시적으로 제외해 test/check가 Docker 없이 실행되게 한다.
            disabledForTestTasks.add("integrationTest")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Gradle Test Worker의 기본 Heap은 512MB다. File 도메인이 AWS SDK(Class 그래프가 크다)를
    // 들이고 @WebMvcTest Slice가 늘면서 이 한계를 넘어 "Java heap space"로 Test JVM이 죽었다
    // (Issue #85 실측). Spring Context를 여러 개 띄우는 Test가 계속 늘어날 것이라 넉넉히 잡는다.
    maxHeapSize = "2g"
}
