plugins {
    id("io.micronaut.application") version "5.0.0"
    id("com.gradleup.shadow") version "9.4.1"
    id("io.micronaut.aot") version "5.0.0"
}

version = "0.1"
group = "com.erp_maya"



repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    // Base de datos: PostgreSQL + pool Hikari + migraciones Liquibase
    implementation("io.micronaut.liquibase:micronaut-liquibase")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")
    // Persistencia JPA (Micronaut Data + Hibernate)
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    // Jackson databind: lo necesita Hibernate para mapear columnas JSON/JSONB (@JdbcTypeCode JSON)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Validación de DTOs (jakarta.validation)
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut.validation:micronaut-validation")
    // Seguridad: autenticación JWT (Bearer) + hashing de passwords (bcrypt)
    annotationProcessor("io.micronaut.security:micronaut-security-annotations")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("at.favre.lib:bcrypt:0.10.2")
    compileOnly("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut:micronaut-http-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}



application {
    mainClass = "com.erp_maya.Application"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}




graalvmNative.toolchainDetection = false
graalvmNative {
    binaries {
        all {
            buildArgs.add("-H:+SharedArenaSupport")
        }
    }
}




micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.erp_maya.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }

}

// Al correr en local se activa el entorno `dev`, que carga
// application-dev.properties — el archivo que git ignora y donde vive la
// contraseña de la base. Sin esto habría que acordarse de exportar
// MICRONAUT_ENVIRONMENTS en cada terminal y en cada configuración del IDE.
//
// No afecta al servidor: allá corre el jar con `java -jar`, no esta tarea.
tasks.named<JavaExec>("run") {
    environment("MICRONAUT_ENVIRONMENTS", System.getenv("MICRONAUT_ENVIRONMENTS") ?: "dev")
}

tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("dockerfile") {

    baseImage = "eclipse-temurin:25-jre"
}







// https://docs.gradle.org/current/userguide/upgrading_major_version_9.html#test_task_fails_when_no_tests_are_discovered
tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}




