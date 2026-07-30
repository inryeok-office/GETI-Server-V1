package team.inreok.getiserver

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Entity/Repository가 반드시 domain/{domain-name}/entity, domain/{domain-name}/repository 아래에만
 * 있어야 한다는 규칙을 ArchUnit(spring-modulith-starter-test가 Test Classpath에 Transitive로 이미
 * 포함하고 있어 새 Dependency를 추가하지 않았다)으로 강제한다.
 *
 * Domain 간 순환 의존성, 다른 Module의 비공개(Named Interface로 노출되지 않은) 내부 구현 참조 금지는
 * [ModularityTest]의 `modules.verify()`(Spring Modulith)가 이미 검증한다. 이 Class는 그 검증이
 * 다루지 않는 "어느 Package에 있어야 하는가"라는 Naming/배치 규칙만 다룬다.
 */
class PackageArchitectureTest {
    private val importedClasses = ClassFileImporter().importPackages("team.inreok.getiserver")

    @Test
    fun `모든 JPA Entity는 domain 하위 도메인의 entity Package 아래에 있다`() {
        classes()
            .that()
            .areAnnotatedWith(Entity::class.java)
            .should()
            .resideInAPackage(DOMAIN_ENTITY_PACKAGE_PATTERN)
            .check(importedClasses)
    }

    @Test
    fun `모든 Repository는 domain 하위 도메인의 repository Package 아래에 있다`() {
        classes()
            .that()
            .areInterfaces()
            .and()
            .areAssignableTo(JpaRepository::class.java)
            .should()
            .resideInAPackage(DOMAIN_REPOSITORY_PACKAGE_PATTERN)
            .check(importedClasses)
    }

    @Test
    fun `global Package에는 JPA Entity가 없다`() {
        noClasses()
            .that()
            .resideInAPackage(GLOBAL_PACKAGE_PATTERN)
            .should()
            .beAnnotatedWith(Entity::class.java)
            .check(importedClasses)
    }

    @Test
    fun `global Package에는 Repository가 없다`() {
        noClasses()
            .that()
            .resideInAPackage(GLOBAL_PACKAGE_PATTERN)
            .and()
            .areInterfaces()
            .should()
            .beAssignableTo(JpaRepository::class.java)
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `Root 전역 entity 또는 repository Package, domain 바로 아래의 공용 entity·repository Package를 사용하지 않는다`() {
        val forbiddenPackages =
            arrayOf(
                "team.inreok.getiserver.entity",
                "team.inreok.getiserver.repository",
                "team.inreok.getiserver.domain.entity",
                "team.inreok.getiserver.domain.repository",
            )

        noClasses()
            .that()
            .resideInAnyPackage(*forbiddenPackages)
            .should()
            .beAnnotatedWith(Entity::class.java)
            .orShould()
            .beAssignableTo(JpaRepository::class.java)
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `global은 domain Package에 의존하지 않는다`() {
        noClasses()
            .that()
            .resideInAPackage(GLOBAL_PACKAGE_PATTERN)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(DOMAIN_ANY_PACKAGE_PATTERN)
            .check(importedClasses)
    }

    companion object {
        private const val ROOT_PACKAGE = "team.inreok.getiserver"
        private const val DOMAIN_ENTITY_PACKAGE_PATTERN = "$ROOT_PACKAGE.domain.*.entity.."
        private const val DOMAIN_REPOSITORY_PACKAGE_PATTERN = "$ROOT_PACKAGE.domain.*.repository.."
        private const val DOMAIN_ANY_PACKAGE_PATTERN = "$ROOT_PACKAGE.domain.."
        private const val GLOBAL_PACKAGE_PATTERN = "$ROOT_PACKAGE.global.."
    }
}
