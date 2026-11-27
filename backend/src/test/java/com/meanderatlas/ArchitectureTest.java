package com.meanderatlas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitCallTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.web.bind.annotation.GetMapping;

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
    private static final String APPLICATION_PACKAGE_PATTERN = "..application..";
    private static final String ADAPTER_PACKAGE_PATTERN = "..adapter..";
    private static final Set<String> FORBIDDEN_THREAD_MEMBERS =
            Set.of(
                    "sleep",
                    "ofVirtual",
                    "ofPlatform",
                    "startVirtualThread",
                    JavaConstructor.CONSTRUCTOR_NAME);
    private static final DescribedPredicate<JavaCall<?>> BLOCKING_THREADING_OR_SYSTEM_CLOCK =
            DescribedPredicate.describe(
                    "Thread.sleep/TimeUnit.sleep/Object.wait, raw Thread creation, Executors"
                            + " factories, or Clock-less now()",
                    call -> isBlockingThreadingOrSystemClock(call.getTarget()));
    private static final int EXPECTED_BLOCKING_VIOLATIONS = 10;
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
    void applicationLayerNeverDependsOnAdapters() {
        noClasses()
                .that()
                .resideInAPackage(APPLICATION_PACKAGE_PATTERN)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ADAPTER_PACKAGE_PATTERN)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void productionCodeNeverBlocksSpawnsRawThreadsOrReadsTheSystemClock() {
        noClasses()
                .should()
                .callCodeUnitWhere(BLOCKING_THREADING_OR_SYSTEM_CLOCK)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void blockingRuleRejectsSleepWaitRawThreadsExecutorsAndClocklessNow() {
        JavaClasses syntheticClasses =
                new ClassFileImporter().importClasses(SyntheticBlockingHolder.class);
        var result =
                noClasses()
                        .should()
                        .callCodeUnitWhere(BLOCKING_THREADING_OR_SYSTEM_CLOCK)
                        .evaluate(syntheticClasses);

        assertThat(result.getFailureReport().getDetails()).hasSize(EXPECTED_BLOCKING_VIOLATIONS);
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
    void deadCodeRuleRejectsUnusedProductionMethodsAndBridgeOnlyCalls() {
        JavaClasses syntheticClasses =
                new ClassFileImporter()
                        .importClasses(
                                SyntheticDeadCodeHolder.class,
                                SyntheticGenericBridgeInterface.class,
                                SyntheticBridgeDeadCodeImpl.class);
        var result =
                methods().should(beCalledInProductionOrBeEntryPoint()).evaluate(syntheticClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .hasSize(EXPECTED_DEAD_METHOD_VIOLATIONS + 2);
    }

    @Test
    void deadCodeRuleAcceptsContractsGenericOverridesMethodRefsRecordsAndAnnotations() {
        JavaClasses syntheticClasses =
                new ClassFileImporter()
                        .importClasses(
                                SyntheticConcreteTargetInterface.class,
                                SyntheticConcreteService.class,
                                SyntheticInterfaceTarget.class,
                                SyntheticInterfaceService.class,
                                SyntheticCleanComparable.class,
                                SyntheticRecord.class,
                                SyntheticCleanEntryPoint.class);
        var result =
                methods().should(beCalledInProductionOrBeEntryPoint()).evaluate(syntheticClasses);

        assertThat(result.hasViolation()).isFalse();
    }

    private static boolean isBlockingThreadingOrSystemClock(CodeUnitCallTarget target) {
        String name = target.getName();
        return switch (target.getOwner().getName()) {
            case "java.lang.Thread" -> FORBIDDEN_THREAD_MEMBERS.contains(name);
            case "java.util.concurrent.TimeUnit" -> name.equals("sleep");
            case "java.util.concurrent.Executors" -> true;
            case "java.time.Instant" ->
                    name.equals("now") && target.getRawParameterTypes().isEmpty();
            default -> name.equals("wait") && target.getRawParameterTypes().size() <= 2;
        };
    }

    private static ArchCondition<JavaMethod> beCalledInProductionOrBeEntryPoint() {
        return new ArchCondition<>("be called in production code or be a framework entry point") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (isSkippableEntryPoint(method)
                        || hasDirectProductionUsage(method)
                        || hasSubtypeProductionUsage(method)
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

    private static Stream<JavaClass> ancestorsOf(JavaClass owner) {
        return Stream.concat(
                owner.getAllRawSuperclasses().stream(), owner.getAllRawInterfaces().stream());
    }

    private static boolean overridesExternalContract(JavaMethod method, JavaClass owner) {
        return ancestorsOf(owner)
                .filter(ancestor -> !isProjectClass(ancestor))
                .anyMatch(ancestor -> findAncestorMethod(ancestor, method).isPresent());
    }

    private static boolean isProjectClass(JavaClass type) {
        String pkg = type.getPackageName();
        return pkg.equals(BASE_PACKAGE) || pkg.startsWith(BASE_PACKAGE_PREFIX);
    }

    private static boolean hasDirectProductionUsage(JavaMethod method) {
        return method.getCallsOfSelf().stream()
                        .anyMatch(call -> isExternalOrNonSelfLambdaCaller(method, call.getOrigin()))
                || method.getReferencesToSelf().stream()
                        .anyMatch(ref -> isExternalOrNonSelfLambdaCaller(method, ref.getOrigin()));
    }

    private static boolean isExternalOrNonSelfLambdaCaller(JavaMethod target, JavaCodeUnit origin) {
        if (origin.equals(target) || origin.getModifiers().contains(JavaModifier.BRIDGE)) {
            return false;
        }
        if (origin.getModifiers().contains(JavaModifier.SYNTHETIC)
                && origin.getOwner().equals(target.getOwner())
                && origin.getName().startsWith("lambda$" + target.getName() + "$")) {
            return false;
        }
        return true;
    }

    private static boolean hasSubtypeProductionUsage(JavaMethod method) {
        return method.getOwner().getAllSubclasses().stream()
                .map(subtype -> findOverridingMethod(subtype, method))
                .flatMap(Optional::stream)
                .anyMatch(ArchitectureTest::hasDirectProductionUsage);
    }

    private static boolean implementsProductionContract(JavaMethod method) {
        return ancestorsOf(method.getOwner())
                .filter(ArchitectureTest::isProjectClass)
                .map(ancestor -> findAncestorMethod(ancestor, method))
                .flatMap(Optional::stream)
                .anyMatch(ArchitectureTest::hasDirectProductionUsage);
    }

    private static Optional<JavaMethod> findAncestorMethod(
            JavaClass ancestor, JavaMethod concreteMethod) {
        List<JavaClass> concreteParams = concreteMethod.getRawParameterTypes();
        String[] exactNames =
                concreteParams.stream().map(JavaClass::getName).toArray(String[]::new);
        Optional<JavaMethod> exact = ancestor.tryGetMethod(concreteMethod.getName(), exactNames);
        if (exact.isPresent()) {
            return exact;
        }
        return ancestor.getMethods().stream()
                .filter(candidate -> candidate.getName().equals(concreteMethod.getName()))
                .filter(candidate ->
                        parametersAssignable(candidate.getRawParameterTypes(), concreteParams))
                .findFirst();
    }

    private static Optional<JavaMethod> findOverridingMethod(
            JavaClass subtype, JavaMethod ancestorMethod) {
        List<JavaClass> ancestorParams = ancestorMethod.getRawParameterTypes();
        String[] exactNames =
                ancestorParams.stream().map(JavaClass::getName).toArray(String[]::new);
        Optional<JavaMethod> exact = subtype.tryGetMethod(ancestorMethod.getName(), exactNames);
        if (exact.isPresent()) {
            return exact;
        }
        return subtype.getMethods().stream()
                .filter(candidate -> candidate.getName().equals(ancestorMethod.getName()))
                .filter(candidate ->
                        parametersAssignable(ancestorParams, candidate.getRawParameterTypes()))
                .findFirst();
    }

    private static boolean parametersAssignable(
            List<JavaClass> ancestorParams, List<JavaClass> concreteParams) {
        if (ancestorParams.size() != concreteParams.size()) {
            return false;
        }
        for (int i = 0; i < ancestorParams.size(); i++) {
            if (!ancestorParams.get(i).isAssignableFrom(concreteParams.get(i).getName())) {
                return false;
            }
        }
        return true;
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

    static class SyntheticBlockingHolder {

        private volatile boolean ready;

        void sleeps() throws InterruptedException {
            Thread.sleep(1L);
            Thread.sleep(Duration.ofMillis(1L));
            TimeUnit.MILLISECONDS.sleep(1L);
        }

        synchronized void waits() throws InterruptedException {
            while (!ready) {
                wait(1L);
            }
        }

        Thread rawThread() {
            return new Thread(() -> {});
        }

        Thread startedVirtualThread() {
            return Thread.startVirtualThread(() -> {});
        }

        Thread.Builder virtualBuilder() {
            return Thread.ofVirtual();
        }

        Thread.Builder platformBuilder() {
            return Thread.ofPlatform();
        }

        ExecutorService executor() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        List<Object> clocklessNow() {
            return List.of(Instant.now());
        }

        List<Object> allowedClockAwareCalls(Clock clock) {
            return List.of(Instant.now(clock), Thread.currentThread());
        }
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

    interface SyntheticGenericBridgeInterface<T> {
        String process(T value);
    }

    static class SyntheticBridgeDeadCodeImpl implements SyntheticGenericBridgeInterface<String> {
        @Override
        public String process(String value) {
            return value;
        }
    }

    interface SyntheticConcreteTargetInterface {
        String greet();
    }

    static class SyntheticConcreteService implements SyntheticConcreteTargetInterface {
        @Override
        public String greet() {
            return "hi";
        }
    }

    interface SyntheticInterfaceTarget {
        String ping();
    }

    static class SyntheticInterfaceService implements SyntheticInterfaceTarget {
        @Override
        public String ping() {
            return "pong";
        }
    }

    static final class SyntheticCleanComparable implements Comparable<SyntheticCleanComparable> {
        private final int rank;

        SyntheticCleanComparable(int rank) {
            this.rank = rank;
        }

        @Override
        public int compareTo(SyntheticCleanComparable other) {
            return Integer.compare(this.rank, other.rank);
        }
    }

    record SyntheticRecord(String label) {}

    static class SyntheticCleanEntryPoint {

        @GetMapping("/synthetic")
        List<String> handleRequest() {
            SyntheticConcreteService concrete = new SyntheticConcreteService();
            SyntheticInterfaceTarget iface = new SyntheticInterfaceService();
            SyntheticRecord rec = new SyntheticRecord(concrete.greet() + iface.ping());
            return Stream.of(rec.label()).map(SyntheticCleanEntryPoint::formatLabel).toList();
        }

        @Bean
        SyntheticCleanComparable comparableBean() {
            return new SyntheticCleanComparable(1);
        }

        private static String formatLabel(String value) {
            return value;
        }
    }
}
