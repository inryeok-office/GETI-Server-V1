package team.inreok.getiserver

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy
import org.springframework.modulith.core.JavaPackage
import java.util.stream.Stream

/**
 * `domain`의 각 Direct Subpackage(`domain.member`, `domain.job` 등)를 개별 Application Module로,
 * `global`은 그대로 하나의 Module로 인식시키는 Spring Modulith Detection Strategy다.
 * 기본 전략(`direct-sub-packages`)은 Root Package의 Direct Subpackage만 Module로 인식하므로
 * `domain` 자체가 하나의 거대한 Module로 잡힌다 — `domain.{domain-name}` 단위로 검증하려면
 * 이 Strategy가 필요하다.
 *
 * `spring.modulith.detection-strategy` 설정 값(Fully Qualified Class Name)으로 지정되며
 * `BeanUtils.instantiateClass`가 Public 기본 생성자로 직접 생성한다(Spring Bean이 아니다).
 */
class DomainApplicationModuleDetectionStrategy : ApplicationModuleDetectionStrategy {
    override fun getModuleBasePackages(basePackage: JavaPackage): Stream<JavaPackage> =
        basePackage.directSubPackages.stream().flatMap { subPackage ->
            if (subPackage.localName == DOMAIN_PACKAGE_LOCAL_NAME) {
                subPackage.directSubPackages.stream()
            } else {
                Stream.of(subPackage)
            }
        }

    companion object {
        private const val DOMAIN_PACKAGE_LOCAL_NAME = "domain"
    }
}
