package com.meanderatlas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.meanderatlas");

    @Test
    void verifiesApplicationModulesAndRegeneratesModuleDocs() throws IOException {
        ApplicationModules modules = ApplicationModules.of(MeanderAtlasApplication.class);
        modules.verify();

        Path docsDir = Path.of("../docs/modules").toAbsolutePath().normalize();
        Files.createDirectories(docsDir);
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
    void facadePublicMethodsNeverExposeDomainTypes() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Facade")
                .and()
                .arePublic()
                .should(notExposeDomainTypes())
                .allowEmptyShould(false)
                .check(PRODUCTION_CLASSES);
    }

    private static ArchCondition<JavaMethod> notExposeDomainTypes() {
        return new ArchCondition<>("not expose domain types in parameters or return type") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (isDomainType(method.getRawReturnType())) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName()
                                            + " returns domain type "
                                            + method.getRawReturnType().getName()));
                }
                for (JavaClass parameterType : method.getRawParameterTypes()) {
                    if (isDomainType(parameterType)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        method,
                                        method.getFullName()
                                                + " accepts domain type "
                                                + parameterType.getName()));
                    }
                }
            }
        };
    }

    private static boolean isDomainType(JavaClass type) {
        return type.getPackageName().contains(".domain");
    }
}
