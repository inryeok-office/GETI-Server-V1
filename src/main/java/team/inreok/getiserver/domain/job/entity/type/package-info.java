/**
 * search Domain(공개 검색 응답의 postingType/applicationMethod/status Field)이 job Domain의
 * 상태 값 Enum을 공유하기 위한 Spring Modulith Named Interface 선언이다. Kotlin은
 * package-level Annotation을 지원하지 않아 이 Java 전용 package-info.java로 선언한다
 * (operation.entity.type/company.entity.type의 선례와 동일한 방식, Issue #69).
 *
 * ApplicationModules.of(GetiServerApplication::class.java)의 ArchUnit 기반 구조 검증(ModularityTest,
 * src/test 전용)이 GetiServerApplication과 같은 Code Source(src/main)에서 Class를 Scan하므로
 * src/test가 아니라 src/main에 둔다. NamedInterface 자체는 검증(Test) 목적으로만 쓰이고
 * 실제 Domain 로직에는 관여하지 않는다. Annotation Type(org.springframework.modulith.NamedInterface)은
 * compileOnly(Compile 시점에만 필요, Runtime Classpath에는 포함되지 않음)로만 추가했다.
 */
@org.springframework.modulith.NamedInterface("type")
package team.inreok.getiserver.domain.job.entity.type;
