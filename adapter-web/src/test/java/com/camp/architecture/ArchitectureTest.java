package com.camp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 컴파일된 클래스를 읽어 의존성 규칙 위반을 찾는다. build.gradle 수정으로 컴파일 에러를 없앤 경우가 검사 대상이다. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.camp");
    }

    private static final List<String> MODULE_ROOTS = List.of(
            "com.camp.domain",
            "com.camp.application",
            "com.camp.infra",
            "com.camp.adapter.web",
            "com.camp.adapter.mcp",
            "com.camp.adapter.batch");

    @Test
    @DisplayName("전 모듈이 스캔 대상에 포함된다")
    void allModulesAreScanned() {
        // 스캔이 비면 아래 규칙들이 검사할 클래스 없이 통과해버린다. 그 상태를 실패로 만든다.
        Set<String> packages = classes.stream().map(JavaClass::getPackageName).collect(toSet());

        for (String root : MODULE_ROOTS) {
            assertThat(packages)
                    .as("모듈 %s 에 스캔된 클래스가 없다", root)
                    .anyMatch(p -> p.equals(root) || p.startsWith(root + "."));
        }
    }

    @Test
    @DisplayName("domain 은 Spring 을 참조하지 않는다")
    void domainDoesNotDependOnSpring() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .check(classes);
    }

    @Test
    @DisplayName("domain 은 JPA 를 참조하지 않는다")
    void domainDoesNotDependOnJpa() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..")
                .check(classes);
    }

    @Test
    @DisplayName("domain 은 Jackson 을 참조하지 않는다")
    void domainDoesNotDependOnJackson() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.fasterxml..")
                .check(classes);
    }

    @Test
    @DisplayName("domain 은 바깥 계층을 참조하지 않는다")
    void domainDoesNotDependOnOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.camp.application..", "com.camp.adapter..", "com.camp.infra..")
                .check(classes);
    }

    @Test
    @DisplayName("application 은 adapter 와 infra 를 참조하지 않는다")
    void applicationDoesNotDependOnAdaptersOrInfra() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.camp.adapter..", "com.camp.infra..")
                .check(classes);
    }

    @Test
    @DisplayName("adapter 는 infra 를 참조하지 않는다")
    void adaptersDoNotDependOnInfra() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.camp.infra..")
                .check(classes);
    }

    @Test
    @DisplayName("infra 는 adapter 를 참조하지 않는다")
    void infraDoesNotDependOnAdapters() {
        noClasses()
                .that()
                .resideInAPackage("com.camp.infra..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.camp.adapter..")
                .check(classes);
    }
}
