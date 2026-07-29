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
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.4.1")
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
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
    testAnnotationProcessor("org.projectlombok:lombok")

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
