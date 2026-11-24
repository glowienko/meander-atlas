package com.meanderatlas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.meanderatlas");

    private static final JavaClasses TEST_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                    .importPackages("com.meanderatlas");

    @Test
    void verifiesApplicationModulesAndRegeneratesModuleDocs() throws IOException {
        ApplicationModules modules = ApplicationModules.of(MeanderAtlasApplication.class);
        modules.verify();

        if (!Boolean.parseBoolean(System.getProperty("meanderatlas.syncModuleDocs", "true"))) {
            return;
        }

        Path docsDir = Path.of("../docs/modules").toAbsolutePath().normalize();
        Files.createDirectories(docsDir);
        try (var existing = Files.list(docsDir)) {
            for (Path path : existing.toList()) {
                Path fileName = path.getFileName();
                if (fileName != null
                        && fileName.toString().startsWith("module-")
                        && fileName.toString().endsWith(".adoc")) {
                    Files.deleteIfExists(path);
                }
            }
        }
        new Documenter(modules, Documenter.Options.defaults().withOutputFolder(docsDir.toString()))
                .writeModuleCanvases(Documenter.CanvasOptions.defaults());
    }

    @Test
    void domainPackagesRemainFreeOfSpringAndSqlDependencies() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "java.sql..")
                .allowEmptyShould(false)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void facadePublicMethodsNeverExposeInternalModuleTypes() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Facade")
                .and()
                .arePublic()
                .should(notExposeInternalModuleTypes())
                .allowEmptyShould(false)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void facadeRuleRejectsGenericInternalReturnAndParameterTypes() {
        JavaClasses syntheticClasses =
                new ClassFileImporter().importClasses(SyntheticLeakyFacade.class);
        var result =
                methods()
                        .that()
                        .areDeclaredInClassesThat()
                        .haveSimpleNameEndingWith("Facade")
                        .and()
                        .arePublic()
                        .should(notExposeInternalModuleTypes())
                        .evaluate(syntheticClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(2);
    }

    @Test
    void productionCodeUsesPinnedBoot4StackOnly() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.fasterxml.jackson..",
                        "org.springframework.lang..",
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.springframework.jdbc.core.simple..",
                        "org.locationtech.jts..",
                        "org.springframework.web.reactive..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void testsAvoidMockitoInFavorOfInMemoryFakes() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.mockito..")
                .check(TEST_CLASSES);
    }

    private static ArchCondition<JavaMethod> notExposeInternalModuleTypes() {
        return new ArchCondition<>(
                "not expose internal module types in parameters or return type") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaClass returnType : method.getReturnType().getAllInvolvedRawTypes()) {
                    if (isInternalModuleType(returnType)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        method,
                                        method.getFullName()
                                                + " returns internal type "
                                                + returnType.getName()));
                    }
                }
                for (JavaType parameterType : method.getParameterTypes()) {
                    for (JavaClass involved : parameterType.getAllInvolvedRawTypes()) {
                        if (isInternalModuleType(involved)) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            method,
                                            method.getFullName()
                                                    + " accepts internal type "
                                                    + involved.getName()));
                        }
                    }
                }
            }
        };
    }

    private static boolean isInternalModuleType(JavaClass type) {
        String pkg = type.getPackageName();
        return pkg.startsWith("com.meanderatlas.")
                && (matchesSubpackage(pkg, "domain")
                        || matchesSubpackage(pkg, "application")
                        || matchesSubpackage(pkg, "adapter"));
    }

    private static boolean matchesSubpackage(String pkg, String segment) {
        return pkg.endsWith("." + segment) || pkg.contains("." + segment + ".");
    }

    static class SyntheticLeakyFacade {

        public List<AtlasHealthRepository> leakyReturn() {
            return List.of();
        }

        public void leakyParam(List<AtlasHealthRepository> repositories) {}
    }
}
