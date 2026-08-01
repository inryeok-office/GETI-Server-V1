plugins {
    kotlin("jvm") version "2.4.10"
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
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:testcontainers-junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:testcontainers-postgresql")
    "integrationTestImplementation"("com.redis:testcontainers-redis")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Docker(Testcontainers)가 필요한 Persistence 통합 테스트를 실행한다."
        group = "verification"
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        useJUnitPlatform()
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
}
