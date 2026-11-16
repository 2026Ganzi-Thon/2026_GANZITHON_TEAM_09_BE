package com.haengunping;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 설계 규칙을 코드로 지킨다(D-03, D-08). 규칙을 바꾸려면 decisions.md를 먼저 고친다.
 */
@AnalyzeClasses(packages = "com.haengunping", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.haengunping";
    private static final List<String> MODULES = List.of(
            "account", "group", "mood", "mission", "ping", "character", "notification", "check", "help", "demo");

    @ArchTest
    static final ArchRule D08_날짜는_주입한_Clock으로만_구한다 = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".common.time..")
            .should().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(Instant.class, "now")
            .because("자정 확정·일요일 점검·시연 시계가 같은 시계를 봐야 한다. LocalDate.now(clock)처럼 Clock을 넘긴다(D-08)");

    @ArchTest
    static final ArchRule D03_common은_기능_모듈에_의존하지_않는다 = noClasses()
            .that().resideInAPackage(ROOT + ".common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    MODULES.stream().map(m -> ROOT + "." + m + "..").toArray(String[]::new))
            .because("common은 모든 모듈의 바닥이다. 거꾸로 의존하면 순환이 생긴다");

    @ArchTest
    static void D03_다른_모듈의_내부_패키지는_쓰지_않는다(JavaClasses classes) {
        for (String module : MODULES) {
            String base = ROOT + "." + module;
            noClasses()
                    .that().resideOutsideOfPackage(base + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            base + ".api..", base + ".application..", base + ".domain..", base + ".infra..")
                    .because(module + " 모듈은 루트 패키지의 XxxQuery·이벤트로만 만난다(D-03)")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }
}
