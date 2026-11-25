package com.meanderatlas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
    private static final Class<?>[] MUTABLE_COLLECTION_CLASSES = {
        ArrayList.class,
        LinkedList.class,
        HashSet.class,
        LinkedHashSet.class,
        TreeSet.class,
        HashMap.class,
        LinkedHashMap.class,
        TreeMap.class
    };
    private static final Set<String> INTERNAL_SUBPACKAGES =
            Set.of("domain", "application", "adapter");
    private static final Set<String> IGNORED_STANDARD_METHODS =
            Set.of("main", "equals", "hashCode", "toString");
    private static final Set<String> ENUM_GENERATED_METHODS = Set.of("values", "valueOf");
    private static final Set<String> FRAMEWORK_ENTRY_POINT_ANNOTATIONS =
            Set.of(
                    "GetMapping",
                    "PostMapping",
                    "PatchMapping",
                    "PutMapping",
                    "DeleteMapping",
                    "RequestMapping",
                    "ExceptionHandler",
                    "Bean",
                    "EventListener",
                    "ApplicationModuleListener",
                    "Scheduled",
                    "PostConstruct",
                    "PreDestroy");
    private static final int EXPECTED_SYNTHETIC_VIOLATIONS = 2;
    private static final int EXPECTED_MUTABLE_VIOLATIONS = 3;
    private static final int EXPECTED_DEAD_METHOD_VIOLATIONS = 1;

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
    void productionCodeEmbracesImmutableCollections() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .belongToAnyOf(MUTABLE_COLLECTION_CLASSES)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void immutabilityRuleRejectsMutableCollections() {
        JavaClasses syntheticClasses =
                new ClassFileImporter().importClasses(SyntheticMutableHolder.class);
        var result =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .belongToAnyOf(MUTABLE_COLLECTION_CLASSES)
                        .evaluate(syntheticClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(EXPECTED_MUTABLE_VIOLATIONS);
    }

    @Test
    void testsAvoidMockitoInFavorOfInMemoryFakes() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(MOCKITO_PACKAGE)
                .check(TEST_CLASSES);
    }

    @Test
    void productionMethodsAreCalledByProductionOrFrameworkEntryPoints() {
        methods().should(beCalledInProductionOrBeEntryPoint()).check(PRODUCTION_CLASSES);
    }

    @Test
    void deadCodeRuleRejectsUnusedProductionMethods() {
        JavaClasses syntheticClasses =
                new ClassFileImporter().importClasses(SyntheticDeadCodeHolder.class);
        var result =
                methods().should(beCalledInProductionOrBeEntryPoint()).evaluate(syntheticClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(EXPECTED_DEAD_METHOD_VIOLATIONS);
    }

    private static ArchCondition<JavaMethod> beCalledInProductionOrBeEntryPoint() {
        return new ArchCondition<>("be called in production code or be a framework entry point") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (isSkippableEntryPoint(method)
                        || hasDirectProductionUsage(method)
                        || implementsProductionContract(method)) {
                    return;
                }
                events.add(
                        SimpleConditionEvent.violated(
                                method,
                                method.getFullName()
                                        + " has no callers or references in production"));
            }
        };
    }

    private static boolean isSkippableEntryPoint(JavaMethod method) {
        JavaClass owner = method.getOwner();
        String name = method.getName();
        if (method.getModifiers().contains(JavaModifier.SYNTHETIC)
                || method.getModifiers().contains(JavaModifier.BRIDGE)
                || IGNORED_STANDARD_METHODS.contains(name)) {
            return true;
        }
        if (owner.isEnum() && ENUM_GENERATED_METHODS.contains(name)) {
            return true;
        }
        if (owner.isRecord()
                && method.getRawParameterTypes().isEmpty()
                && owner.tryGetField(name).isPresent()) {
            return true;
        }
        if (method.getAnnotations().stream()
                .anyMatch(annotation ->
                        FRAMEWORK_ENTRY_POINT_ANNOTATIONS.contains(
                                annotation.getRawType().getSimpleName()))) {
            return true;
        }
        return overridesExternalContract(method, owner);
    }

    private static boolean overridesExternalContract(JavaMethod method, JavaClass owner) {
        return java.util.stream.Stream.concat(
                        owner.getAllRawSuperclasses().stream(),
                        owner.getAllRawInterfaces().stream())
                .filter(ancestor -> !ancestor.getPackageName().startsWith(BASE_PACKAGE_PREFIX))
                .anyMatch(ancestor ->
                        ancestor.tryGetMethod(
                                        method.getName(),
                                        method.getRawParameterTypes().stream()
                                                .map(JavaClass::getName)
                                                .toArray(String[]::new))
                                .isPresent());
    }

    private static boolean hasDirectProductionUsage(JavaMethod method) {
        return method.getCallsOfSelf().stream().anyMatch(call -> !call.getOrigin().equals(method))
                || method.getReferencesToSelf().stream()
                        .anyMatch(ref -> !ref.getOrigin().equals(method));
    }

    private static boolean implementsProductionContract(JavaMethod method) {
        JavaClass owner = method.getOwner();
        String[] paramTypeNames =
                method.getRawParameterTypes().stream()
                        .map(JavaClass::getName)
                        .toArray(String[]::new);
        return java.util.stream.Stream.concat(
                        owner.getAllRawSuperclasses().stream(),
                        owner.getAllRawInterfaces().stream())
                .filter(ancestor -> ancestor.getPackageName().startsWith(BASE_PACKAGE_PREFIX))
                .map(ancestor -> ancestor.tryGetMethod(method.getName(), paramTypeNames))
                .flatMap(java.util.Optional::stream)
                .anyMatch(ArchitectureTest::hasDirectProductionUsage);
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

    static class SyntheticMutableHolder {

        List<String> mutableList() {
            return new ArrayList<>();
        }

        Set<String> mutableSet() {
            return new HashSet<>();
        }

        Map<String, String> mutableMap() {
            return new HashMap<>();
        }
    }

    static class SyntheticDeadCodeHolder {

        String usedHelper() {
            return "ok";
        }

        public static void main(String[] args) {
            new SyntheticDeadCodeHolder().usedHelper();
        }

        void unusedHelper() {}
    }
}
