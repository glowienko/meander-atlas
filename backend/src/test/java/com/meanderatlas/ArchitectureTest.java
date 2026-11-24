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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.meanderatlas";
    private static final String BASE_PACKAGE_PREFIX = BASE_PACKAGE + ".";
    private static final String DOCS_MODULES_RELATIVE_PATH = "../docs/modules";
    private static final String MODULE_DOC_PREFIX = "module-";
    private static final String MODULE_DOC_SUFFIX = ".adoc";
    private static final String SYNC_MODULE_DOCS_PROPERTY = "meanderatlas.syncModuleDocs";
    private static final String DEFAULT_SYNC_MODULE_DOCS = "true";
    private static final String DOMAIN_PACKAGE_PATTERN = "..domain..";
    private static final String FACADE_SUFFIX = "Facade";
    private static final String MOCKITO_PACKAGE = "org.mockito..";
    private static final String[] FORBIDDEN_DOMAIN_PACKAGES = {
        "org.springframework..", "java.sql.."
    };
    private static final String[] FORBIDDEN_STACK_PACKAGES = {
        "com.fasterxml.jackson..",
        "org.springframework.lang..",
        "jakarta.persistence..",
        "org.springframework.data..",
        "org.springframework.jdbc.core.simple..",
        "org.locationtech.jts..",
        "org.springframework.web.reactive.."
    };
    private static final Set<String> INTERNAL_SUBPACKAGES =
            Set.of("domain", "application", "adapter");
    private static final int EXPECTED_SYNTHETIC_VIOLATIONS = 2;

    private static final JavaClasses PRODUCTION_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE_PACKAGE);

    private static final JavaClasses TEST_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                    .importPackages(BASE_PACKAGE);

    @Test
    void verifiesApplicationModulesAndRegeneratesModuleDocs() throws IOException {
        ApplicationModules modules = ApplicationModules.of(MeanderAtlasApplication.class);
        modules.verify();

        if (!Boolean.parseBoolean(
                System.getProperty(SYNC_MODULE_DOCS_PROPERTY, DEFAULT_SYNC_MODULE_DOCS))) {
            return;
        }

        Path docsDir = Path.of(DOCS_MODULES_RELATIVE_PATH).toAbsolutePath().normalize();
        Files.createDirectories(docsDir);
        try (var existing = Files.list(docsDir)) {
            for (Path path : existing.toList()) {
                Path fileName = path.getFileName();
                if (fileName != null
                        && fileName.toString().startsWith(MODULE_DOC_PREFIX)
                        && fileName.toString().endsWith(MODULE_DOC_SUFFIX)) {
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
                .resideInAPackage(DOMAIN_PACKAGE_PATTERN)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_DOMAIN_PACKAGES)
                .allowEmptyShould(false)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void facadePublicMethodsNeverExposeInternalModuleTypes() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith(FACADE_SUFFIX)
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
                        .haveSimpleNameEndingWith(FACADE_SUFFIX)
                        .and()
                        .arePublic()
                        .should(notExposeInternalModuleTypes())
                        .evaluate(syntheticClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(EXPECTED_SYNTHETIC_VIOLATIONS);
    }

    @Test
    void productionCodeUsesPinnedBoot4StackOnly() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_STACK_PACKAGES)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void testsAvoidMockitoInFavorOfInMemoryFakes() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(MOCKITO_PACKAGE)
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
        if (!pkg.startsWith(BASE_PACKAGE_PREFIX)) {
            return false;
        }
        for (String segment : INTERNAL_SUBPACKAGES) {
            if (pkg.endsWith("." + segment) || pkg.contains("." + segment + ".")) {
                return true;
            }
        }
        return false;
    }

    static class SyntheticLeakyFacade {

        public List<AtlasHealthRepository> leakyReturn() {
            return List.of();
        }

        public void leakyParam(List<AtlasHealthRepository> repositories) {}
    }
}
