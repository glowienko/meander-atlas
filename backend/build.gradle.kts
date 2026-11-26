import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.testing.jacoco.tasks.JacocoReportBase
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    checkstyle
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

group = "com.meanderatlas"
version = "0.0.1-SNAPSHOT"
description = "MeanderAtlas Backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.modulith.bom.get().toString())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation(libs.springdoc.openapi.webmvc.ui)
    implementation(libs.jspecify)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-docs")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val jacocoClassDirectories =
    files(
        sourceSets.main.get().output.classesDirs.files.map { dir ->
            fileTree(dir) {
                exclude("com/meanderatlas/MeanderAtlasApplication.class")
            }
        }
    )

spotless {
    java {
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror"))
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        check("UnusedMethod", CheckSeverity.ERROR)
        check("UnusedVariable", CheckSeverity.ERROR)
        check("UnusedNestedClass", CheckSeverity.ERROR)
        check("DeadException", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.meanderatlas")
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:HandleTestAssertionLibraries", "true")
        option(
            "NullAway:ExcludedFieldAnnotations",
            "org.springframework.beans.factory.annotation.Autowired,org.springframework.boot.test.web.server.LocalServerPort"
        )
    }
}

tasks.named<ProcessResources>("processTestResources") {
    val props =
        mapOf(
            "postgisImage" to libs.versions.postgis.image.get(),
            "postgisMajorPrefix" to libs.versions.postgis.major.prefix.get(),
        )
    inputs.properties(props)
    filesMatching("test-environment.properties") {
        expand(props)
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("meander-atlas-backend.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    outputs.file(rootProject.file("../contracts/openapi.json"))
    outputs.dir(rootProject.file("../docs/modules"))
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.withType<JacocoReportBase>().configureEach {
    dependsOn(tasks.named<Test>("test"))
    executionData.setFrom(files(layout.buildDirectory.file("jacoco/test.exec")))
    classDirectories.setFrom(jacocoClassDirectories)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

tasks.register<Test>("unitTest") {
    description = "Runs fast unit and architecture tests without Docker."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("meanderatlas.syncModuleDocs", "false")
    useJUnitPlatform {
        excludeTags("e2e")
    }
}
